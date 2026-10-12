import Fastify from 'fastify';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import fastifyStatic from '@fastify/static';
import fs from 'node:fs';
import path from 'node:path';
import { UPLOAD_DIR, ROOT, ftsAvailable } from './db.js';
import { registerAuth, authHook } from './routes/auth.js';
import { registerEntries } from './routes/entries.js';
import { registerHabits } from './routes/habits.js';
import { registerImages } from './routes/images.js';
import { registerSync } from './routes/sync.js';
import { registerStats } from './routes/stats.js';
import { registerExport } from './routes/export.js';
import { registerAI } from './routes/ai.js';

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
const webDist = path.resolve(ROOT, '..', 'web', 'dist');
if (fs.existsSync(webDist)) {
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
app.log.info(`「知己」后端已启动 http://${host}:${port}（FTS5 ${ftsAvailable ? '已启用' : '不可用，使用 LIKE 回退'}）`);
app.log.info(`上传目录：${path.resolve(UPLOAD_DIR)}`);
