export type EntryType = 'mood' | 'text' | 'photo' | 'habit_checkin' | 'health' | 'dream';

export interface Entry {
  id: string;
  clientId: string;
  type: EntryType;
  content: any;
  tags: string[];
  entryDate: string;
  createdAt: string;
  updatedAt: string;
  deleted?: boolean;
}

export interface Habit {
  id: string;
  clientId: string;
  name: string;
  emoji: string;
  color: string;
  schedule: { type: string; days?: number[] };
  archived: boolean;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface ImageInfo {
  id: string;
  url: string;
  size?: number;
}

export interface StatsSummary {
  range: { from: string; to: string };
  counts: {
    byType: Record<string, number>;
    totalEntries: number;
    totalDays: number;
    avgDaysPerWeek: number;
  };
  mood: {
    avgByDay: { date: string; avg: number; count: number }[];
    overallAvg: number | null;
    firstHalfAvg: number | null;
    secondHalfAvg: number | null;
    distribution: { score: number; count: number }[];
    emotionTop: { name: string; count: number }[];
  };
  tagsTop: { name: string; count: number }[];
  habits: {
    habitId: string;
    name: string;
    emoji: string;
    dates: string[];
    count: number;
    streak: number;
    rate: number;
  }[];
  health: {
    sleep: { date: string; v: number }[];
    weight: { date: string; v: number }[];
    steps: { date: string; v: number }[];
  };
  timeline: { date: string; type: string; label: string; body: string; tags: string[] }[];
}
