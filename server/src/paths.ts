/**
 * 运行环境路径解析：兼容三种运行方式
 * 1) 打包后的 exe（Node SEA）—— 数据、网页都相对 exe 所在目录
 * 2) 开发模式（npm start / tsx）—— 相对 server/ 目录
 * 3) 环境变量覆盖：DATA_DIR / UPLOAD_DIR / WEB_DIST
 */
import path from 'node:path';
import fs from 'node:fs';

/** 是否运行在打包 exe 中（Node SEA）。开发模式（ESM）下 require 不存在 */
export const IS_EXE = (() => {
  if (typeof require === 'function') {
    try {
      // SEA 内置模块：普通 node 运行返回 false，exe 内返回 true
      return !!(require('node:sea') as any).isSea?.();
    } catch {
      return false;
    }
  }
  return false;
})();

/** 应用根目录：exe 所在目录 / 开发时的 server 目录 */
export const APP_ROOT = IS_EXE ? path.dirname(process.execPath) : process.cwd();

export const DATA_DIR = process.env.DATA_DIR || path.join(APP_ROOT, 'data');
export const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(APP_ROOT, 'uploads');

/** 网页端构建产物目录：优先 exe/项目根旁的 web/dist，兼容开发目录结构 */
export const WEB_DIST = (() => {
  if (process.env.WEB_DIST) return process.env.WEB_DIST;
  const candidates = [
    path.join(APP_ROOT, 'web', 'dist'),
    path.resolve(APP_ROOT, '..', 'web', 'dist'),
  ];
  return candidates.find((p) => fs.existsSync(path.join(p, 'index.html'))) || candidates[0];
})();

fs.mkdirSync(DATA_DIR, { recursive: true });
fs.mkdirSync(UPLOAD_DIR, { recursive: true });
