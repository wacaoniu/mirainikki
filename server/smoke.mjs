/**
 * 冒烟测试：node smoke.mjs [baseUrl]
 * 走完整闭环：初始化 → 登录 → 习惯 → 五类记录 → 同步 push/pull → 搜索 → 统计 → 档案导出 → 全量导出
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8787';
let token = '';
let pass = 0, fail = 0;

const api = async (method, path, body, isForm = false) => {
  const headers = { authorization: `Bearer ${token}` };
  if (body && !isForm) headers['content-type'] = 'application/json';
  const resp = await fetch(BASE + path, {
    method,
    headers,
    body: body ? (isForm ? body : JSON.stringify(body)) : undefined,
  });
  const text = await resp.text();
  let data;
  try { data = JSON.parse(text); } catch { data = text; }
  return { status: resp.status, data };
};

const check = (name, cond, extra = '') => {
  if (cond) { pass++; console.log(`  ✔ ${name}`); }
  else { fail++; console.log(`  ✘ ${name} ${extra}`); }
};

const today = new Date().toLocaleDateString('sv-SE'); // YYYY-MM-DD（本地时区）

console.log(`目标：${BASE}\n`);

// 1. 认证
console.log('[认证]');
let r = await api('GET', '/api/auth/status');
if (r.data.initialized) {
  r = await api('POST', '/api/auth/login', { password: 'smoke-test' });
  if (r.status === 401) {
    console.log('  服务端已有数据且密码不是 smoke-test，跳过冒烟测试以免污染数据');
    process.exit(1);
  }
  token = r.data.token;
  check('登录已有账号', !!token);
} else {
  r = await api('POST', '/api/auth/setup', { password: 'smoke-test' });
  token = r.data.token;
  check('初始化账号', r.status === 200 && !!token);
}
{
  const resp = await fetch(BASE + '/api/entries');
  check('无 Token 被拒（401）', resp.status === 401, `实际 ${resp.status}`);
}

// 清掉历史冒烟数据（此库专用于冒烟测试），保证下面的精确计数
{
  const list = await api('GET', '/api/entries?limit=500');
  for (const e of list.data?.items || []) await api('DELETE', '/api/entries/' + e.id);
  const hs = await api('GET', '/api/habits?all=1');
  for (const h of hs.data?.items || []) await api('DELETE', '/api/habits/' + h.id);
}

// 2. 习惯
console.log('[习惯]');
r = await api('POST', '/api/habits', { name: '阅读', emoji: '📚', color: '#FF9800' });
const habitId = r.data?.id;
check('创建习惯', !!habitId);
r = await api('POST', '/api/habits', { name: '跑步', emoji: '🏃' });
const habit2 = r.data?.id;
check('创建第二个习惯', !!habit2);

// 3. 五类记录
console.log('[五类记录]');
r = await api('POST', '/api/entries', { type: 'mood', content: { score: 4, emotions: ['开心', '平静'] }, tags: ['周末'] });
check('心情打卡', r.status === 200 && r.data?.type === 'mood', JSON.stringify(r.data));
r = await api('POST', '/api/entries', { type: 'text', content: { title: '项目启动', body: '今天开始做未来日记 App，把生活方方面面都记录下来，以后让 AI 更了解我。' }, tags: ['开发', '项目'] });
check('文字日记', r.status === 200 && r.data?.content?.title === '项目启动');
r = await api('POST', '/api/entries', { type: 'photo', content: { caption: '今天的晚霞', imageIds: [] }, tags: [] });
check('空图照片被拒（400，至少 1 张图）', r.status === 400);
// 上传一张真实图片再建照片记录
const png = Buffer.from('89504e470d0a1a0a0000000d494844520000000100000001080600000' +
  '01f15c4890000000d49444154789c626001000000050001a5f645400000000049454e44ae426082', 'hex');
const form = new FormData();
form.append('file', new Blob([png], { type: 'image/png' }), 'test.png');
r = await api('POST', '/api/images', form, true);
const imgId = r.data?.id;
check('图片上传', r.status === 200 && !!imgId && r.data?.url?.startsWith('/uploads/'), JSON.stringify(r.data));
const imgResp = await fetch(BASE + (r.data?.url || ''));
check('图片可访问', imgResp.ok);
r = await api('POST', '/api/entries', { type: 'habit_checkin', content: { habitId, habitName: '阅读' } });
check('习惯打卡', r.status === 200);
r = await api('POST', '/api/entries', { type: 'health', content: { sleepHours: 7.5, steps: 8200 } });
check('健康数据', r.status === 200);
r = await api('POST', '/api/entries', { type: 'health', content: {} });
check('空健康数据被拒（400）', r.status === 400);
r = await api('POST', '/api/entries', { type: 'photo', content: { caption: '今天的晚霞', imageIds: [imgId] }, tags: ['风景'] });
check('照片记录', r.status === 200 && r.data?.content?.imageIds?.[0] === imgId);

// 4. 查询
console.log('[查询]');
r = await api('GET', '/api/entries?limit=50');
check('列表查询', r.data?.total >= 5 && r.data?.items?.length >= 5);
r = await api('GET', `/api/entries?q=${encodeURIComponent('晚霞')}`);
check('中文搜索', r.data?.total === 1, JSON.stringify(r.data));
r = await api('GET', '/api/entries?type=mood');
check('类型筛选', r.data?.total === 1);
r = await api('GET', `/api/entries?tag=${encodeURIComponent('项目')}`);
check('标签筛选', r.data?.total === 1);
r = await api('GET', '/api/entries/dates');
check('日期打点', Array.isArray(r.data) && r.data.includes(today));

// 5. 同步
console.log('[同步]');
const cid = 'smoke-' + Date.now();
const ts1 = new Date().toISOString();
r = await api('POST', '/api/sync/push', {
  entries: [{
    clientId: cid, type: 'mood', content: { score: 5, emotions: ['兴奋'] }, tags: ['同步测试'],
    entryDate: today, createdAt: ts1, updatedAt: ts1,
  }],
  habits: [],
});
check('push 接受', r.data?.accepted === 1, JSON.stringify(r.data));
const firstPull = await api('GET', '/api/sync/pull');
// 相同时间戳重推：LWW 规则 existing >= incoming → 应跳过
r = await api('POST', '/api/sync/push', {
  entries: [{
    clientId: cid, type: 'mood', content: { score: 3, emotions: ['一般'] }, tags: ['同步测试'],
    entryDate: today, createdAt: ts1, updatedAt: ts1,
  }],
  habits: [],
});
check('push 幂等跳过（旧数据）', r.data?.skipped === 1 || r.data?.accepted === 0, JSON.stringify(r.data));
r = await api('GET', `/api/sync/pull?since=${encodeURIComponent(firstPull.data.serverTime)}`);
check('pull 增量', r.data?.serverTime != null);

// 6. 统计
console.log('[统计]');
r = await api('GET', '/api/stats/summary');
check('统计汇总', r.data?.counts?.totalEntries >= 5 && Array.isArray(r.data?.habits), JSON.stringify(r.data?.counts));
check('情绪均值', typeof r.data?.mood?.overallAvg === 'number');
check('习惯打卡统计', r.data?.habits?.some((h) => h.name === '阅读'));

// 7. 导出
console.log('[导出]');
r = await api('GET', '/api/export/profile?days=30');
check('档案导出', typeof r.data === 'string' && r.data.includes('个人记忆档案') && r.data.includes('阅读'));
r = await api('GET', '/api/export/full');
check('全量导出', r.data?.entries?.length >= 5);

// 8. AI 预留
console.log('[AI 预留]');
r = await api('GET', '/api/ai/status');
check('AI 状态接口', r.data?.configured === false);
r = await api('POST', '/api/ai/chat', { messages: [{ role: 'user', content: 'hi' }] });
check('未配置返回 501', r.status === 501);

// 9. 删除
console.log('[清理]');
{
  const list = await api('GET', '/api/entries?limit=200');
  for (const e of list.data.items.filter((x) => x.tags?.includes?.('同步测试'))) {
    await api('DELETE', '/api/entries/' + e.id);
  }
}
r = await api('GET', `/api/entries?q=${encodeURIComponent('晚霞')}`);
const photoEntry = r.data?.items?.[0];
if (photoEntry) { const d = await api('DELETE', '/api/entries/' + photoEntry.id); check('删除记录', d.data?.ok === true); }

console.log(`\n结果：${pass} 通过，${fail} 失败`);
process.exit(fail ? 1 : 0);
