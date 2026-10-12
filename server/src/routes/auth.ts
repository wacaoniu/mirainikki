import crypto from 'node:crypto';
import type { FastifyInstance } from 'fastify';
import { metaGet, metaSet } from '../db.js';
import { sha256, nowISO } from '../lib/util.js';

/** 多设备 Token：每次登录签发新 Token 并追加到列表（最近 20 个均有效） */
function issueToken(): string {
  const token = crypto.randomBytes(32).toString('hex');
  let list: any[] = [];
  try { list = JSON.parse(metaGet('tokens') || '[]'); } catch { list = []; }
  list.push({ token, issued_at: nowISO() });
  metaSet('tokens', JSON.stringify(list.slice(-20)));
  return token;
}

function validTokens(): Set<string> {
  try {
    const list = JSON.parse(metaGet('tokens') || '[]');
    // 兼容旧版单 token
    const legacy = metaGet('token');
    const s = new Set<string>(list.map((x: any) => x.token));
    if (legacy) s.add(legacy);
    return s;
  } catch {
    return new Set();
  }
}

export function registerAuth(app: FastifyInstance) {
  app.get('/api/auth/status', async () => ({ initialized: !!metaGet('password_hash') }));

  app.post('/api/auth/setup', async (req, reply) => {
    const { password } = (req.body || {}) as { password?: string };
    if (metaGet('password_hash')) return reply.code(409).send({ error: '已初始化，请直接登录' });
    if (!password || password.length < 4) return reply.code(400).send({ error: '密码至少 4 位' });
    const salt = crypto.randomBytes(16).toString('hex');
    metaSet('salt', salt);
    metaSet('password_hash', sha256(salt + password));
    metaSet('initialized_at', nowISO());
    return { token: issueToken() };
  });

  app.post('/api/auth/login', async (req, reply) => {
    const { password } = (req.body || {}) as { password?: string };
    const salt = metaGet('salt');
    const hash = metaGet('password_hash');
    if (!hash || !salt) return reply.code(400).send({ error: '尚未初始化，请先 setup' });
    if (sha256(salt + (password || '')) !== hash) return reply.code(401).send({ error: '密码错误' });
    return { token: issueToken() };
  });
}

/** 全局 Token 校验：/api/* 除 auth 外；/uploads 与图片直读路由放开（UUID 即凭证） */
export function authHook(app: FastifyInstance) {
  app.addHook('onRequest', async (req, reply) => {
    const url = req.url.split('?')[0];
    if (!url.startsWith('/api/') || url.startsWith('/api/auth/')) return;
    if (/^\/api\/images\/[^/]+\/file$/.test(url)) return;
    const token = (req.headers.authorization || '').replace(/^Bearer\s+/i, '');
    if (!token || !validTokens().has(token)) {
      return reply.code(401).send({ error: '未授权' });
    }
  });
}
