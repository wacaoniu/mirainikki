import path from 'node:path';
import fs from 'node:fs';
import type { FastifyInstance } from 'fastify';
import { UPLOAD_DIR, db } from '../db.js';
import { uuid, nowISO } from '../lib/util.js';

const ALLOWED: Record<string, string> = {
  'image/jpeg': 'jpg',
  'image/png': 'png',
  'image/webp': 'webp',
  'image/gif': 'gif',
};

export function registerImages(app: FastifyInstance) {
  app.post('/api/images', async (req, reply) => {
    const file = await (req as any).file();
    if (!file) return reply.code(400).send({ error: '缺少文件' });
    const ext = ALLOWED[file.mimetype];
    if (!ext) return reply.code(400).send({ error: `不支持的图片类型 ${file.mimetype}` });

    const id = uuid();
    const filename = `${id}.${ext}`;
    const buf = await file.toBuffer();
    if (buf.length > 20 * 1024 * 1024) return reply.code(400).send({ error: '图片不能超过 20MB' });
    fs.writeFileSync(path.join(UPLOAD_DIR, filename), buf);
    db.prepare('INSERT INTO images (id, entry_id, filename, size, created_at) VALUES (?, ?, ?, ?, ?)')
      .run(id, '', filename, buf.length, nowISO());
    return { id, url: `/uploads/${filename}`, size: buf.length };
  });

  app.get('/api/images/:id/file', async (req, reply) => {
    const { id } = req.params as any;
    const row = db.prepare('SELECT filename FROM images WHERE id = ?').get(id) as any;
    if (!row || !fs.existsSync(path.join(UPLOAD_DIR, row.filename))) {
      return reply.code(404).send({ error: '图片不存在' });
    }
    return (reply as any).sendFile(row.filename, UPLOAD_DIR);
  });

  app.delete('/api/images/:id', async (req, reply) => {
    const { id } = req.params as any;
    const row = db.prepare('SELECT * FROM images WHERE id = ?').get(id) as any;
    if (!row) return reply.code(404).send({ error: '不存在' });
    db.prepare('DELETE FROM images WHERE id = ?').run(id);
    try { fs.unlinkSync(path.join(UPLOAD_DIR, row.filename)); } catch { /* 文件可能已不存在 */ }
    return { ok: true };
  });
}

export function attachImagesToEntry(entryClientId: string, imageIds: string[]) {
  const row = db.prepare('SELECT id FROM entries WHERE client_id = ?').get(entryClientId) as any;
  if (!row) return;
  const stmt = db.prepare('UPDATE images SET entry_id = ? WHERE id = ?');
  for (const imgId of imageIds) stmt.run(row.id, imgId);
}
