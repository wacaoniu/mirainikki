import { z } from 'zod';

export const moodContent = z.object({
  score: z.number().int().min(1).max(5),
  emotions: z.array(z.string().min(1).max(20)).max(20).default([]),
  note: z.string().max(2000).optional(),
});

export const textContent = z
  .object({
    title: z.string().max(200).optional(),
    body: z.string().max(50000).optional(),
  })
  .refine((c) => (c.title?.trim() || c.body?.trim()), { message: '标题和正文至少填一项' });

export const photoContent = z.object({
  caption: z.string().max(2000).optional(),
  imageIds: z.array(z.string().min(1)).min(1).max(9),
});

export const habitCheckinContent = z.object({
  habitId: z.string().min(1),
  habitName: z.string().max(100).optional(),
});

export const healthContent = z
  .object({
    sleepHours: z.number().min(0).max(24).optional(),
    weightKg: z.number().min(20).max(500).optional(),
    steps: z.number().int().min(0).max(300000).optional(),
  })
  .refine((c) => c.sleepHours != null || c.weightKg != null || c.steps != null, {
    message: '至少填写一项健康数据',
  });

export const dreamContent = z
  .object({
    body: z.string().max(50000).optional(),
    emotions: z.array(z.string().min(1).max(20)).max(20).default([]),
  })
  .refine((c) => c.body?.trim() || c.emotions?.length, { message: '梦境描述和醒来情绪至少填一项' });

const contentSchemas: Record<string, z.ZodTypeAny> = {
  mood: moodContent,
  text: textContent,
  photo: photoContent,
  habit_checkin: habitCheckinContent,
  health: healthContent,
  dream: dreamContent,
};

export const ENTRY_TYPES = Object.keys(contentSchemas);

export function validateContent(type: string, content: unknown): { ok: true; data: any } | { ok: false; error: string } {
  const schema = contentSchemas[type];
  if (!schema) return { ok: false, error: `未知类型 ${type}` };
  const r = schema.safeParse(content);
  if (!r.success) return { ok: false, error: r.error.issues.map((i) => `${i.path.join('.') || 'content'}: ${i.message}`).join('; ') };
  return { ok: true, data: r.data };
}

export const tagsSchema = z.array(z.string().min(1).max(50)).max(20).default([]);
