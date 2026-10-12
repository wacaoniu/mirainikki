export const MOODS = [
  { score: 1, emoji: '😢', color: '#EF5350', label: '糟糕' },
  { score: 2, emoji: '😕', color: '#FF8A65', label: '较差' },
  { score: 3, emoji: '😐', color: '#FFD54F', label: '一般' },
  { score: 4, emoji: '🙂', color: '#9CCC65', label: '不错' },
  { score: 5, emoji: '😄', color: '#66BB6A', label: '很好' },
] as const;

export const moodOf = (score: number) => MOODS.find((m) => m.score === score) || MOODS[2];

export const EMOTIONS = [
  '开心', '平静', '满足', '兴奋', '幸福', '期待', '感动', '惊喜',
  '疲惫', '焦虑', '烦躁', '委屈', '孤独', '悲伤', '愤怒', '无力',
];

export const TYPE_META: Record<string, { label: string; icon: string }> = {
  text: { label: '文字', icon: '📝' },
  mood: { label: '心情', icon: '💭' },
  photo: { label: '照片', icon: '📷' },
  habit_checkin: { label: '习惯', icon: '✅' },
  health: { label: '健康', icon: '💪' },
  dream: { label: '梦境', icon: '🌙' },
};

export function fmtDay(dateStr: string): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  const week = ['日', '一', '二', '三', '四', '五', '六'][new Date(y, m - 1, d).getDay()];
  const now = new Date();
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
  if (dateStr === today) return `今天（${m}月${d}日 · 周${week}）`;
  const yesterday = new Date(now.getTime() - 86400000);
  const yStr = `${yesterday.getFullYear()}-${String(yesterday.getMonth() + 1).padStart(2, '0')}-${String(yesterday.getDate()).padStart(2, '0')}`;
  if (dateStr === yStr) return `昨天（${m}月${d}日 · 周${week}）`;
  return `${y !== now.getFullYear() ? y + '年' : ''}${m}月${d}日 · 周${week}`;
}

export function fmtTime(iso: string): string {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

export function todayStr(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
