/**
 * 把服务端 + 网页端打包成单文件 exe（Windows，Node SEA）
 * 用法：node build-exe.mjs
 * 产物：dist-exe/未来日记.exe（双击即启动，无需安装 Node）
 */
import { execSync, execFileSync } from 'node:child_process';
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

fs.rmSync(path.join(SERVER, '.sea'), { recursive: true, force: true });
fs.mkdirSync(path.join(SERVER, '.sea'), { recursive: true });
fs.mkdirSync(OUT_DIR, { recursive: true });

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
    // 内置模块与原生依赖不打包
    '--external:node:*',
    '--outfile=' + BUNDLE,
  ],
  { stdio: 'inherit' },
);

console.log('[2/5] 生成 SEA 配置 …');
const seaConfig = {
  main: BUNDLE,
  output: BLOB,
  disableExperimentalSEAWarning: true,
  useSnapshot: false,
  useCodeCache: false,
};
fs.writeFileSync(path.join(SERVER, '.sea', 'sea-config.json'), JSON.stringify(seaConfig, null, 2));

console.log('[3/5] 生成 SEA blob …');
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
console.log('提示：运行 exe 时请把它放在项目根目录（web/、data/ 同级），数据与网页会自动就位。');
