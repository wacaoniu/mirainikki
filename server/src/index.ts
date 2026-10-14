import Fastify from 'fastify';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import fastifyStatic from '@fastify/static';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { exec } from 'node:child_process';
import { UPLOAD_DIR, DATA_DIR, WEB_DIST, IS_EXE } from './paths.js';
import { ftsAvailable } from './db.js';
import { registerAuth, authHook } from './routes/auth.js';
import { registerEntries } from './routes/entries.js';
import { registerHabits } from './routes/habits.js';
import { registerImages } from './routes/images.js';
import { registerSync } from './routes/sync.js';
import { registerStats } from './routes/stats.js';
import { registerExport } from './routes/export.js';
import { registerAI } from './routes/ai.js';

async function main() {
  const app = Fastify({ logger: true, bodyLimit: 20 * 1024 * 1024 });

  await app.register(cors, { origin: true });
  await app.register(multipart, { limits: { fileSize: 20 * 1024 * 1024 } });
  await app.register(fastifyStatic, { root: UPLOAD_DIR, prefix: '/uploads/' });

  authHook(app); // 必须在路由注册前挂载

  registerAuth(app);
  registerEntries(app);
  registerHabits(app);
  registerImages(app);
  registerSync(app);
  registerStats(app);
  registerExport(app);
  registerAI(app);

  const port = parseInt(process.env.PORT || '8787');
  const host = process.env.HOST || '0.0.0.0';

  // 托管网页端构建产物（web/dist），单进程同时提供 API + 网页；SPA 兜底回 index.html
  const webDist = WEB_DIST;
  if (fs.existsSync(path.join(webDist, 'index.html'))) {
    const MIME: Record<string, string> = {
      '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css',
      '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.ico': 'image/x-icon', '.woff2': 'font/woff2',
    };
    app.setNotFoundHandler(async (req, reply) => {
      const url = decodeURIComponent(req.url.split('?')[0]);
      if (url.startsWith('/api/') || url.startsWith('/uploads/')) return reply.code(404).send({ error: 'Not Found' });
      const rel = path.normalize(url).replace(/^([/\\]+)*/, '') || 'index.html';
      const file = path.join(webDist, rel);
      if (file.startsWith(webDist) && fs.existsSync(file) && fs.statSync(file).isFile()) {
        return reply.header('content-type', MIME[path.extname(file)] || 'application/octet-stream').send(fs.readFileSync(file));
      }
      return reply.header('content-type', 'text/html; charset=utf-8').send(fs.readFileSync(path.join(webDist, 'index.html')));
    });
    app.log.info(`网页端：${webDist}`);
  }

  await app.listen({ port, host });
  app.log.info(`「未来日记」后端已启动 http://${host}:${port}（FTS5 ${ftsAvailable ? '已启用' : '不可用，使用 LIKE 回退'}）`);
  app.log.info(`上传目录：${path.resolve(UPLOAD_DIR)}`);

  /** 双击 exe 运行时：友好的中文提示 + 自动打开浏览器 */
  if (IS_EXE) {
    const lanIps: string[] = [];
    for (const infos of Object.values(os.networkInterfaces())) {
      for (const info of infos || []) {
        if (info.family === 'IPv4' && !info.internal) lanIps.push(info.address);
      }
    }
    const line = '='.repeat(60);
    console.log(`\n${line}`);
    console.log('  未来日记 MiraiNikki 已启动');
    console.log(line);
    console.log(`\n  【电脑上看日记】浏览器会自动打开；没弹出就手动访问：`);
    console.log(`      http://localhost:${port}`);
    console.log(`\n  【手机连电脑】App「设置」页 -> 服务器地址，填下面之一：`);
    const shown = lanIps.length ? lanIps : ['<未检测到局域网IP，请确认已连WiFi>'];
    for (const ip of shown) console.log(`      http://${ip}:${port}`);
    console.log(`\n  手机要和电脑连同一个 WiFi；Windows 防火墙弹窗请点"允许"。`);
    console.log(`  数据都在本文件夹的 data\\ 和 uploads\\ 里，拷走即备份。`);
    console.log(`  关闭本窗口 = 停止服务（手机里的记录不受影响）。`);
    console.log(`\n${line}\n`);

    const url = `http://localhost:${port}`;
    exec(process.platform === 'win32' ? `start "" "${url}"` : `open "${url}"`, () => {});
  }
}

main().catch(async (err) => {
  const code = (err as any)?.code;
  if (code === 'EADDRINUSE') {
    console.error('\n启动失败：端口 8787 已被占用。');
    console.error('可能已经有一个「未来日记」在运行 —— 直接打开 http://localhost:8787 看看。');
    console.error('如果那是别的程序占用的，请先关闭它，再重新双击本程序。\n');
  } else {
    console.error('\n启动失败：', err);
  }
  // 双击 exe 时窗口不能一闪而过，等用户看一眼
  if (IS_EXE) {
    const rl = await import('node:readline');
    const iface = rl.createInterface({ input: process.stdin, output: process.stdout });
    await new Promise<void>((resolve) => iface.question('按回车键关闭…', () => { iface.close(); resolve(); }));
  }
  process.exit(1);
});
