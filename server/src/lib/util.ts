import crypto from 'node:crypto';

export const uuid = () => crypto.randomUUID();
export const nowISO = () => new Date().toISOString();

/** 服务器本地时区的今天 YYYY-MM-DD（客户端通常会显式传 entry_date） */
export function localToday(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function isValidDate(s: unknown): s is string {
  return typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && !isNaN(Date.parse(s));
}

export function isValidISO(s: unknown): s is string {
  return typeof s === 'string' && !isNaN(Date.parse(s)) && /\d{4}-\d{2}-\d{2}T/.test(s);
}

export function sha256(s: string): string {
  return crypto.createHash('sha256').update(s).digest('hex');
}

/** 生成可搜索文本：各类型关键信息 + 标签 拼接 */
export function buildTextIndex(type: string, content: any, tags: string[]): string {
  const parts: string[] = [];
  switch (type) {
    case 'text':
      if (content.title) parts.push(content.title);
      if (content.body) parts.push(content.body);
      break;
    case 'mood':
      if (Array.isArray(content.emotions)) parts.push(content.emotions.join(' '));
      if (content.note) parts.push(content.note);
      break;
    case 'photo':
      if (content.caption) parts.push(content.caption);
      break;
    case 'habit_checkin':
      if (content.habitName) parts.push(`习惯打卡 ${content.habitName}`);
      break;
    case 'health': {
      const segs: string[] = [];
      if (content.sleepHours != null) segs.push(`睡眠 ${content.sleepHours} 小时`);
      if (content.weightKg != null) segs.push(`体重 ${content.weightKg} 公斤`);
      if (content.steps != null) segs.push(`步数 ${content.steps} 步`);
      if (segs.length) parts.push(`健康 ${segs.join(' ')}`);
      break;
    }
    case 'dream': {
      const segs: string[] = [];
      if (content.body) segs.push(content.body);
      if (Array.isArray(content.emotions)) segs.push(content.emotions.join(' '));
      if (segs.length) parts.push(`梦 ${segs.join(' ')}`);
      break;
    }
  }
  if (tags?.length) parts.push(tags.join(' '));
  return parts.join(' \n ').slice(0, 50000);
}

export function escapeLike(s: string): string {
  return s.replace(/[\\%_]/g, (c) => '\\' + c);
}
