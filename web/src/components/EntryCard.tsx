import { useState } from 'react';
import type { Entry } from '../types';
import { TYPE_META, moodOf, fmtTime } from '../constants';
import { api } from '../api';

const base = () => localStorage.getItem('sd_base') || '';

export default function EntryCard({ entry, onEdit, onChanged }: {
  entry: Entry; onEdit: (e: Entry) => void; onChanged: () => void;
}) {
  const [viewer, setViewer] = useState<string | null>(null);
  const del = async () => {
    if (!confirm('确定删除这条记录吗？')) return;
    await api.del('/api/entries/' + entry.id);
    onChanged();
  };

  return (
    <div className="entry-card">
      <div className="entry-ops">
        <button className="btn ghost" onClick={() => onEdit(entry)} style={{ padding: '4px 8px' }}>编辑</button>
        <button className="btn ghost" onClick={del} style={{ padding: '4px 8px', color: 'var(--danger)' }}>删除</button>
      </div>
      <div className="entry-time">{TYPE_META[entry.type]?.icon} {fmtTime(entry.createdAt)}</div>

      {entry.type === 'text' && (
        <div>
          {entry.content.title && <div className="entry-text-title">{entry.content.title}</div>}
          <div className="entry-text-body">{entry.content.body}</div>
        </div>
      )}

      {entry.type === 'dream' && (
        <div>
          <div className="entry-text-body">{entry.content.body}</div>
          {entry.content.emotions?.length > 0 && (
            <div className="mood-emotions" style={{ marginTop: 6 }}>
              醒来：<div style={{ display: 'contents' }}>
                {entry.content.emotions.map((e: string) => <span className="mood-emotion" key={e}>{e}</span>)}
              </div>
            </div>
          )}
        </div>
      )}

      {entry.type === 'mood' && (
        <div className="mood-row">
          <div className="mood-emoji">{moodOf(entry.content.score).emoji}</div>
          <div className="mood-info">
            <div className="mood-score-word">今日心情 {entry.content.score}/5 · {moodOf(entry.content.score).label}</div>
            <div className="mood-emotions">
              {(entry.content.emotions || []).map((e: string) => <span className="mood-emotion" key={e}>{e}</span>)}
            </div>
            {entry.content.note && <div className="mood-note">{entry.content.note}</div>}
          </div>
        </div>
      )}

      {entry.type === 'photo' && (
        <div>
          <div className="photo-grid">
            {(entry.content.imageIds || []).map((id: string) => (
              <img key={id} src={imgSrc(id)} alt="" loading="lazy" onClick={() => setViewer(imgSrc(id))} />
            ))}
          </div>
          {entry.content.caption && <div className="photo-caption">{entry.content.caption}</div>}
        </div>
      )}

      {entry.type === 'habit_checkin' && (
        <div className="habit-row">
          <span className="habit-badge">✅ 完成打卡 · {entry.content.habitName || '习惯'}</span>
        </div>
      )}

      {entry.type === 'health' && (
        <div className="health-row">
          {entry.content.sleepHours != null && (
            <div className="health-item"><span className="v">😴 {entry.content.sleepHours}<small> 小时</small></span><span className="k">睡眠</span></div>
          )}
          {entry.content.weightKg != null && (
            <div className="health-item"><span className="v">⚖️ {entry.content.weightKg}<small> kg</small></span><span className="k">体重</span></div>
          )}
          {entry.content.steps != null && (
            <div className="health-item"><span className="v">👟 {entry.content.steps?.toLocaleString()}</span><span className="k">步数</span></div>
          )}
        </div>
      )}

      {entry.tags?.length > 0 && (
        <div className="entry-tags">
          {entry.tags.map((t) => <span className="tag-chip" key={t}>#{t}</span>)}
        </div>
      )}

      {viewer && (
        <div className="img-viewer" onClick={() => setViewer(null)}>
          <img src={viewer} alt="" />
        </div>
      )}
    </div>
  );

  function imgSrc(id: string): string {
    return `${base()}/api/images/${id}/file`;
  }
}
