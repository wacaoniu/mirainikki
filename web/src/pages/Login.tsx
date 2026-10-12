import { useEffect, useState } from 'react';
import { authStatus, authSetup, authLogin, setAuth, ApiError } from '../api';

export default function Login({ onAuthed }: { onAuthed: () => void }) {
  const [initialized, setInitialized] = useState<boolean | null>(null);
  const [password, setPassword] = useState('');
  const [password2, setPassword2] = useState('');
  const [serverBase, setServerBase] = useState(localStorage.getItem('sd_base') || '');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => { authStatus().then((r) => setInitialized(r.initialized)).catch(() => setInitialized(null)); }, []);

  const submit = async () => {
    setErr('');
    if (!password) return setErr('请输入密码');
    setBusy(true);
    try {
      setAuth(serverBase, ''); // 先设 base 再请求
      const r = initialized ? await authLogin(password) : await authSetup(password);
      setAuth(serverBase, r.token);
      onAuthed();
    } catch (e: any) {
      if (e instanceof ApiError && e.status === 409) { setInitialized(true); setErr('已初始化过，请直接登录'); }
      else setErr(e.message || '连接失败，请检查服务器地址');
    } finally {
      setBusy(false);
    }
  };

  if (initialized === null) {
    return <div className="login-wrap"><div className="login-card">
      <div className="big-emoji">📔</div>
      <h1>知己</h1>
      <p>无法连接服务器</p>
      <input type="text" value={serverBase} placeholder="服务器地址，如 http://192.168.1.10:8787（留空=当前站点）"
        onChange={(e) => setServerBase(e.target.value)} />
      <div className="login-err">{err}</div>
      <button className="btn primary" onClick={() => location.reload()}>重试</button>
    </div></div>;
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <div className="big-emoji">📔</div>
        <h1>知己</h1>
        <p>{initialized ? '欢迎回来，登录继续记录' : '首次使用，设置一个密码'}</p>
        <input type="text" value={serverBase} placeholder="服务器地址（留空=当前站点）"
          onChange={(e) => setServerBase(e.target.value)} style={{ fontSize: 13 }} />
        <input type="password" value={password} placeholder="密码"
          onChange={(e) => setPassword(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && submit()} />
        {!initialized && (
          <input type="password" value={password2} placeholder="再输一遍密码"
            onChange={(e) => setPassword2(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && submit()} />
        )}
        <div className="login-err">{err || (initialized === false && password && password2 && password !== password2 ? '两次密码不一致' : '')}</div>
        <button className="btn primary" onClick={submit} disabled={busy || (!initialized && password !== password2)}>
          {busy ? '请稍候…' : initialized ? '登录' : '开始记录'}
        </button>
      </div>
    </div>
  );
}
