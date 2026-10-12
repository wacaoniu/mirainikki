import type { FastifyInstance } from 'fastify';
import { db } from '../db.js';
import { nowISO, isValidISO } from '../lib/util.js';
import { syncUpsertEntry, rowToEntry } from './entries.js';
import { syncUpsertHabit, rowToHabit } from './habits.js';
import { attachImagesToEntry } from './images.js';

/**
 * 离线优先同步：
 * - 上行 push：按 client_id upsert，updated_at 新者胜（LWW）
 * - 下行 pull：?since= 增量拉取（含删除墓碑），客户端同样按 LWW 合并
 */
export function registerSync(app: FastifyInstance) {
  app.post('/api/sync/push', async (req, reply) => {
    const b = (req.body || {}) as any;
    let accepted = 0, skipped = 0;
    const errors: string[] = [];

    for (const h of Array.isArray(b.habits) ? b.habits : []) {
      try {
        if (syncUpsertHabit(h)) accepted++; else skipped++;
      } catch (e: any) { errors.push(`habit ${h?.clientId}: ${e.message}`); }
    }

    for (const e of Array.isArray(b.entries) ? b.entries : []) {
      try {
        if (syncUpsertEntry(e)) {
          accepted++;
          if (e.type === 'photo' && Array.isArray(e.content?.imageIds)) {
            attachImagesToEntry(e.clientId, e.content.imageIds);
          }
        } else skipped++;
      } catch (err: any) { errors.push(`entry ${e?.clientId}: ${err.message}`); }
    }

    return { accepted, skipped, errors, serverTime: nowISO() };
  });

  app.get('/api/sync/pull', async (req, reply) => {
    const q: any = req.query;
    const since = q.since;
    if (since && !isValidISO(since)) return reply.code(400).send({ error: 'since 需为 ISO8601' });
    const entries = (db.prepare('SELECT * FROM entries WHERE updated_at > ? ORDER BY updated_at').all(since || '1970-01-01T00:00:00.000Z') as any[]).map(rowToEntry);
    const habits = (db.prepare('SELECT * FROM habits WHERE updated_at > ? ORDER BY updated_at').all(since || '1970-01-01T00:00:00.000Z') as any[]).map(rowToHabit);
    return { serverTime: nowISO(), entries, habits };
  });
}
