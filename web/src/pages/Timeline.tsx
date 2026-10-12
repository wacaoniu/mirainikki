import { useEffect, useMemo, useState } from 'react';
import type { Entry } from '../types';
import { fetchEntries } from '../api';
import { fmtDay, TYPE_META, moodOf } from '../constants';
import EntryCard from '../components/EntryCard';

function monthRange(ym: string): { from: string; to: string } {
  const [y, m] = ym.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  const p = (n: number) => String(n).padStart(2, '0');
  return { from: `${y}-${p(m)}-01`, to: `${y}-${p(m)}-${p(last)}` };
}

function shiftMonth(ym: string, delta: number): string {
  const [y, m] = ym.split('-').map(Number);
  const d = new Date(y, m - 1 + delta, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function nowYM(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export default function Timeline({ refreshKey, onEdit, onChanged }: {
  refreshKey: number; onEdit: (e: Entry) => void; onChanged: () => void;
}) {
  const [month, setMonth] = useState(nowYM());
  const [q, setQ] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [entries, setEntries] = useState<Entry[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const searching = q.trim().length > 0 || typeFilter !== '';

  useEffect(() => {
    setLoading(true);
    const cond = searching
      ? { q: q.trim() || undefined, type: typeFilter || undefined, limit: 300 }
      : { ...monthRange(month), limit: 500 };
    fetchEntries(cond)
      .then((r) => { setEntries(r.items); setTotal(r.total); })
      .catch(() => setEntries([]))
      .finally(() => setLoading(false));
  }, [month, q, typeFilter, refreshKey]);

  const groups = useMemo(() => {
    const map = new Map<string, Entry[]>();
    for (const e of entries) {
      if (!map.has(e.entryDate)) map.set(e.entryDate, []);
      map.get(e.entryDate)!.push(e);
    }
    return [...map.entries()].sort((a, b) => b[0].localeCompare(a[0]));
  }, [entries]);

  const [y, m] = month.split('-').map(Number);
  const now = new Date();
  const isCurrentMonth = y === now.getFullYear() && m === now.getMonth() + 1;

  return (
    <div className="container">
      <div className="timeline-toolbar">
        <div className="month-nav">
          <button className="btn ghost" onClick={() => setMonth(shiftMonth(month, -1))}>‹ 上月</button>
          <div className="month-label">{y} 年 {m} 月</div>
          <button className="btn ghost" onClick={() => setMonth(shiftMonth(month, 1))} disabled={isCurrentMonth}>下月 ›</button>
        </div>
        <div className="search-box">
          <input value={q} onChange={(e) => setQ(e.target.value)}
            placeholder="搜索日记、心情、标签…（回车全文搜索）"
            onKeyDown={(e) => e.key === 'Enter' && setQ(e.currentTarget.value)} />
        </div>
        <div className="type-filter">
          <button className={typeFilter === '' ? 'on' : ''} onClick={() => setTypeFilter('')}>全部</button>
          {Object.entries(TYPE_META).map(([t, meta]) => (
            <button key={t} className={typeFilter === t ? 'on' : ''} onClick={() => setTypeFilter(t === typeFilter ? '' : t)}>
              {meta.icon}
            </button>
          ))}
        </div>
      </div>

      {loading && <div className="empty-tip">加载中…</div>}

      {!loading && entries.length === 0 && (
        <div className="empty-tip">
          <div className="e">{searching ? '🔍' : '🌱'}</div>
          {searching ? `没有找到相关记录（共 0 条）` : '这个月还没有记录，从写下第一条开始'}
        </div>
      )}

      {!loading && searching && entries.length > 0 && (
        <div style={{ color: 'var(--ink-3)', fontSize: 13, marginBottom: 12 }}>共 {total} 条结果</div>
      )}

      {groups.map(([date, items]) => {
        const moods = items.filter((e) => e.type === 'mood');
        const dayMood = moods.length
          ? `当日心情 ${moodOf(Math.round(moods.reduce((s, e) => s + e.content.score, 0) / moods.length)).emoji}`
          : '';
        return (
          <div className="day-group" key={date}>
            <div className="day-head">
              <span className="day-title">{fmtDay(date)}</span>
              <span className="day-mood">{dayMood} · {items.length} 条</span>
            </div>
            {items.map((e) => <EntryCard key={e.id} entry={e} onEdit={onEdit} onChanged={onChanged} />)}
          </div>
        );
      })}
    </div>
  );
}
