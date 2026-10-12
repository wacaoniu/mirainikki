import type { FastifyInstance } from 'fastify';
import { db, ftsAvailable } from '../db.js';
import { uuid, nowISO, localToday, buildTextIndex, isValidDate, escapeLike } from '../lib/util.js';
import { validateContent, tagsSchema } from '../lib/validate.js';
import { attachImagesToEntry } from './images.js';
import { z } from 'zod';

export type EntryRow = {
  id: string; client_id: string; type: string; content: string; text_index: string;
  tags: string; entry_date: string; created_at: string; updated_at: string; deleted: number;
};

export function rowToEntry(r: any) {
  return {
    id: r.id,
    clientId: r.client_id,
    type: r.type,
    content: JSON.parse(r.content),
    tags: JSON.parse(r.tags),
    entryDate: r.entry_date,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
    deleted: !!r.deleted,
  };
}

const upsertEntryStmt = () => db.prepare(`
  INSERT INTO entries (id, client_id, type, content, text_index, tags, entry_date, created_at, updated_at, deleted)
  VALUES (@id, @client_id, @type, @content, @text_index, @tags, @entry_date, @created_at, @updated_at, @deleted)
  ON CONFLICT(client_id) DO UPDATE SET
    type = excluded.type, content = excluded.content, text_index = excluded.text_index,
    tags = excluded.tags, entry_date = excluded.entry_date, updated_at = excluded.updated_at,
    deleted = excluded.deleted
`);

export function upsertEntry(e: {
  id?: string; clientId: string; type: string; content: any; tags?: string[];
  entryDate: string; createdAt?: string; updatedAt?: string; deleted?: boolean;
}) {
  const v = validateContent(e.type, e.content);
  if (!v.ok) throw new Error(v.error);
  // 正文里的 #标签 自动并入 tags（去重），免得用户还要单独维护标签
  const hashTags = extractHashTags(v.data?.body) || [];
  const merged = [...new Set([...tagsSchema.parse(e.tags || []), ...hashTags])];
  const now = nowISO();
  upsertEntryStmt().run({
    id: e.id || uuid(),
    client_id: e.clientId,
    type: e.type,
    content: JSON.stringify(v.data),
    text_index: buildTextIndex(e.type, v.data, merged),
    tags: JSON.stringify(merged),
    entry_date: e.entryDate,
    created_at: e.createdAt || now,
    updated_at: e.updatedAt || now,
    deleted: e.deleted ? 1 : 0,
  });
}

