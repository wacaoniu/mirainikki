# 「未来日记」设计文档

个人生活记录 + AI 记忆库。手机快速记录生活的方方面面，电脑端回看与统计，长期沉淀结构化数据，随时生成"个人记忆档案"喂给任何 AI。

## 1. 总体架构

```
Android App (Kotlin+Compose)     电脑网页端 (React+Vite+TS)
  手机记录 · 离线优先+同步          浏览/统计/搜索/导出
          └─────────┬─────────┘
             HTTPS REST + Token
                    ▼
     Node.js 后端 (Fastify + SQLite)
     REST API · 图片存储 · 档案导出 · AI接口预留
     Docker Compose 一键部署
```

- 单用户系统：首次访问设置密码，之后凭密码换 Token。
- 后端零外部依赖（SQLite 文件 + uploads 目录），`docker compose up -d` 即部署。

## 2. 数据模型

### 2.1 核心思想：一切皆 Entry

统一"记录 (Entry)"模型，`type` 区分类型，`content` 为按类型不同的 JSON。新增记录类型无需改表结构。

### 2.2 表结构

```sql
-- 记录表（核心）
CREATE TABLE entries (
  id          TEXT PRIMARY KEY,   -- 服务端 UUID
  client_id   TEXT NOT NULL UNIQUE, -- 客户端生成的 UUID（离线优先、同步幂等键）
  type        TEXT NOT NULL,      -- mood | text | photo | habit_checkin | health
  content     TEXT NOT NULL,      -- JSON，按 type 有不同 schema
  text_index  TEXT NOT NULL DEFAULT '', -- 可搜索文本冗余（标题/正文/备注/情绪词等拼接）
  tags        TEXT NOT NULL DEFAULT '[]', -- JSON 数组
  entry_date  TEXT NOT NULL,      -- 归属日期 'YYYY-MM-DD'（默认当天，可补记）
  created_at  TEXT NOT NULL,      -- ISO8601 UTC
  updated_at  TEXT NOT NULL,      -- ISO8601 UTC，同步冲突以新者胜
  deleted     INTEGER NOT NULL DEFAULT 0  -- 软删除（同步需要）
);

-- 习惯定义
CREATE TABLE habits (
  id         TEXT PRIMARY KEY,
  client_id  TEXT NOT NULL UNIQUE,
  name       TEXT NOT NULL,
  emoji      TEXT NOT NULL DEFAULT '✅',
  color      TEXT NOT NULL DEFAULT '#4CAF50',
  schedule_json TEXT NOT NULL DEFAULT '{"type":"daily"}', -- daily | weekly:{days:[1..7]}
  archived   INTEGER NOT NULL DEFAULT 0,
  sort_order INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted    INTEGER NOT NULL DEFAULT 0
);

-- 图片
CREATE TABLE images (
  id         TEXT PRIMARY KEY,
  entry_id   TEXT NOT NULL,
  filename   TEXT NOT NULL,      -- uploads/ 下的文件名
  width      INTEGER, height     INTEGER,
  size       INTEGER,            -- 字节
  created_at TEXT NOT NULL
);

-- 全文搜索（FTS5，内容跟随 entries.text_index）
CREATE VIRTUAL TABLE entries_fts USING fts5(text_index, content='');

-- 元数据（同步游标等）
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
```

### 2.3 content 各类型 schema

| type | content JSON |
|------|--------------|
| mood | `{"score":1-5, "emotions":["开心","平静",...]}` |
| text | `{"title":"可选标题", "body":"正文"}` |
| photo | `{"caption":"一句话说明", "imageIds":["img-uuid",...]}` |
| habit_checkin | `{"habitId":"habit-uuid", "habitName":"快照名称"}` |
| health | `{"sleepHours":7.5, "weightKg":65.2, "steps":8000}` 字段均可选 |

`text_index` 生成规则：text → title+body；photo → caption；mood → 情绪词；habit_checkin → 习惯名；health → 固定描述。tags 恒并入。

### 2.4 预留 AI 字段

entries 表预留 `ai_tags`、`ai_summary`（二期由 AI 分析回填，本期不建列，content JSON 内亦不放——保持导出干净；二期以独立 `ai` 列或伴生表加入）。

## 3. 同步协议（离线优先）

约定：客户端（Android）本地 Room 是首要写入点，用 `client_id` 幂等；`updated_at` ISO8601 UTC，冲突时**新者胜**（last-write-wins）。

```
上行  POST /api/sync/push   { entries:[...], habits:[...], lastImageIds? }
      → 服务端按 client_id upsert；已有且 updated_at >= 传入者跳过
      → 返回 { accepted:N, skipped:M }

下行  GET  /api/sync/pull?since=<ISO8601>
      → 返回 since 之后 updated_at 变化的 entries/habits（含删除墓碑）
      → 客户端按 updated_at 新者胜合并入 Room

图片  先 POST /api/images (multipart) 得到 imageIds，再随 photo entry 推送
```

