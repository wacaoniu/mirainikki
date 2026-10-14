import type { FastifyInstance } from 'fastify';
import { db } from '../db.js';
import { localToday } from '../lib/util.js';
import { aggregateStats, shiftDate } from './stats.js';

function weekKey(d: string): string {
  // 周一为一周开始
  const dt = new Date(d + 'T00:00:00Z');
  const dow = (dt.getUTCDay() + 6) % 7;
  const mon = new Date(dt);
  mon.setUTCDate(dt.getUTCDate() - dow);
  return mon.toISOString().slice(0, 10);
}

function avgArr(arr: { v: number }[]): number | null {
  if (!arr.length) return null;
  return +(arr.reduce((s, x) => s + x.v, 0) / arr.length).toFixed(1);
}

export function buildProfileMarkdown(days: number): string {
  const to = localToday();
  const from = shiftDate(to, -(days - 1));
  const s = aggregateStats(from, to);
  const L: string[] = [];
  const p = (line = '') => L.push(line);

  p(`# 关于我 —— 个人记忆档案`);
  p();
  p(`> 生成时间：${new Date().toLocaleString('zh-CN')}  `);
  p(`> 数据范围：${from} ~ ${to}（近 ${days} 天，共 ${s.counts.totalEntries} 条记录，${s.counts.totalDays} 天有记录）`);
  p();

  p(`## 一、生活概览`);
  p();
  p(`- 记录频率：平均每周 ${s.counts.avgDaysPerWeek} 天有记录`);
  const typeNames: Record<string, string> = { text: '文字日记', mood: '心情打卡', photo: '照片', habit_checkin: '习惯打卡', health: '健康数据', dream: '梦境记录' };
  const typeLines = Object.entries(s.counts.byType).sort((a, b) => b[1] - a[1]).map(([t, n]) => `${typeNames[t] || t} ${n} 条`);
  p(`- 记录构成：${typeLines.join('，') || '暂无'}`);
  if (s.tagsTop.length) p(`- 高频标签：${s.tagsTop.slice(0, 8).map((t) => `${t.name}(${t.count})`).join('、')}`);
  if (s.mood.emotionTop.length) p(`- 高频情绪：${s.mood.emotionTop.slice(0, 6).map((t) => `${t.name}(${t.count})`).join('、')}`);
  if (s.mood.overallAvg != null) {
    const trend = s.mood.firstHalfAvg != null && s.mood.secondHalfAvg != null
      ? (s.mood.secondHalfAvg > s.mood.firstHalfAvg ? `较前期 ↑（${s.mood.firstHalfAvg} → ${s.mood.secondHalfAvg}）`
        : s.mood.secondHalfAvg < s.mood.firstHalfAvg ? `较前期 ↓（${s.mood.firstHalfAvg} → ${s.mood.secondHalfAvg}）`
        : '与前期持平')
      : '';
    p(`- 情绪状态：近 ${days} 天均值 **${s.mood.overallAvg} / 5**，${trend}`);
    p(`- 情绪分布：${s.mood.distribution.map((d) => `${d.score} 分 × ${d.count}`).join('，')}`);
  }
  p();

  p(`## 二、习惯与健康`);
  p();
  if (s.habits.length) {
    for (const h of s.habits) {
      p(`- ${h.emoji} **${h.name}**：打卡 ${h.count} 天（坚持率 ${h.rate}%），当前连续 ${h.streak} 天`);
    }
  } else {
    p(`- 暂无习惯打卡记录`);
  }
  const sleepAvg = avgArr(s.health.sleep);
  const stepsAvg = avgArr(s.health.steps);
  if (sleepAvg != null) p(`- 睡眠：平均 ${sleepAvg} 小时/天`);
  if (s.health.weight.length >= 2) {
    const w1 = s.health.weight[0], w2 = s.health.weight[s.health.weight.length - 1];
    const delta = +(w2.v - w1.v).toFixed(1);
    p(`- 体重：${w1.v}kg → ${w2.v}kg（${delta > 0 ? '+' : ''}${delta}kg）`);
  }
  if (stepsAvg != null) p(`- 步数：平均 ${Math.round(stepsAvg)} 步/天`);
  if (sleepAvg == null && stepsAvg == null && !s.health.weight.length && !s.habits.length) p(`- 暂无数据`);
  p();

  p(`## 三、近期时间线要点（按周）`);
  p();
  const weeks = new Map<string, typeof s.timeline>();
  for (const t of s.timeline) {
    const wk = weekKey(t.date);
    if (!weeks.has(wk)) weeks.set(wk, []);
    weeks.get(wk)!.push(t);
  }
  const weekList = [...weeks.entries()].sort((a, b) => b[0].localeCompare(a[0]));
  if (!weekList.length) {
    p(`暂无时间线记录。`);
  }
  for (const [wk, items] of weekList) {
    const end = shiftDate(wk, 6);
    p(`### ${wk} ~ ${end > to ? to : end}`);
    const weekTags = new Map<string, number>();
    for (const it of items) for (const tg of it.tags) weekTags.set(tg, (weekTags.get(tg) || 0) + 1);
    const moodsOfWeek = s.mood.avgByDay.filter((m) => m.date >= wk && m.date <= end);
    const moodAvg = moodsOfWeek.length ? (moodsOfWeek.reduce((x, m) => x + m.avg, 0) / moodsOfWeek.length).toFixed(1) : null;
    const metaParts: string[] = [`${items.length} 条记录`];
    if (moodAvg) metaParts.push(`情绪均值 ${moodAvg}/5`);
    if (weekTags.size) metaParts.push(`标签：${[...weekTags.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([t]) => t).join('、')}`);
    p(`- ${metaParts.join('；')}`);
    for (const it of items.slice(0, 12)) {
      const icon = it.type === 'text' ? '📝' : it.type === 'photo' ? '📷' : it.type === 'dream' ? '🌙' : '💭';
      const withBody = it.body && (it.type === 'text' || it.type === 'dream')
        ? ' —— ' + it.body.replace(/\s+/g, ' ').slice(0, 80) : '';
      p(`  - ${it.date} ${icon} ${it.label}${withBody}`);
    }
    if (items.length > 12) p(`  - …另有 ${items.length - 12} 条`);
    p();
  }

  p(`## 四、给 AI 的使用说明`);
  p();
  p(`这份档案由「未来日记」App 基于本人真实生活记录自动生成。你可以：`);
  p(`1. 结合档案内容回答关于我的问题、给我建议时参考我的真实状态；`);
  p(`2. 注意情绪与习惯的趋势变化，而非单条记录；`);
  p(`3. 档案中未提及的方面，不代表没发生，只是未被记录，可以正常询问我。`);
  p();
  return L.join('\n');
}

export function registerExport(app: FastifyInstance) {
  app.get('/api/export/profile', async (req, reply) => {
    const q: any = req.query;
    const days = Math.min(Math.max(parseInt(q.days) || 90, 7), 3650);
    const md = buildProfileMarkdown(days);
    reply.header('content-type', 'text/markdown; charset=utf-8');
    if (q.download !== undefined) reply.header('content-disposition', `attachment; filename="my-profile-${days}d.md"`);
    return md;
  });

  app.get('/api/export/full', async () => {
    const entries = db.prepare('SELECT * FROM entries ORDER BY entry_date, created_at').all() as any[];
    const habits = db.prepare('SELECT * FROM habits').all() as any[];
    const images = db.prepare('SELECT * FROM images').all() as any[];
    const toJson = (r: any) => {
      const { text_index, ...rest } = r;
      return { ...rest, content: JSON.parse(r.content), tags: JSON.parse(r.tags) };
    };
    return { exportedAt: new Date().toISOString(), entries: entries.map(toJson), habits: habits.map((h) => ({ ...h, schedule: JSON.parse(h.schedule_json) })), images };
  });
}
