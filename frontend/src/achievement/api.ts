import { api } from '../api/client';

/** A badge on a profile (рішення 33); {@code earnedAt} is null while it is still ahead. */
export type BadgeView = { code: string; title: string; description: string; earnedAt: string | null; progress: number; goal: number };

export type AchievementProfile = { level: number; points: number; levelPoints: number; nextLevelPoints: number; badges: BadgeView[] };

export const achievementApi = {
    profile: (nick: string) => api<AchievementProfile>(`/api/users/${encodeURIComponent(nick)}/achievements`),
};
