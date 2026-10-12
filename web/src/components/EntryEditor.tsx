import { useEffect, useState } from 'react';
import type { Entry, EntryType, Habit, ImageInfo } from '../types';
import { MOODS, EMOTIONS, TYPE_META, todayStr } from '../constants';
import { api } from '../api';

const TYPES: EntryType[] = ['text', 'mood', 'dream', 'photo', 'habit_checkin', 'health'];

export default function EntryEditor({ entry, onClose, onSaved }: {
  entry?: Entry; onClose: () => void; onSaved: () => void;
}) {
  const editing = !!entry;
  const [type, setType] = useState<EntryType>(entry?.type || 'text');
  const [date, setDate] = useState(entry?.entryDate || todayStr());

  const [title, setTitle] = useState(entry?.content?.title || '');
  const [body, setBody] = useState(entry?.content?.body || '');

  const [score, setScore] = useState<number>(entry?.content?.score || 4);
  const [emotions, setEmotions] = useState<string[]>(entry?.content?.emotions || []);
  const [moodNote, setMoodNote] = useState(entry?.content?.note || '');

  const [caption, setCaption] = useState(entry?.content?.caption || '');
  const [images, setImages] = useState<ImageInfo[]>(
    (entry?.content?.imageIds || []).map((id: string) => ({ id, url: `/api/images/${id}/file` })),
  );
  const [uploading, setUploading] = useState(false);

  const [habits, setHabits] = useState<Habit[]>([]);
  const [habitId, setHabitId] = useState<string>(entry?.content?.habitId || '');

  const [sleep, setSleep] = useState<string>(entry?.content?.sleepHours?.toString() || '');
  const [weight, setWeight] = useState<string>(entry?.content?.weightKg?.toString() || '');
  const [steps, setSteps] = useState<string>(entry?.content?.steps?.toString() || '');

  const [dreamEmotions, setDreamEmotions] = useState<string[]>(entry?.content?.emotions || []);

  const [tags, setTags] = useState<string[]>(entry?.tags || []);
  const [tagInput, setTagInput] = useState('');
  const [recentTags, setRecentTags] = useState<string[]>([]);
  const [err, setErr] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api.get('/api/habits').then((r) => setHabits(r.items)).catch(() => {});
    api.get('/api/stats/summary?days=365')
      .then((s) => setRecentTags((s.tagsTop || []).map((t: any) => t.name)))
      .catch(() => {});
  }, []);

  const addTag = () => {
    const t = tagInput.trim().replace(/^#/, '');
    if (t && !tags.includes(t)) setTags([...tags, t]);
    setTagInput('');
  };

  const uploadFiles = async (files: FileList | null) => {
    if (!files?.length) return;
    setUploading(true);
    try {
      for (const f of Array.from(files)) {
        const info = await api.upload(f);
        setImages((arr) => [...arr, info]);
      }
    } catch (e: any) {
      setErr('图片上传失败：' + e.message);
    } finally {
      setUploading(false);
    }
  };

  const buildContent = (): any => {
    switch (type) {
      case 'text': return { title: title.trim() || undefined, body: body.trim() || undefined };
      case 'mood': return { score, emotions, note: moodNote.trim() || undefined };
      case 'photo': return { caption: caption.trim() || undefined, imageIds: images.map((i) => i.id) };
      case 'habit_checkin': {
        const h = habits.find((x) => x.id === habitId);
        return { habitId, habitName: h?.name };
      }
      case 'health': {
        const c: any = {};
        if (sleep !== '') c.sleepHours = Number(sleep);
        if (weight !== '') c.weightKg = Number(weight);
        if (steps !== '') c.steps = Number(steps);
        return c;
      }
      case 'dream': return { body: body.trim() || undefined, emotions: dreamEmotions };
    }
  };

  const save = async () => {
    setErr('');
    if (type === 'text' && !title.trim() && !body.trim()) return setErr('标题和正文至少填一项');
    if (type === 'dream' && !body.trim() && dreamEmotions.length === 0) return setErr('梦境描述和醒来情绪至少填一项');
    if (type === 'photo' && !images.length) return setErr('请至少添加一张照片');
    if (type === 'habit_checkin' && !habitId) return setErr('请选择要打卡的习惯');
    if (type === 'health' && sleep === '' && weight === '' && steps === '') return setErr('至少填写一项健康数据');
    setSaving(true);
    try {
      const payload = { type, content: buildContent(), tags, entryDate: date };
      if (editing) await api.put('/api/entries/' + entry!.id, payload);
      else await api.post('/api/entries', payload);
      onSaved();
    } catch (e: any) {
      setErr(e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="modal-mask" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal">
        <h3>{editing ? '编辑记录' : '写一条记录'}</h3>

        {!editing && (
          <div className="editor-types">
            {TYPES.map((t) => (
              <button key={t} className={type === t ? 'on' : ''} onClick={() => setType(t)}>
                {TYPE_META[t].icon} {TYPE_META[t].label}
              </button>
            ))}
          </div>
        )}

        {type === 'text' && (
          <>
            <div className="field"><label>标题（可选）</label>
              <input type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="今天发生了什么…" maxLength={200} />
            </div>
            <div className="field"><label>正文</label>
              <textarea value={body} onChange={(e) => setBody(e.target.value)} placeholder="写下此刻的想法、经历、感受…" />
            </div>
          </>
        )}

        {type === 'mood' && (
          <>
            <div className="field"><label>现在的心情</label>
              <div className="mood-picker">
                {MOODS.map((m) => (
                  <button key={m.score} className={score === m.score ? 'on' : ''} onClick={() => setScore(m.score)}>
                    <span className="me">{m.emoji}</span><span className="ml">{m.label}</span>
                  </button>
                ))}
              </div>
            </div>
            <div className="field"><label>情绪词（可多选）</label>
              <div className="emotion-pool">
                {EMOTIONS.map((e) => (
                  <button key={e} className={emotions.includes(e) ? 'on' : ''}
                    onClick={() => setEmotions(emotions.includes(e) ? emotions.filter((x) => x !== e) : [...emotions, e])}>
                    {e}
                  </button>
                ))}
              </div>
            </div>
            <div className="field"><label>补充一句（可选）</label>
              <input type="text" value={moodNote} onChange={(e) => setMoodNote(e.target.value)} placeholder="为什么是这个心情？" />
            </div>
          </>
        )}

        {type === 'dream' && (
          <>
            <div className="field"><label>梦见什么了？</label>
              <textarea value={body} onChange={(e) => setBody(e.target.value)} placeholder="趁还记得，把梦记下来：场景、人物、情节、细节…" />
            </div>
            <div className="field"><label>醒来情绪（可多选）</label>
              <div className="emotion-pool">
                {EMOTIONS.map((e) => (
                  <button key={e} className={dreamEmotions.includes(e) ? 'on' : ''}
                    onClick={() => setDreamEmotions(dreamEmotions.includes(e) ? dreamEmotions.filter((x) => x !== e) : [...dreamEmotions, e])}>
                    {e}
                  </button>
                ))}
              </div>
            </div>
          </>
        )}

        {type === 'photo' && (
          <div className="photo-pick">
            <div className="field"><label>照片（可多选，上传后自动压缩显示）</label>
              <input type="file" accept="image/*" multiple onChange={(e) => uploadFiles(e.target.files)} />
              {uploading && <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>上传中…</div>}
              <div className="thumbs">
                {images.map((img) => (
                  <div className="thumb" key={img.id}>
                    <img src={img.url} alt="" />
                    <button className="rm" onClick={() => setImages(images.filter((x) => x.id !== img.id))}>✕</button>
                  </div>
                ))}
              </div>
            </div>
            <div className="field"><label>配一句话（可选）</label>
              <input type="text" value={caption} onChange={(e) => setCaption(e.target.value)} placeholder="记录这个瞬间…" />
            </div>
          </div>
        )}

        {type === 'habit_checkin' && (
          <div className="field"><label>选择习惯</label>
            <div className="habit-pick">
              {habits.length === 0 && <div style={{ fontSize: 13, color: 'var(--ink-3)' }}>还没有习惯，去手机 App 创建，或先记录其他类型。</div>}
              {habits.map((h) => (
                <label key={h.id} className={habitId === h.id ? 'on' : ''} onClick={() => setHabitId(h.id)}>
                  <span style={{ fontSize: 18 }}>{h.emoji}</span> {h.name}
                </label>
              ))}
            </div>
          </div>
        )}

        {type === 'health' && (
          <>
            <div className="field"><label>睡眠时长（小时）</label>
              <input type="number" step="0.5" min="0" max="24" value={sleep} onChange={(e) => setSleep(e.target.value)} placeholder="如 7.5" />
            </div>
            <div className="field"><label>体重（kg）</label>
              <input type="number" step="0.1" min="20" max="300" value={weight} onChange={(e) => setWeight(e.target.value)} placeholder="如 65.2" />
            </div>
            <div className="field"><label>步数</label>
              <input type="number" step="1" min="0" value={steps} onChange={(e) => setSteps(e.target.value)} placeholder="如 8000" />
            </div>
          </>
        )}

        <div className="field"><label>标签（可选，点选常用或回车新建）</label>
          {recentTags.filter((t) => !tags.includes(t)).length > 0 && (
            <div className="emotion-pool" style={{ marginBottom: 8 }}>
              {recentTags.filter((t) => !tags.includes(t)).slice(0, 12).map((t) => (
                <button type="button" key={t} onClick={() => setTags([...tags, t])}>#{t}</button>
              ))}
            </div>
          )}
          <input type="text" value={tagInput} onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTag(); } }}
            onBlur={addTag} placeholder="新标签，回车添加" />
          {tags.length > 0 && (
            <div className="entry-tags" style={{ marginTop: 8 }}>
              {tags.map((t) => (
                <span className="tag-chip" key={t} style={{ cursor: 'pointer' }} onClick={() => setTags(tags.filter((x) => x !== t))}>
                  #{t} ✕
                </span>
              ))}
            </div>
          )}
        </div>

        <div className="field"><label>日期（可补记）</label>
          <input type="date" value={date} max={todayStr()} onChange={(e) => setDate(e.target.value)} />
        </div>

        {err && <div className="editor-err">{err}</div>}

        <div className="editor-footer">
          <button className="btn" onClick={onClose}>取消</button>
          <button className="btn primary" onClick={save} disabled={saving || uploading}>
            {saving ? '保存中…' : '保存'}
          </button>
        </div>
      </div>
    </div>
  );
}
