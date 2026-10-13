import { useState } from 'react';
import { api } from '../api';
import { parseLegacyDiary, type ParsedEntry } from '../lib/legacyImport';

/** 导入旧日记：粘贴 md/txt 或 JSON（全量备份格式），解析预览后确认导入 */
export default function ImportDialog({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [text, setText] = useState('');
  const [parsed, setParsed] = useState<ParsedEntry[] | null>(null);
  const [unparsed, setUnparsed] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ imported: number; failed: number; errors: string[] } | null>(null);

  const analyze = () => {
    const r = parseLegacyDiary(text, undefined);
    setParsed(r.entries);
    setUnparsed(r.unparsed);
  };

  const doImport = async () => {
    if (!parsed?.length) return;
    setBusy(true);
    try {
      const r = await api.post<{ imported: number; failed: number; errors: string[] }>('/api/import', { entries: parsed });
      setResult(r);
      if (r.imported > 0) setTimeout(onDone, 1200);
    } finally {
      setBusy(false);
    }
  };

  const pickFile = async (f: File | undefined) => {
    if (!f) return;
    setText(await f.text());
    setParsed(null);
  };

  return (
    <div className="modal-mask" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal">
        <h3>📥 导入旧日记</h3>

        {!result && (
          <>
            <div style={{ fontSize: 13.5, color: 'var(--ink-2)', lineHeight: 1.7, marginBottom: 12 }}>
              支持两种方式（自动识别）：<br />
              <b>① 粘贴文本</b>：Markdown / 纯文本，以「<code># 2026-01-15</code>」「2026年1月15日」等日期行分天，每天存为一条文字记录；<br />
              <b>② 选择文件</b>：.md / .txt / .json（json 为本应用「全量备份」格式，可整库迁移）。
            </div>

            <div className="field">
              <label>选择文件（可选）</label>
              <input type="file" accept=".md,.txt,.json,text/markdown,text/plain,application/json" onChange={(e) => pickFile(e.target.files?.[0])} />
            </div>

            <div className="field">
              <label>或直接粘贴内容</label>
              <textarea value={text} onChange={(e) => { setText(e.target.value); setParsed(null); }}
                placeholder={"# 2026-01-15\n今天去爬山了，山顶风很大…\n\n# 2026-01-16\n和妈妈打电话聊了两小时 #家人"}
                style={{ minHeight: 160 }} />
            </div>

            <div className="editor-footer">
              <button className="btn" onClick={onClose}>取消</button>
              <button className="btn" onClick={analyze} disabled={!text.trim()}>解析预览</button>
            </div>

            {parsed && (
              <div style={{ marginTop: 14 }}>
                <div style={{ fontSize: 14, marginBottom: 6 }}>
                  解析结果：<b>{parsed.length}</b> 条记录
                  {parsed.length > 0 && `（${parsed[0].entryDate} ~ ${parsed[parsed.length - 1].entryDate}）`}
                  {unparsed.length > 0 && <span style={{ color: 'var(--danger)' }}>，{unparsed.length} 段无法识别</span>}
                </div>
                <div className="profile-md" style={{ maxHeight: 180, fontSize: 12.5, marginBottom: 10 }}>
                  {parsed.slice(0, 5).map((e, i) => (
                    <div key={i}>· {e.entryDate} [{e.type}] {String(e.content?.body || e.content?.title || JSON.stringify(e.content)).slice(0, 60)}</div>
                  ))}
                  {parsed.length > 5 && <div>…共 {parsed.length} 条</div>}
                  {unparsed.map((u, i) => <div key={'u' + i} style={{ color: 'var(--danger)' }}>⚠ {u}</div>)}
                </div>
                <div className="editor-footer">
                  <button className="btn" onClick={() => setParsed(null)}>重新编辑</button>
                  <button className="btn primary" onClick={doImport} disabled={busy || !parsed.length}>
                    {busy ? '导入中…' : `确认导入 ${parsed.length} 条`}
                  </button>
                </div>
              </div>
            )}
          </>
        )}

        {result && (
          <div style={{ textAlign: 'center', padding: '10px 0' }}>
            <div style={{ fontSize: 40 }}>{result.failed === 0 ? '✅' : '⚠️'}</div>
            <div style={{ fontSize: 16, margin: '8px 0' }}>
              成功导入 <b>{result.imported}</b> 条{result.failed > 0 && `，失败 ${result.failed} 条`}
            </div>
            {result.errors?.length > 0 && (
              <div className="profile-md" style={{ textAlign: 'left', fontSize: 12, maxHeight: 140 }}>
                {result.errors.map((e, i) => <div key={i}>{e}</div>)}
              </div>
            )}
            <button className="btn primary" style={{ marginTop: 12 }} onClick={onDone}>完成</button>
          </div>
        )}
      </div>
    </div>
  );
}
