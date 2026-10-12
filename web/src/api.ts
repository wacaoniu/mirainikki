export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

const getBase = () => localStorage.getItem('sd_base') || '';
const getToken = () => localStorage.getItem('sd_token') || '';

export const setAuth = (base: string, token: string) => {
  localStorage.setItem('sd_base', base.replace(/\/$/, ''));
  localStorage.setItem('sd_token', token);
};

export const clearAuth = () => {
  localStorage.removeItem('sd_token');
};

async function req<T = any>(method: string, path: string, body?: any, isForm = false): Promise<T> {
  const headers: Record<string, string> = {};
  const token = getToken();
  if (token) headers.authorization = `Bearer ${token}`;
  if (body && !isForm) headers['content-type'] = 'application/json';
  const resp = await fetch(getBase() + path, {
    method,
    headers,
    body: body ? (isForm ? (body as FormData) : JSON.stringify(body)) : undefined,
  });
  const text = await resp.text();
  let data: any;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!resp.ok) {
    if (resp.status === 401) clearAuth();
    throw new ApiError(resp.status, data?.error || data?.message || `请求失败 ${resp.status}`);
  }
  return data as T;
}

export const api = {
  get: <T = any>(path: string) => req<T>('GET', path),
  post: <T = any>(path: string, body?: any) => req<T>('POST', path, body),
  put: <T = any>(path: string, body?: any) => req<T>('PUT', path, body),
  del: <T = any>(path: string) => req<T>('DELETE', path),
  upload: (file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return req<{ id: string; url: string; size: number }>('POST', '/api/images', fd, true);
  },
};

export const authStatus = () => api.get<{ initialized: boolean }>('/api/auth/status');
export const authSetup = (password: string) => api.post<{ token: string }>('/api/auth/setup', { password });
export const authLogin = (password: string) => api.post<{ token: string }>('/api/auth/login', { password });

export interface EntryQuery {
  from?: string; to?: string; type?: string; tag?: string; q?: string; limit?: number; offset?: number;
}
export const fetchEntries = (query: EntryQuery) => {
  const p = new URLSearchParams();
  Object.entries(query).forEach(([k, v]) => { if (v != null && v !== '') p.set(k, String(v)); });
  return api.get<{ total: number; items: any[] }>(`/api/entries?${p}`);
};
export const fetchHabits = () => api.get<{ items: any[] }>('/api/habits');
export const fetchStats = (days: number) => api.get<any>(`/api/stats/summary?days=${days}`);
export const fetchProfile = (days: number) => api.get<string>(`/api/export/profile?days=${days}`);
