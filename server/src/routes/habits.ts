import type { FastifyInstance } from 'fastify';
import { db } from '../db.js';
import { uuid, nowISO, isValidISO } from '../lib/util.js';

export function rowToHabit(r: any) {
  return {
    id: r.id,
    clientId: r.client_id,
    name: r.name,
    emoji: r.emoji,
    color: r.color,
    schedule: JSON.parse(r.schedule_json),
    archived: !!r.archived,
    sortOrder: r.sort_order,
    createdAt: r.created_at,
    updatedAt: r.updated_at,
    deleted: !!r.deleted,
  };
}

function upsertHabit(h: {
  id?: string; clientId: string; name: string; emoji?: string; color?: string;
  schedule?: any; archived?: boolean; sortOrder?: number; createdAt?: string; updatedAt?: string; deleted?: boolean;
}) {
  const now = nowISO();
  db.prepare(`
    INSERT INTO habits (id, client_id, name, emoji, color, schedule_json, archived, sort_order, created_at, updated_at, deleted)
    VALUES (@id, @client_id, @name, @emoji, @color, @schedule_json, @archived, @sort_order, @created_at, @updated_at, @deleted)
    ON CONFLICT(client_id) DO UPDATE SET
      name = excluded.name, emoji = excluded.emoji, color = excluded.color,
      schedule_json = excluded.schedule_json, archived = excluded.archived,
      sort_order = excluded.sort_order, updated_at = excluded.updated_at, deleted = excluded.deleted
  `).run({
    id: h.id || uuid(),
    client_id: h.clientId,
    name: String(h.name).slice(0, 100),
    emoji: h.emoji || '✅',
    color: h.color || '#4CAF50',
    schedule_json: JSON.stringify(h.schedule || { type: 'daily' }),
    archived: h.archived ? 1 : 0,
    sort_order: h.sortOrder || 0,
    created_at: h.createdAt || now,
    updated_at: h.updatedAt || now,
    deleted: h.deleted ? 1 : 0,
  });
}

export function syncUpsertHabit(row: any): boolean {
  const existing = db.prepare('SELECT updated_at FROM habits WHERE client_id = ?').get(row.clientId) as any;
  if (existing && existing.updated_at >= row.updatedAt) return false;
  upsertHabit(row);
  return true;
}

export function registerHabits(app: FastifyInstance) {
  app.get('/api/habits', async (req) => {
    const q: any = req.query;
    const where = ['deleted = 0'];
    if (!q.all) where.push('archived = 0');
    const rows = db.prepare(`SELECT * FROM habits WHERE ${where.join(' AND ')} ORDER BY sort_order, created_at`).all() as any[];
    return { items: rows.map(rowToHabit) };
  });

  app.post('/api/habits', async (req) => {
    const b = req.body as any;
    const clientId = b.clientId || uuid();
    upsertHabit({ clientId, name: b.name, emoji: b.emoji, color: b.color, schedule: b.schedule, sortOrder: b.sortOrder });
    return rowToHabit(db.prepare('SELECT * FROM habits WHERE client_id = ?').get(clientId));
  });

  app.put('/api/habits/:id', async (req, reply) => {
    const { id } = req.params as any;
    const b = req.body as any;
    const existing = db.prepare('SELECT * FROM habits WHERE (id = ? OR client_id = ?)').get(id, id) as any;
    if (!existing) return reply.code(404).send({ error: '不存在' });
    upsertHabit({
      id: existing.id, clientId: existing.client_id, name: b.name ?? existing.name,
      emoji: b.emoji ?? existing.emoji, color: b.color ?? existing.color,
      schedule: b.schedule ?? JSON.parse(existing.schedule_json),
      archived: b.archived ?? !!existing.archived, sortOrder: b.sortOrder ?? existing.sort_order,
      createdAt: existing.created_at, updatedAt: nowISO(),
    });
    return rowToHabit(db.prepare('SELECT * FROM habits WHERE id = ?').get(existing.id));
  });

  app.delete('/api/habits/:id', async (req, reply) => {
    const { id } = req.params as any;
    const r = db.prepare('UPDATE habits SET deleted = 1, updated_at = ? WHERE (id = ? OR client_id = ?)').run(nowISO(), id, id);
    if (!r.changes) return reply.code(404).send({ error: '不存在' });
    return { ok: true };
  });

  app.get('/api/habits/:id/checkins', async (req) => {
    const { id } = req.params as any;
    const q: any = req.query;
    const where = [`type = 'habit_checkin'`, 'deleted = 0', `json_extract(content, '$.habitId') = ?`];
    const params: any[] = [id];
    if (q.from) { where.push('entry_date >= ?'); params.push(q.from); }
    if (q.to) { where.push('entry_date <= ?'); params.push(q.to); }
    const rows = db.prepare(`SELECT DISTINCT entry_date FROM entries WHERE ${where.join(' AND ')} ORDER BY entry_date`).all(...params) as any[];
    return { dates: rows.map((r) => r.entry_date) };
  });
}
