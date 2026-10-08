import { useQuery } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { Plus } from '../../ui/icons';
import { useState } from 'react';
import { useMe } from '../../auth/me';
import { useCanRun, useMyShahs } from '../../ledger/api';
import { Cover } from '../../reading/Cover';
import { chaptersWord } from '../../reading/api';
import { ROLE_LABELS, studioApi, type MyEdition } from '../../studio/api';
import { autotranslateApi } from '../../studio/autotranslate';
import { LinkButton } from '../../ui/LinkButton';
import { Notice } from '../../ui/Notice';
import { TextInput } from '../../ui/TextInput';
import styles from './studio.module.css';

/** Search shows once the list is long enough to need it. */
const SEARCH_FROM = 6;

export function StudioHome() {
    const me = useMe();
    const shahs = useMyShahs();
    const canRun = useCanRun();
    const hasShahs = Boolean(shahs.data && (shahs.data.available > 0 || shahs.data.reserved > 0 || shahs.data.history.length > 0));
    const runs = me?.role === 'owner' || hasShahs;
    const mine = useQuery({ queryKey: ['studio'], queryFn: studioApi.mine });
    const active = useQuery({
        queryKey: ['processes', 'active-count'],
        queryFn: () => autotranslateApi.processes({ state: 'active', page: 1 }),
        enabled: runs,
    });
    const [search, setSearch] = useState('');
    const needle = search.trim().toLocaleLowerCase('uk');
    const shown = mine.data?.filter((item) => item.title.toLocaleLowerCase('uk').includes(needle));
    return (
        <section className={styles.page}>
            <h1 className={styles.title}>Студія</h1>
            <p className={styles.muted}>Ваші переклади й твори: власні та ті, де ви в команді.</p>
            <div className={styles.actions}>
                <LinkButton to="/studio/new"><Plus size={18} aria-hidden />Нова публікація</LinkButton>
                <LinkButton to="/studio/teams" variant="secondary">Мої команди</LinkButton>
                {runs && (
                    <LinkButton to="/studio/processes" variant="secondary">
                        Процеси{active.data && active.data.total > 0 ? ` · ${active.data.total} іде` : ''}
                    </LinkButton>
                )}
            </div>
            {mine.isError && <Notice tone="error">{mine.error.message}</Notice>}
            {mine.isSuccess && mine.data.length === 0 && (
                <p className={styles.muted}>Поки порожньо. Почніть із «Нова публікація»: свій переклад або свій твір.</p>
            )}
            {(mine.data?.length ?? 0) >= SEARCH_FROM && <TextInput label="Пошук за назвою" value={search} onChange={setSearch} />}
            {shown?.length === 0 && <p className={styles.muted}>Нічого не знайдено.</p>}
            {shown?.map((item) => <EditionCard key={item.editionId} item={item} canRun={canRun} />)}
        </section>
    );
}

function EditionCard({ item, canRun }: { item: MyEdition; canRun: boolean }) {
    const machine = canRun && (item.kind === 'machine' || item.kind === 'mixed');
    const working = item.jobState === 'running' || item.jobState === 'queued';
    return (
        <Link to="/studio/$editionId" params={{ editionId: String(item.editionId) }} className={styles.row} style={{ alignItems: 'flex-start' }}>
            <Cover url={item.coverUrl} title={item.title} seed={item.novelSlug} width={44} />
            <div className={styles.grow}>
                <div className={styles.ellipsis} style={{ fontWeight: 500 }}>{item.title}</div>
                <div className={styles.muted}>
                    {item.chapterCount} {chaptersWord(item.chapterCount)} · ${item.teamHandle} · {ROLE_LABELS[item.role]}
                </div>
                <div className={styles.cardBadges}>
                    {working && (
                        <span className={`${styles.badge} ${styles.badgeRun}`}>
                            автопереклад{item.jobChapter !== null ? ` · гл. ${item.jobChapter}` : ''}
                        </span>
                    )}
                    {item.jobState === 'failed' && <span className={`${styles.badge} ${styles.badgeOn}`}>автопереклад зупинився</span>}
                    {item.pendingSuggestions > 0 && <span className={`${styles.badge} ${styles.badgeOn}`}>правок: {item.pendingSuggestions}</span>}
                    {machine && item.newWords > 0 && <span className={styles.badge}>нових слів: {item.newWords}</span>}
                    {item.drafts > 0 && <span className={styles.badge}>чернеток: {item.drafts}</span>}
                </div>
            </div>
        </Link>
    );
}
