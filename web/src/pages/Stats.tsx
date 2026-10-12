import { useEffect, useMemo, useState } from 'react';
import * as echarts from 'echarts';
import { fetchStats } from '../api';
import type { StatsSummary } from '../types';
import { MOODS, moodOf } from '../constants';
import Chart from '../components/Chart';

const INK2 = '#6f6a61';
const AXIS = { axisLine: { lineStyle: { color: '#d9d3c8' } }, axisLabel: { color: INK2, fontSize: 11 }, splitLine: { lineStyle: { color: '#f0ece5' } } };

export default function Stats({ refreshKey }: { refreshKey: number }) {
  const [days, setDays] = useState(90);
  const [s, setS] = useState<StatsSummary | null>(null);

  useEffect(() => {
    fetchStats(days).then(setS).catch(() => setS(null));
  }, [days, refreshKey]);

  const heat = useMemo(() => buildHeat(s), [s]);

  if (!s) return <div className="container empty-tip">统计加载中…</div>;

  const moodOption = {
    grid: { left: 40, right: 16, top: 20, bottom: 28 },
    xAxis: { type: 'category', data: s.mood.avgByDay.map((d) => d.date.slice(5)), ...AXIS },
    yAxis: { type: 'value', min: 1, max: 5, ...AXIS },
    tooltip: { trigger: 'axis', formatter: (p: any) => `${p[0].name}<br/>心情均值 ${p[0].value}/5` },
    series: [{
      type: 'line', data: s.mood.avgByDay.map((d) => d.avg), smooth: true, symbolSize: 6,
      lineStyle: { color: '#7c9a6d', width: 2.5 }, itemStyle: { color: '#7c9a6d' },
      areaStyle: { color: grad('#7c9a6d') },
    }],
  } as any;

  const distOption = {
    grid: { left: 40, right: 16, top: 20, bottom: 28 },
    xAxis: { type: 'category', data: s.mood.distribution.map((d) => `${moodOf(d.score).emoji} ${d.score}分`), ...AXIS },
    yAxis: { type: 'value', ...AXIS },
    tooltip: {},
    series: [{
      type: 'bar', barWidth: 36,
      data: s.mood.distribution.map((d) => ({ value: d.count, itemStyle: { color: moodOf(d.score).color, borderRadius: [6, 6, 0, 0] } })),
    }],
  } as any;

  const emotionOption = {
    grid: { left: 70, right: 30, top: 10, bottom: 24 },
    xAxis: { type: 'value', ...AXIS },
    yAxis: { type: 'category', data: s.mood.emotionTop.map((e) => e.name).reverse(), ...AXIS },
    tooltip: {},
    series: [{
      type: 'bar', barWidth: 14,
      data: s.mood.emotionTop.map((e) => e.count).reverse(),
      itemStyle: { color: '#d9a558', borderRadius: [0, 6, 6, 0] },
    }],
  } as any;

  const healthLine = (data: { date: string; v: number }[], color: string, unit: string) => ({
    grid: { left: 46, right: 16, top: 20, bottom: 28 },
    xAxis: { type: 'category', data: data.map((d) => d.date.slice(5)), ...AXIS },
    yAxis: { type: 'value', ...AXIS, axisLabel: { ...AXIS.axisLabel, formatter: `{value}${unit}` } },
    tooltip: { trigger: 'axis' },
    series: [{ type: 'line', data: data.map((d) => d.v), smooth: true, symbolSize: 5, lineStyle: { color, width: 2 }, itemStyle: { color } }],
  } as any);

  return (
    <div className="container">
      <div className="stats-toolbar">
        <div className="seg">
          {[30, 90, 180, 365].map((d) => (
            <button key={d} className={days === d ? 'on' : ''} onClick={() => setDays(d)}>近 {d} 天</button>
          ))}
        </div>
        <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>{s.range.from} ~ {s.range.to}</span>
      </div>

      <div className="overview-cards">
        <div className="ov-card"><div className="v">{s.counts.totalEntries}</div><div className="k">总记录（条）</div></div>
        <div className="ov-card"><div className="v">{s.counts.totalDays}</div><div className="k">有记录的天数</div></div>
        <div className="ov-card"><div className="v">{s.counts.avgDaysPerWeek}</div><div className="k">平均每周记录（天）</div></div>
        <div className="ov-card">
          <div className="v">{s.mood.overallAvg != null ? `${s.mood.overallAvg} / 5` : '—'}</div>
          <div className="k">心情均值 {trendText(s)}</div>
        </div>
      </div>

      {s.mood.avgByDay.length > 0 && (
        <div className="chart-card">
          <h4>💭 心情走势</h4>
          <Chart option={moodOption} />
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
        {s.mood.distribution.length > 0 && (
          <div className="chart-card"><h4>心情分布</h4><Chart option={distOption} /></div>
        )}
        {s.mood.emotionTop.length > 0 && (
          <div className="chart-card"><h4>高频情绪</h4><Chart option={emotionOption} /></div>
        )}
      </div>

      {s.habits.length > 0 && (
        <div className="chart-card">
          <h4>✅ 习惯打卡热力图（每格一天，从左到右）</h4>
          <div className="heat-wrap">{heat}</div>
          <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', marginTop: 10, fontSize: 13, color: 'var(--ink-2)' }}>
            {s.habits.map((h) => (
              <span key={h.habitId}>{h.emoji} {h.name}：{h.count} 天 · 坚持率 {h.rate}% · 当前连续 <b>{h.streak}</b> 天</span>
            ))}
          </div>
        </div>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 16 }}>
        {s.health.sleep.length > 1 && (
          <div className="chart-card"><h4>😴 睡眠（小时）</h4><Chart option={healthLine(s.health.sleep, '#7986cb', 'h')} /></div>
        )}
        {s.health.steps.length > 1 && (
          <div className="chart-card"><h4>👟 步数</h4><Chart option={healthLine(s.health.steps, '#4db6ac', '')} /></div>
        )}
        {s.health.weight.length > 1 && (
          <div className="chart-card"><h4>⚖️ 体重（kg）</h4><Chart option={healthLine(s.health.weight, '#e57373', 'kg')} /></div>
        )}
      </div>

      {s.tagsTop.length > 0 && (
        <div className="chart-card">
          <h4>🏷️ 高频标签</h4>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {s.tagsTop.map((t) => (
              <span className="tag-chip" key={t.name}>#{t.name} × {t.count}</span>
            ))}
          </div>
        </div>
      )}

      {s.counts.totalEntries === 0 && (
        <div className="empty-tip"><div className="e">📉</div>这个区间还没有数据，先去记几条吧</div>
      )}
    </div>
  );
}

function trendText(s: StatsSummary): string {
  const { firstHalfAvg: a, secondHalfAvg: b } = s.mood;
  if (a == null || b == null) return '';
  if (b > a) return `（后半段 ↑）`;
  if (b < a) return `（后半段 ↓）`;
  return '（平稳）';
}

function buildHeat(s: StatsSummary | null) {
  if (!s || !s.habits.length) return null;
  const dates = new Set<string>();
  for (const h of s.habits) h.dates.forEach((d) => dates.add(d));
  const all = [...dates].sort();
  if (!all.length) return null;
  const from = new Date(s.range.from + 'T00:00:00');
  const to = new Date(s.range.to + 'T00:00:00');
  const dayCount = Math.min(Math.round((+to - +from) / 86400000) + 1, 182); // 最多显示 26 周
  const start = new Date(to.getTime() - (dayCount - 1) * 86400000);

  return s.habits.map((h) => {
    const set = new Set(h.dates);
    const cells = [];
    for (let i = 0; i < dayCount; i++) {
      const d = new Date(start.getTime() + i * 86400000);
      const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
      cells.push(<div key={key} className="heat-cell" title={`${key}${set.has(key) ? ' ✓' : ''}`}
        style={set.has(key) ? { background: '#7c9a6d' } : undefined} />);
    }
    return (
      <div className="heat-row" key={h.habitId}>
        <span className="heat-name">{h.emoji} {h.name}</span>
        <div className="heat-cells">{cells}</div>
      </div>
    );
  });
}

function grad(color: string) {
  return new echarts.graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: color + '33' },
    { offset: 1, color: color + '05' },
  ]);
}
