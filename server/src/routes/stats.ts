import type { FastifyInstance } from 'fastify';
import { db } from '../db.js';
import { localToday } from '../lib/util.js';

export function shiftDate(d: string, days: number): string {
  const dt = new Date(d + 'T00:00:00Z');
  dt.setUTCDate(dt.getUTCDate() + days);
  return dt.toISOString().slice(0, 10);
}

export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(to + 'T00:00:00Z') - Date.parse(from + 'T00:00:00Z')) / 86400000) + 1;
}

function streak(dates: Set<string>): number {
  const today = localToday();
  let anchor: string | null = null;
  if (dates.has(today)) anchor = today;
  else if (dates.has(shiftDate(today, -1))) anchor = shiftDate(today, -1);
  if (!anchor) return 0;
  let n = 0;
  let d = anchor;
  while (dates.has(d)) { n++; d = shiftDate(d, -1); }
  return n;
}

export type Aggregated = ReturnType<typeof aggregateStats>;

/** 区间内全部记录的 JS 聚合（个人数据量下性能足够），stats API 与档案导出共用 */
export function aggregateStats(from: string, to: string) {
  const rows = db.prepare('SELECT * FROM entries WHERE deleted = 0 AND entry_date >= ? AND entry_date <= ? ORDER BY entry_date, created_at').all(from, to) as any[];

  const moodByDay = new Map<string, { sum: number; n: number }>();
  const moodDist = new Map<number, number>();
  const emotions = new Map<string, number>();
  const tags = new Map<string, number>();
  const checkins = new Map<string, Set<string>>();
  const habitSnapNames = new Map<string, string>();
  const sleep: { date: string; v: number }[] = [];
  const weight: { date: string; v: number }[] = [];
  const steps: { date: string; v: number }[] = [];
  const countsByType: Record<string, number> = {};
  const days = new Set<string>();
  const timeline: { date: string; type: string; label: string; body: string; tags: string[] }[] = [];

  for (const r of rows) {
    const c = JSON.parse(r.content);
    const entryTags: string[] = JSON.parse(r.tags) || [];
    days.add(r.entry_date);
    countsByType[r.type] = (countsByType[r.type] || 0) + 1;
    for (const t of entryTags) tags.set(t, (tags.get(t) || 0) + 1);

    switch (r.type) {
      case 'mood': {
        const m = moodByDay.get(r.entry_date) || { sum: 0, n: 0 };
        m.sum += c.score; m.n++;
        moodByDay.set(r.entry_date, m);
        moodDist.set(c.score, (moodDist.get(c.score) || 0) + 1);
        for (const e of c.emotions || []) emotions.set(e, (emotions.get(e) || 0) + 1);
        if (c.note) timeline.push({ date: r.entry_date, type: 'mood', label: `心情 ${c.score}/5 ${emotionStr(c.emotions)}`, body: c.note, tags: entryTags });
        break;
      }
      case 'habit_checkin': {
        if (!checkins.has(c.habitId)) checkins.set(c.habitId, new Set());
        checkins.get(c.habitId)!.add(r.entry_date);
        if (c.habitName) habitSnapNames.set(c.habitId, c.habitName);
        break;
      }
      case 'health': {
        if (c.sleepHours != null) sleep.push({ date: r.entry_date, v: c.sleepHours });
        if (c.weightKg != null) weight.push({ date: r.entry_date, v: c.weightKg });
        if (c.steps != null) steps.push({ date: r.entry_date, v: c.steps });
        break;
      }
      case 'text':
        timeline.push({ date: r.entry_date, type: 'text', label: c.title || excerpt(c.body || ''), body: c.body || '', tags: entryTags });
        break;
      case 'photo':
        timeline.push({ date: r.entry_date, type: 'photo', label: c.caption ? `📷 ${c.caption}` : '📷 照片', body: c.caption || '', tags: entryTags });
        break;
      case 'dream':
        timeline.push({ date: r.entry_date, type: 'dream', label: excerpt(c.body || (c.emotions || []).join('、') || '梦境', 40), body: c.body || '', tags: entryTags });
        break;
    }
  }

  // 习惯定义表补充名称
  const habitDefs = new Map<string, any>();
  for (const h of db.prepare('SELECT * FROM habits WHERE deleted = 0').all() as any[]) {
    habitDefs.set(h.id, { name: h.name, emoji: h.emoji, color: h.color, schedule: JSON.parse(h.schedule_json) });
  }

  const habitsOut = [...checkins.entries()].map(([habitId, dateSet]) => {
    const def = habitDefs.get(habitId);
    const dates = [...dateSet];
    const totalDays = daysBetween(from, to);
    return {
      habitId,
      name: def?.name || habitSnapNames.get(habitId) || habitId,
      emoji: def?.emoji || '✅',
      dates,
      count: dates.length,
      streak: streak(dateSet),
      rate: totalDays > 0 ? Math.round((dates.length / totalDays) * 100) : 0,
    };
  }).sort((a, b) => b.count - a.count);

  // 情绪趋势：区间对半分比较
  const mid = shiftDate(from, Math.floor((daysBetween(from, to) - 1) / 2));
  const avg = (lo: string, hi: string) => {
    let s = 0, n = 0;
    for (const [d, m] of moodByDay) if (d >= lo && d <= hi) { s += m.sum; n += m.n; }
    return n ? +(s / n).toFixed(2) : null;
  };

  return {
    range: { from, to },
    counts: {
      byType: countsByType,
      totalEntries: rows.length,
      totalDays: days.size,
      avgDaysPerWeek: +(days.size / Math.max(daysBetween(from, to) / 7, 0.1)).toFixed(1),
    },
    mood: {
      avgByDay: [...moodByDay.entries()].map(([date, m]) => ({ date, avg: +(m.sum / m.n).toFixed(2), count: m.n })).sort((a, b) => a.date.localeCompare(b.date)),
      overallAvg: avg(from, to),
      firstHalfAvg: avg(from, shiftDate(mid, -1)),
      secondHalfAvg: avg(mid, to),
      distribution: [...moodDist.entries()].map(([score, count]) => ({ score, count })).sort((a, b) => a.score - b.score),
      emotionTop: [...emotions.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count).slice(0, 10),
    },
    tagsTop: [...tags.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count).slice(0, 15),
    habits: habitsOut,
    health: { sleep, weight, steps },
    timeline,
  };
}

function emotionStr(list?: string[]): string {
  return (list && list.length) ? list.join('、') : '';
}

function excerpt(s: string, n = 50): string {
  const t = (s || '').replace(/\s+/g, ' ').trim();
  return t.length > n ? t.slice(0, n) + '…' : t;
}

export function registerStats(app: FastifyInstance) {
  app.get('/api/stats/summary', async (req) => {
    const q: any = req.query;
    const to = q.to || localToday();
    const days = Math.min(Math.max(parseInt(q.days) || 90, 1), 3650);
    const from = q.from || shiftDate(to, -(days - 1));
    return aggregateStats(from, to);
  });
}