网页端直接调 CRUD API（在线使用），不走 push/pull。

## 4. REST API 约定

统一前缀 `/api`，除 auth 外均需 `Authorization: Bearer <token>`。时间均为 ISO8601 UTC。

### 认证
- `GET  /api/auth/status` → `{initialized:boolean}`
- `POST /api/auth/setup`  `{password}` → `{token}`（仅未初始化时）
- `POST /api/auth/login`  `{password}` → `{token}`

### 记录 CRUD
- `GET /api/entries?from=&to=&type=&tag=&q=&limit=&offset=`
  按日期倒序；q 走 FTS；返回 `{total, items:[entry...]}`
- `POST /api/entries` — 创建（服务端生成 client_id 亦可）
- `GET /api/entries/:id`
- `PUT /api/entries/:id` — 全量更新
- `DELETE /api/entries/:id` — 软删除
- `GET /api/entries/dates?from=&to=` → `["2026-10-12",...]` 有记录的日期（日历打点）

### 习惯
- `GET/POST /api/habits`，`PUT/DELETE /api/habits/:id`
- `GET /api/habits/:id/checkins?from=&to=` → 该习惯打卡日期集合

### 图片
- `POST /api/images` multipart file → `{id, url}`
- `GET /uploads/<filename>` 静态服务（带鉴权可选，MVP 直读）
- `DELETE /api/images/:id`

### 统计
- `GET /api/stats/summary?from=&to=` →
  `{ moodAvgByDay:[{date,avg}], moodDistribution:[{score,count}], emotionTop:[{name,count}], habitHeat:[{habitId,date}], habitStreak:{habitId:n}, healthSeries:{sleep:[{date,v}],weight:[...],steps:[...]}, counts:{byType, totalDays, totalEntries} }`

### 导出（记忆档案）
- `GET /api/export/profile?days=90` → Markdown 文本（个人画像 + 统计 + 时间线要点）
- `GET /api/export/full` → 全量 JSON（备份/迁移用）

### AI（预留，未配置返回 501）
- `POST /api/ai/chat` `{messages:[...]}` — OpenAI 兼容透传
- `POST /api/ai/analyze` `{entryIds?}` — 摘要/打标签（二期实现）
- `GET  /api/ai/status` → `{configured:boolean, model?}`

环境变量：`AI_BASE_URL` / `AI_API_KEY` / `AI_MODEL`。

### 同步
- `POST /api/sync/push`、`GET /api/sync/pull?since=`

## 5. 记忆档案（profile）模板

无 AI 也能生成，喂给外部 AI 即用：

```
# 关于我 —— 个人记忆档案
（生成时间、数据范围：近 90 天，共 N 条记录）

## 生活概览
- 记录频率：平均每周 x.x 天有记录
- 最常记录的类型 / 高频标签 / 高频情绪词
- 情绪均值与走势（近 90 天均值 x.x / 5，较前一期 ↑/↓）

## 习惯与健康
- 各习惯坚持率、当前连续天数
- 睡眠均值 / 体重变化 / 日均步数

## 近期时间线要点
- 按周归并：本周情绪均值、完成的习惯打卡、文字记录标题/首句摘录
- 出现过的人物/地点标签（来自 tags）

## 给 AI 的使用说明
（提示词：这是一份结构化生活档案，可用于回答关于本人的问题…）
```

## 6. 三端目录

```
日记/
├── server/    Fastify + better-sqlite3 + TS (tsx 运行)
├── web/       React 18 + Vite + TS + ECharts
├── android/   Kotlin + Compose + Room + Retrofit/OkHttp + WorkManager
└── docs/
```

## 7. 安全与部署

- Token：多设备共存 —— 每次登录签发新的随机 32 字节 Token 追加到 meta 表（最近 20 个同时有效），手机/网页/脚本互不挤掉。
- 部署：`docker compose up -d`；数据卷 `./data`（sqlite）与 `./uploads`（图片）。
- 备份 = 拷贝 data/ + uploads/ 两个目录。
- 公网使用建议挂 HTTPS 反代（Caddy/Nginx），MVP 本地/内网明文即可。

## 8. 路线图

- **MVP（本期）**：上述全部
- **二期 AI**：接入 OpenAI 兼容 API（GLM/DeepSeek/Ollama）；每日自动摘要、自动标签、情绪分析、对话式查日记、周报月报、AI 维护滚动画像
- **三期**：Android Widget 快捷打卡、睡前提醒通知、Health Connect 手环数据、照片 EXIF 地点/地图轨迹、年度回顾页
