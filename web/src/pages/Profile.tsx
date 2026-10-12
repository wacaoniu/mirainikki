import { useState } from 'react';
import { fetchProfile, api } from '../api';

export default function Profile() {
  const [days, setDays] = useState(90);
  const [md, setMd] = useState('');
  const [busy, setBusy] = useState(false);
  const [toast, setToast] = useState('');

  const generate = async () => {
    setBusy(true);
    try {
      setMd(await fetchProfile(days));
    } catch (e: any) {
      setMd('生成失败：' + e.message);
    } finally {
      setBusy(false);
    }
  };

  const copy = async () => {
    await navigator.clipboard.writeText(md);
    setToast('已复制到剪贴板，粘贴给任何 AI 即可');
    setTimeout(() => setToast(''), 2500);
  };

  const download = () => {
    const blob = new Blob([md], { type: 'text/markdown;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `my-profile-${days}d.md`;
    a.click();
    URL.revokeObjectURL(a.href);
  };

  const downloadFull = async () => {
    const data = await api.get('/api/export/full');
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `zhiji-backup-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
    setToast('全量备份已下载');
    setTimeout(() => setToast(''), 2500);
  };

  return (
    <div className="container">
      <div className="profile-toolbar">
        <div className="seg">
          {[30, 90, 365].map((d) => (
            <button key={d} className={days === d ? 'on' : ''} onClick={() => setDays(d)}>近 {d} 天</button>
          ))}
        </div>
        <button className="btn primary" onClick={generate} disabled={busy}>{busy ? '生成中…' : '⚡ 生成记忆档案'}</button>
        {md && !md.startsWith('生成失败') && (
          <>
            <button className="btn" onClick={copy}>📋 复制</button>
            <button className="btn" onClick={download}>⬇ 下载 .md</button>
          </>
        )}
        <span style={{ flex: 1 }} />
        <button className="btn ghost" onClick={downloadFull} title="下载全部记录的 JSON 备份">📦 全量备份</button>
      </div>

      <div style={{ fontSize: 13.5, color: 'var(--ink-2)', marginBottom: 14, lineHeight: 1.7 }}>
        记忆档案是一份结构化的个人生活报告：记录频率、情绪走势、习惯坚持、健康数据与时间线要点。<br />
        <b>把生成的档案粘贴给 ChatGPT / Claude / GLM 等任何 AI</b>，它就能基于你真实的生活数据来了解你、回答关于你的问题。
      </div>

      {md ? <div className="profile-md">{md}</div> : (
        <div className="empty-tip"><div className="e">🤖</div>点击「生成记忆档案」，喂给任何 AI 让它更懂你</div>
      )}

      {toast && <div className="toast">{toast}</div>}
    </div>
  );
}
