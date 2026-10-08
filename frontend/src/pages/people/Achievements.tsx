import { useQuery } from '@tanstack/react-query';
import { Award } from '../../ui/icons';
import { achievementApi } from '../../achievement/api';
import { relativeTime } from '../../lib/dates';
import styles from './achievements.module.css';

/** The level and badges on a profile: only for joy, they open nothing (рішення 33). */
export function Achievements({ nick }: { nick: string }) {
    const profile = useQuery({ queryKey: ['achievements', nick.toLowerCase()], queryFn: () => achievementApi.profile(nick) });
    const data = profile.data;
    if (!data) return null;
    const span = Math.max(1, data.nextLevelPoints - data.levelPoints);
    const done = Math.min(100, Math.round(((data.points - data.levelPoints) / span) * 100));
    const earned = data.badges.filter((badge) => badge.earnedAt);
    return (
        <div className={styles.section} id="achievements">
            <h2 className={styles.sectionTitle}>Досягнення</h2>
            <div className={styles.level}>
                <span className={styles.levelNumber}>Рівень {data.level}</span>
                <div className={styles.bar} role="progressbar" aria-label={`До рівня ${data.level + 1}`}
                    aria-valuemin={0} aria-valuemax={100} aria-valuenow={done}>
                    <span style={{ width: `${done}%` }} />
                </div>
                <span className={styles.muted}>{data.points} з {data.nextLevelPoints} до рівня {data.level + 1}</span>
            </div>
            <p className={styles.muted}>Отримано {earned.length} з {data.badges.length}.</p>
            <ul className={styles.badges}>
                {data.badges.map((badge) => (
                    <li key={badge.code} className={`${styles.badge} ${badge.earnedAt ? styles.earned : ''}`}>
                        <Award size={22} aria-hidden className={styles.icon} />
                        <div>
                            <div className={styles.badgeTitle}>{badge.title}</div>
                            <div className={styles.muted}>{badge.description}</div>
                            <div className={styles.muted}>
                                {badge.earnedAt
                                    ? `отримано ${relativeTime(new Date(badge.earnedAt))}`
                                    : badge.goal > 1 ? `${badge.progress} з ${badge.goal}` : 'ще попереду'}
                            </div>
                        </div>
                    </li>
                ))}
            </ul>
        </div>
    );
}
