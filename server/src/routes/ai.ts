import type { FastifyInstance } from 'fastify';

/**
 * AI 接口预留（OpenAI 兼容）。
 * 配置环境变量后即启用：AI_BASE_URL / AI_API_KEY / AI_MODEL
 * 例：AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4  AI_MODEL=glm-4-flash
 */
export const aiConfigured = () => !!(process.env.AI_BASE_URL && process.env.AI_API_KEY && process.env.AI_MODEL);

export function registerAI(app: FastifyInstance) {
  app.get('/api/ai/status', async () => ({
    configured: aiConfigured(),
    model: process.env.AI_MODEL || null,
    note: aiConfigured() ? 'AI 已配置' : '设置 AI_BASE_URL / AI_API_KEY / AI_MODEL 后启用（OpenAI 兼容格式）',
  }));

  app.post('/api/ai/chat', async (req, reply) => {
    if (!aiConfigured()) return reply.code(501).send({ error: 'AI 未配置：请设置 AI_BASE_URL / AI_API_KEY / AI_MODEL' });
    const { messages } = (req.body || {}) as any;
    if (!Array.isArray(messages) || !messages.length) return reply.code(400).send({ error: '缺少 messages' });
    try {
      const resp = await fetch(`${process.env.AI_BASE_URL!.replace(/\/$/, '')}/chat/completions`, {
        method: 'POST',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${process.env.AI_API_KEY}` },
        body: JSON.stringify({ model: process.env.AI_MODEL, messages }),
      });
      if (!resp.ok) {
        const text = await resp.text();
        return reply.code(502).send({ error: `上游返回 ${resp.status}`, detail: text.slice(0, 2000) });
      }
      const data: any = await resp.json();
      return { content: data.choices?.[0]?.message?.content ?? '', raw: data };
    } catch (e: any) {
      return reply.code(502).send({ error: `请求失败：${e.message}` });
    }
  });

  app.post('/api/ai/analyze', async (_req, reply) => {
    return reply.code(501).send({ error: '自动摘要/打标签将在二期实现；当前可用 /api/export/profile 生成档案' });
  });
}
