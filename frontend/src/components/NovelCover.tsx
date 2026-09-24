import type { ReactNode } from 'react';

/** Palettes: background, mid tone, light accent, seal colour. All keep cream text readable. */
const palettes = [
    ['#2f4a40', '#446a57', '#e5dfc9', '#c9774d'],
    ['#7d4f38', '#9c6a4b', '#f0dcc0', '#2f4a40'],
    ['#48506b', '#646d8e', '#e2e0ef', '#c9774d'],
    ['#666447', '#84825e', '#ece6cc', '#9b3f33'],
    ['#2d3f52', '#41596f', '#dbe5ea', '#d08a4f'],
    ['#5a3f52', '#7a5a70', '#efdfe6', '#c9a34d'],
];

/** Deterministic 32-bit hash and PRNG, so a novel keeps the same cover on every visit. */
function hash(text: string) {
    let value = 2166136261;
    for (let index = 0; index < text.length; index++) value = Math.imul(value ^ text.charCodeAt(index), 16777619);
    return value >>> 0;
}
function random(seed: number) {
    return () => {
        seed = (seed + 0x6D2B79F5) | 0;
        let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
        t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

type Motif = (next: () => number, mid: string, light: string) => ReactNode;
const motifs: Motif[] = [
    // Layered mountains under a low sun.
    (next, mid, light) => <>
        <circle cx={200 + next() * 60} cy={62} r={30} fill={light} opacity=".85" />
        <path d={`M0 150V${104 + next() * 12}L${60 + next() * 30} 58L140 112L${190 + next() * 30} 70L300 118V150Z`} fill={mid} />
        <path d={`M0 150V128L${90 + next() * 40} 96L190 132L${240 + next() * 30} 110L300 136V150Z`} fill="#00000033" />
    </>,
    // Rows of waves.
    (next, mid, light) => <>{Array.from({ length: 5 }, (_, row) => {
        const y = 60 + row * 20, shift = next() * 40;
        return <path key={row} d={Array.from({ length: 8 }, (_, i) => `${i ? '' : `M${-40 + shift} ${y}`}q20 -${14 - row} 40 0`).join('')}
            fill="none" stroke={row % 2 ? light : mid} strokeWidth="3" opacity={0.35 + row * 0.12} />;
    })}</>,
    // Full moon crossed by thin cloud bands, as in woodblock prints.
    (next, mid, light) => {
        const cx = 90 + next() * 120;
        return <>
            <circle cx={cx} cy={72} r={46} fill={light} opacity=".92" />
            {Array.from({ length: 4 }, (_, i) => {
                const y = 50 + i * 16 + next() * 6, width = 90 + next() * 90, x = cx - width / 2 + (next() - 0.5) * 80;
                return <rect key={i} x={x} y={y} width={width} height={i % 2 ? 5 : 8} rx={4} fill={i % 2 ? mid : 'currentColor'} opacity={i % 2 ? 0.9 : 1} />;
            })}
        </>;
    },
    // Bamboo grove.
    (next, mid, light) => <>{Array.from({ length: 7 }, (_, i) => {
        const x = 20 + i * 42 + next() * 14, width = 7 + next() * 5;
        return <g key={i} fill={i % 2 ? mid : light} opacity={i % 2 ? 0.9 : 0.35}>
            <rect x={x} y={0} width={width} height={150} rx={2} />
            {Array.from({ length: 4 }, (_, node) => <rect key={node} x={x - 1.5} y={20 + node * 34 + next() * 8} width={width + 3} height={2.5} fill="#00000040" />)}
            <path d={`M${x + width} ${30 + next() * 60}q22 -6 34 -20q-18 2 -34 12Z`} />
        </g>;
    })}</>,
    // Night street with lanterns.
    (next, mid, light) => <>
        {Array.from({ length: 6 }, (_, i) => {
            const height = 50 + next() * 60, x = i * 52;
            return <rect key={'b' + i} x={x} y={150 - height} width={48} height={height} fill={mid} />;
        })}
        <path d="M0 30Q150 58 300 26" stroke={light} strokeWidth="1.2" fill="none" opacity=".7" />
        {Array.from({ length: 5 }, (_, i) => {
            const x = 30 + i * 60, y = 30 + Math.sin(i / 1.3) * 10 + 14;
            return <g key={'l' + i}><rect x={x - 7} y={y} width={14} height={18} rx={6} fill="#e59a52" /><rect x={x - 5} y={y + 3} width={10} height={2} fill="#00000030" /></g>;
        })}
    </>,
    // Constellation.
    (next, _mid, light) => {
        const points = Array.from({ length: 7 }, () => [30 + next() * 240, 20 + next() * 110]);
        return <>
            <polyline points={points.map(point => point.join(',')).join(' ')} fill="none" stroke={light} strokeWidth="1" opacity=".5" />
            {points.map(([x, y], i) => <circle key={i} cx={x} cy={y} r={i % 3 ? 2.2 : 3.6} fill={light} />)}
            {Array.from({ length: 24 }, (_, i) => <circle key={'s' + i} cx={next() * 300} cy={next() * 150} r={0.8} fill={light} opacity=".6" />)}
        </>;
    },
];

/** Generated cover: motif and palette depend only on the novel id; the hanko seal carries the title's first letter. */
export function NovelCover({ id, title, className = '' }: { id: string; title: string; className?: string }) {
    const seed = hash(id);
    const [background, mid, light, seal] = palettes[seed % palettes.length];
    const motif = motifs[(seed >>> 8) % motifs.length];
    return <div className={'book-cover ' + className} aria-hidden="true">
        <svg className="cover-art" viewBox="0 0 300 150" preserveAspectRatio="xMidYMid slice" style={{ color: background }}>
            <rect width="300" height="150" fill={background} />
            {motif(random(seed), mid, light)}
        </svg>
        <span className="cover-id">{id}</span>
        <span className="cover-seal" style={{ background: seal }}>{title.slice(0, 1)}</span>
        <span className="cover-imprint">NOVELKA / STORIES</span>
    </div>;
}
