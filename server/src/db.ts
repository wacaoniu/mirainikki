import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const ROOT = path.resolve(__dirname, '..');
export const DATA_DIR = process.env.DATA_DIR || path.join(ROOT, 'data');
export const UPLOAD_DIR = process.env.UPLOAD_DIR || path.join(ROOT, 'uploads');
fs.mkdirSync(DATA_DIR, { recursive: true });
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

export const db = new DatabaseSync(path.join(DATA_DIR, 'zhiji.db'));
db.exec('PRAGMA journal_mode = WAL;');

db.exec(`
CREATE TABLE IF NOT EXISTS entries (
  id          TEXT PRIMARY KEY,
  client_id   TEXT NOT NULL UNIQUE,
  type        TEXT NOT NULL,
  content     TEXT NOT NULL,
  text_index  TEXT NOT NULL DEFAULT '',
  tags        TEXT NOT NULL DEFAULT '[]',
  entry_date  TEXT NOT NULL,
  created_at  TEXT NOT NULL,
  updated_at  TEXT NOT NULL,
  deleted     INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_entries_date ON entries(entry_date);
CREATE INDEX IF NOT EXISTS idx_entries_updated ON entries(updated_at);
CREATE INDEX IF NOT EXISTS idx_entries_type_date ON entries(type, entry_date);

CREATE TABLE IF NOT EXISTS habits (
  id             TEXT PRIMARY KEY,
  client_id      TEXT NOT NULL UNIQUE,
  name           TEXT NOT NULL,
  emoji          TEXT NOT NULL DEFAULT '✅',
  color          TEXT NOT NULL DEFAULT '#4CAF50',
  schedule_json  TEXT NOT NULL DEFAULT '{"type":"daily"}',
  archived       INTEGER NOT NULL DEFAULT 0,
  sort_order     INTEGER NOT NULL DEFAULT 0,
  created_at     TEXT NOT NULL,
  updated_at     TEXT NOT NULL,
  deleted        INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS images (
  id         TEXT PRIMARY KEY,
  entry_id   TEXT NOT NULL DEFAULT '',
  filename   TEXT NOT NULL,
  width      INTEGER,
  height     INTEGER,
  size       INTEGER,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS meta (
  key   TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
`);

// FTS5 + trigram 中文可搜；环境不支持时回退 LIKE（个人数据量下足够快）
export const ftsAvailable = (() => {
  try {
    db.exec(`CREATE VIRTUAL TABLE IF NOT EXISTS entries_fts USING fts5(text_index, content='', tokenize='trigram');`);
    db.exec(`
      CREATE TRIGGER IF NOT EXISTS entries_fts_ai AFTER INSERT ON entries BEGIN
        INSERT INTO entries_fts(rowid, text_index) VALUES (new.rowid, new.text_index);
      END;
      CREATE TRIGGER IF NOT EXISTS entries_fts_ad AFTER DELETE ON entries BEGIN
        INSERT INTO entries_fts(entries_fts, rowid, text_index) VALUES('delete', old.rowid, old.text_index);
      END;
      CREATE TRIGGER IF NOT EXISTS entries_fts_au AFTER UPDATE ON entries BEGIN
        INSERT INTO entries_fts(entries_fts, rowid, text_index) VALUES('delete', old.rowid, old.text_index);
        INSERT INTO entries_fts(rowid, text_index) VALUES (new.rowid, new.text_index);
      END;
    `);
    return true;
  } catch (e: any) {
    console.warn('[db] FTS5 不可用，搜索回退 LIKE：', e?.message);
    return false;
  }
})();

export function metaGet(key: string): string | undefined {
  const row = db.prepare('SELECT value FROM meta WHERE key = ?').get(key) as any;
  return row?.value;
}

export function metaSet(key: string, value: string) {
  db.prepare('INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value').run(key, value);
}
