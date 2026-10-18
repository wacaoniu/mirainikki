/**
 * 把服务端 + 网页端打包成单文件 exe（Windows，Node SEA）
 * 用法：node build-exe.mjs
 * 产物：dist-exe/未来日记.exe —— 网页页面已嵌入 exe 内，单文件即可运行，
 *       无需安装 Node，也不依赖旁边的 web 目录。
 */
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SERVER = __dirname;
const PROJECT = path.resolve(SERVER, '..');
const OUT_DIR = path.join(PROJECT, 'dist-exe');
const OUT_EXE = path.join(OUT_DIR, '未来日记.exe');
const BUNDLE = path.join(SERVER, '.sea', 'server.cjs');
const BLOB = path.join(SERVER, '.sea', 'sea-prep.blob');
const WEB_DIST = path.join(PROJECT, 'web', 'dist');

fs.rmSync(path.join(SERVER, '.sea'), { recursive: true, force: true });
fs.mkdirSync(path.join(SERVER, '.sea'), { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

if (!fs.existsSync(path.join(WEB_DIST, 'index.html'))) {
  console.error('缺少 web/dist 构建产物：请先在 web 目录执行 npm run build');
  process.exit(1);
}

console.log('[1/5] esbuild 打包服务端 …');
execFileSync(
  process.execPath,
  [
    path.join(SERVER, 'node_modules', 'esbuild', 'bin', 'esbuild'),
    path.join(SERVER, 'src', 'index.ts'),
    '--bundle',
    '--platform=node',
    '--target=node24',
    '--format=cjs',
    '--external:node:*',
    '--outfile=' + BUNDLE,
  ],
  { stdio: 'inherit' },
);

console.log('[2/5] 生成 SEA 配置（含嵌入的网页资源）…');
// 网页所有文件作为 SEA assets 嵌入，key 即 URL 相对路径（web/ 前缀）
const assets = {};
function walk(dir, prefix) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) walk(full, prefix + name + '/');
    else assets[prefix + name] = full;
  }
}
walk(WEB_DIST, 'web/');
fs.writeFileSync(
  path.join(SERVER, '.sea', 'sea-config.json'),
  JSON.stringify(
    {
      main: BUNDLE,
      output: BLOB,
      disableExperimentalSEAWarning: true,
      useSnapshot: false,
      useCodeCache: false,
      assets,
    },
    null,
    2,
  ),
);

console.log(`[3/5] 生成 SEA blob（嵌入 ${Object.keys(assets).length} 个网页文件）…`);
execFileSync(process.execPath, ['--experimental-sea-config', path.join(SERVER, '.sea', 'sea-config.json')], {
  stdio: 'inherit',
});

console.log('[4/5] 复制 node 并注入 blob …');
fs.copyFileSync(process.execPath, OUT_EXE);
execFileSync(
  process.execPath,
  [
    path.join(SERVER, 'node_modules', 'postject', 'dist', 'cli.js'),
    OUT_EXE,
    'NODE_SEA_BLOB',
    BLOB,
    '--sentinel-fuse',
    'NODE_SEA_FUSE_fce680ab2cc467b6e072b8b5df1996b2',
  ],
  { stdio: 'inherit' },
);

console.log('[5/5] 完成：' + OUT_EXE);
console.log('网页已嵌入 exe —— 单文件放任何位置都能运行，数据生成在 exe 旁的 data/ 与 uploads/。');