/** 从正文提取 #标签（中英文/数字/连字符），如 "今天跑了五公里 #运动 #晨跑" */
function extractHashTags(body?: string): string[] {
  if (!body) return [];
  return [...body.matchAll(/#([\w\u4e00-\u9fa5-]+)/g)].map((m) => m[1]).slice(0, 20);
}

/** 同步用：按 client_id + updated_at 新者胜 upsert，返回是否落库 */
export function syncUpsertEntry(row: any): boolean {
  const existing = db.prepare('SELECT updated_at FROM entries WHERE client_id = ?').get(row.clientId) as any;
  if (existing && existing.updated_at >= row.updatedAt) return false;
  upsertEntry({
    id: row.id, clientId: row.clientId, type: row.type, content: row.content, tags: row.tags,
    entryDate: row.entryDate, createdAt: row.createdAt, updatedAt: row.updatedAt, deleted: row.deleted,
  });
  return true;
}

function buildSearch(q: string): { cond: string; params: string[] } {
  const t = q.trim();
  if (ftsAvailable && t.length >= 3) {
    return {
      cond: 'e.rowid IN (SELECT rowid FROM entries_fts WHERE entries_fts MATCH ?)',
      params: [`"${t.replace(/"/g, '""')}"`],
    };
  }
  return { cond: "e.text_index LIKE ? ESCAPE '\\'", params: [`%${escapeLike(t)}%`] };
}

export function registerEntries(app: FastifyInstance) {
  app.get('/api/entries', async (req) => {
    const q: any = req.query;
    const where: string[] = ['e.deleted = 0'];
    const params: any[] = [];
    if (q.from && isValidDate(q.from)) { where.push('e.entry_date >= ?'); params.push(q.from); }
    if (q.to && isValidDate(q.to)) { where.push('e.entry_date <= ?'); params.push(q.to); }
    if (q.type) {
      const types = String(q.type).split(',').filter(Boolean);
      where.push(`e.type IN (${types.map(() => '?').join(',')})`);
      params.push(...types);
    }
    if (q.tag) { where.push('e.tags LIKE ?'); params.push(`%"${String(q.tag).replace(/"/g, '""')}"%`); }
    if (q.q) {
      const s = buildSearch(String(q.q));
      where.push(s.cond);
      params.push(...s.params);
    }
    const limit = Math.min(Math.max(parseInt(q.limit) || 100, 1), 500);
    const offset = Math.max(parseInt(q.offset) || 0, 0);
    const whereSql = where.join(' AND ');
    const total = (db.prepare(`SELECT COUNT(*) c FROM entries e WHERE ${whereSql}`).get(...params) as any).c;
    const rows = db
      .prepare(`SELECT e.* FROM entries e WHERE ${whereSql} ORDER BY e.entry_date DESC, e.created_at DESC LIMIT ? OFFSET ?`)
      .all(...params, limit, offset);
    return { total, items: rows.map(rowToEntry) };
  });

  app.post('/api/entries', async (req, reply) => {
    const b = req.body as any;
    const parsed = parseEntryBody(b);
    if (!parsed.ok) return reply.code(400).send({ error: parsed.error });
    const now = nowISO();
    const clientId = b.clientId || uuid();
    upsertEntry({ clientId, type: b.type, content: b.content, tags: b.tags, entryDate: parsed.entryDate, createdAt: now, updatedAt: now });
    if (b.type === 'photo' && Array.isArray(b.content?.imageIds)) attachImagesToEntry(clientId, b.content.imageIds);
    const row = db.prepare('SELECT * FROM entries WHERE client_id = ?').get(clientId) as any;
    return rowToEntry(row);
  });

  app.get('/api/entries/:id', async (req, reply) => {
    const { id } = req.params as any;
    const row = db.prepare('SELECT * FROM entries WHERE (id = ? OR client_id = ?) AND deleted = 0').get(id, id) as any;
    if (!row) return reply.code(404).send({ error: '不存在' });
    return rowToEntry(row);
  });

  app.put('/api/entries/:id', async (req, reply) => {
    const { id } = req.params as any;
    const b = req.body as any;
    const existing = db.prepare('SELECT * FROM entries WHERE (id = ? OR client_id = ?)').get(id, id) as any;
    if (!existing) return reply.code(404).send({ error: '不存在' });
    const parsed = parseEntryBody(b);
    if (!parsed.ok) return reply.code(400).send({ error: parsed.error });
    const now = nowISO();
    upsertEntry({
      id: existing.id, clientId: existing.client_id,
      type: b.type, content: b.content, tags: b.tags,
      entryDate: parsed.entryDate, createdAt: existing.created_at, updatedAt: now,
    });
    const row = db.prepare('SELECT * FROM entries WHERE id = ?').get(existing.id) as any;
    return rowToEntry(row);
  });

  app.delete('/api/entries/:id', async (req, reply) => {
    const { id } = req.params as any;
    const r = db.prepare('UPDATE entries SET deleted = 1, updated_at = ? WHERE (id = ? OR client_id = ?)').run(nowISO(), id, id);
    if (!r.changes) return reply.code(404).send({ error: '不存在' });
    return { ok: true };
  });

  app.get('/api/entries/dates', async (req) => {
    const q: any = req.query;
    const where = ['deleted = 0'];
    const params: any[] = [];
    if (q.from) { where.push('entry_date >= ?'); params.push(q.from); }
    if (q.to) { where.push('entry_date <= ?'); params.push(q.to); }
    const rows = db.prepare(`SELECT DISTINCT entry_date FROM entries WHERE ${where.join(' AND ')} ORDER BY entry_date DESC`).all(...params) as any[];
    return rows.map((r) => r.entry_date);
  });
}

function parseEntryBody(b: any): { ok: true; entryDate: string } | { ok: false; error: string } {
  const v = validateContent(b?.type, b?.content);
  if (!v.ok) return { ok: false, error: v.error };
  const tags = tagsSchema.safeParse(b?.tags);
  if (!tags.success) return { ok: false, error: 'tags 格式错误' };
  if (b?.entryDate != null && !isValidDate(b.entryDate)) return { ok: false, error: 'entryDate 需为 YYYY-MM-DD' };
  return { ok: true, entryDate: b?.entryDate || localToday() };
}
