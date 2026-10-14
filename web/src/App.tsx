import { useEffect, useState } from 'react';
import { api, clearAuth } from './api';
import Login from './pages/Login';
import Timeline from './pages/Timeline';
import Stats from './pages/Stats';
import Profile from './pages/Profile';
import EntryEditor from './components/EntryEditor';

type Tab = 'timeline' | 'stats' | 'profile';

export default function App() {
  const [authed, setAuthed] = useState<boolean | null>(null);
  const [tab, setTab] = useState<Tab>('timeline');
  const [editing, setEditing] = useState<{ open: boolean; entry?: any; refreshKey?: number }>({ open: false });
  const [refreshKey, setRefreshKey] = useState(0);

  useEffect(() => {
    api.get('/api/habits').then(() => setAuthed(true)).catch((e) => {
      setAuthed(e.status === 401 ? false : true); // 非 401（如网络错误）先放行到页面内处理
      if (e.status !== 401) setAuthed(null), console.warn(e);
    });
  }, []);

  if (authed === null) return <div className="loading">连接中…</div>;
  if (!authed) return <Login onAuthed={() => setAuthed(true)} />;

  const bump = () => setRefreshKey((k) => k + 1);

  return (
    <div className="app">
      <header className="topbar">
        <div className="logo">📔 未来日记 <span className="logo-sub">随手记，AI 帮你记着呢</span></div>
        <nav className="tabs">
          <button className={tab === 'timeline' ? 'on' : ''} onClick={() => setTab('timeline')}>时间线</button>
          <button className={tab === 'stats' ? 'on' : ''} onClick={() => setTab('stats')}>统计</button>
          <button className={tab === 'profile' ? 'on' : ''} onClick={() => setTab('profile')}>AI 档案</button>
        </nav>
        <div className="topbar-right">
          <button className="btn primary" onClick={() => setEditing({ open: true })}>＋ 写记录</button>
          <button className="btn ghost" title="退出登录" onClick={() => { clearAuth(); location.reload(); }}>退出</button>
        </div>
      </header>

      <main className="main">
        {tab === 'timeline' && <Timeline refreshKey={refreshKey} onEdit={(entry) => setEditing({ open: true, entry })} onChanged={bump} />}
        {tab === 'stats' && <Stats refreshKey={refreshKey} />}
        {tab === 'profile' && <Profile />}
      </main>

      {editing.open && (
        <EntryEditor
          entry={editing.entry}
          onClose={() => setEditing({ open: false })}
          onSaved={() => { setEditing({ open: false }); bump(); }}
        />
      )}
    </div>
  );
}
