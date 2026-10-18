/**
 * 运行环境路径解析：兼容三种运行方式
 * 1) 打包后的 exe（Node SEA）—— 网页资源嵌入 exe 内；数据相对 exe 所在目录
 * 2) 开发模式（npm start / tsx）—— 网页读 web/dist，数据相对 server/ 目录
 * 3) 环境变量覆盖：DATA_DIR / UPLOAD_DIR / WEB_DIST
 */
import path from 'node:path';
import fs from 'node:fs';

/** 是否运行在打包 exe 中（Node SEA）。开发模式（ESM）下 require 不存在 */
export const IS_EXE = (() => {
  if (typeof require === 'function') {
    try {
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

/** 网页端构建产物目录：仅开发模式用；exe 模式从嵌入资源读取 */
export const WEB_DIST = (() => {
  if (process.env.WEB_DIST) return process.env.WEB_DIST;
  const candidates = [
    path.join(APP_ROOT, 'web', 'dist'),
    path.resolve(APP_ROOT, '..', 'web', 'dist'),
  ];
  return candidates.find((p) => fs.existsSync(path.join(p, 'index.html'))) || candidates[0];
})();

/** 读取嵌入的网页资源；非 exe 环境返回 null */
export function readEmbeddedAsset(key: string): Buffer | null {
  if (!IS_EXE) return null;
  try {
    const sea = require('node:sea');
    const buf = sea.getAsset('web/' + key) as ArrayBuffer;
    return Buffer.from(buf);
  } catch {
    return null;
  }
}

export function hasEmbeddedWeb(): boolean {
  if (!IS_EXE) return false;
  return readEmbeddedAsset('index.html') != null;
}

fs.mkdirSync(DATA_DIR, { recursive: true });
fs.mkdirSync(UPLOAD_DIR, { recursive: true });
