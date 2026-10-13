/**
 * 旧日记解析器：把 md/txt/json 文本解析成可导入的 entries。
 * 支持（自动识别）：
 * 1. 顶栏 JSON：[{entryDate,type,content,tags}]（本应用「全量备份」格式）
 * 2. Markdown/纯文本：
 *    - 「# 2026-01-15」或「## 2026年1月15日」或「[2026-01-15]」作为日期分隔
 *    - 日期行之后到下一个日期行之前的内容 = 该天一条文字记录
 *    - 行内 #标签 会在服务端自动提取，这里无需处理
 *    - 无任何日期行时：整体作为一条导入到指定默认日期
 */
export interface ParsedEntry {
  entryDate: string;
  type: string;
  content: any;
  tags?: string[];
}

export interface ParseResult {
  entries: ParsedEntry[];
  unparsed: string[]; // 无法归入任何日期的内容片段（通常为空）
}

const DATE_PATTERNS: RegExp[] = [
  /^#{1,3}\s*(\d{4})[-/.年](\d{1,2})[-/.月](\d{1,2})日?\s*.*$/,
  /^[([]\s*(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\s*[)\]].*$/,
  /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\s*[（(]?周[一二三四五六日天][)）]?\s*$/,
  /^\s*(\d{4})-(\d{2})-(\d{2})\s*$/, // ISO 行
];

function matchDate(line: string): string | null {
  for (const re of DATE_PATTERNS) {
    const m = line.match(re);
    if (m) {
      const y = +m[1], mo = +m[2], d = +m[3];
      if (mo >= 1 && mo <= 12 && d >= 1 && d <= 31) {
        return `${y}-${String(mo).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
      }
    }
  }
  return null;
}

export function parseLegacyDiary(text: string, defaultDate?: string): ParseResult {
  const trimmed = text.trim();
  if (!trimmed) return { entries: [], unparsed: [] };

  // 1) 尝试 JSON
  if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
    try {
      const obj = JSON.parse(trimmed);
      const list: any[] = Array.isArray(obj) ? obj : (obj.entries || []);
      const entries: ParsedEntry[] = [];
      const unparsed: string[] = [];
      for (const e of list) {
        if (e && typeof e.entryDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(e.entryDate) && e.content) {
          entries.push({
            entryDate: e.entryDate,
            type: e.type || 'text',
            content: typeof e.content === 'string' ? JSON.parse(e.content) : e.content,
            tags: e.tags || (typeof e.tagsJson === 'string' ? JSON.parse(e.tagsJson) : undefined),
          });
        } else {
          unparsed.push(JSON.stringify(e).slice(0, 60));
        }
      }
      if (entries.length) return { entries, unparsed };
    } catch { /* 不是合法 JSON，继续按文本解析 */ }
  }

  // 2) 按日期分隔行切分
  const lines = text.split(/\r?\n/);
  const buckets = new Map<string, string[]>();
  const order: string[] = [];
  let currentDate: string | null = null;
  const headerless: string[] = [];

  for (const line of lines) {
    const d = matchDate(line.trim());
    if (d) {
      currentDate = d;
      if (!buckets.has(d)) { buckets.set(d, []); order.push(d); }
      // 日期行本身若带正文（如「2026-01-15 今天去爬山」）保留后半句
      const rest = line.trim().replace(/^.*?\d{4}[-/.年]\d{1,2}[-/.月]\d{1,2}日?\s*/, '').trim();
      if (rest) buckets.get(d)!.push(rest);
      continue;
    }
    (currentDate ? buckets.get(currentDate)! : headerless).push(line);
  }

  const entries: ParsedEntry[] = [];
  for (const d of order) {
    const body = buckets.get(d)!.join('\n').trim();
    if (body) entries.push({ entryDate: d, type: 'text', content: { body } });
  }
  // 无日期头的散落内容 → 归入默认日期（或丢弃并报告）
  const rest = headerless.join('\n').trim();
  const unparsed: string[] = [];
  if (rest) {
    if (defaultDate) entries.push({ entryDate: defaultDate, type: 'text', content: { body: rest } });
    else unparsed.push(`${rest.slice(0, 50)}…（无日期，未导入）`);
  }
  // 去掉日期行可能留下的空标题
  return { entries, unparsed };
}
