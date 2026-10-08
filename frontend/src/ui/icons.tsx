import * as lucide from 'lucide-react';
import { useEffect, useState, type CSSProperties } from 'react';
import { siteLook, type SiteLook } from '../appearance/model';
import { useAppearance } from '../appearance/store';

/**
 * The site's icons in the set the person chose (етап 18): Lucide (always here), or Tabler,
 * Phosphor, Iconoir, Heroicons — all open, loaded only when picked. The names are lucide-react's,
 * so pages import from here as they did from it. The line width follows the chosen one; Phosphor
 * and Heroicons can also be filled.
 */

export type IconProps = {
    size?: number | string;
    strokeWidth?: number;
    className?: string;
    style?: CSSProperties;
    /** «currentColor» marks an icon drawn filled on purpose (a bell that is on). */
    fill?: string;
    'aria-hidden'?: boolean | 'true' | 'false';
};
export type LucideIcon = (props: IconProps) => React.ReactElement;
export type SetIconProps = { size: number; stroke: number; fill: boolean };
type SetRender = (name: string, props: SetIconProps) => React.ReactElement | null;

type SetName = Exclude<SiteLook['icons'], 'lucide'>;
const LOADERS: Record<SetName, () => Promise<{ default: SetRender }>> = {
    tabler: () => import('./iconSets/tabler'),
    phosphor: () => import('./iconSets/phosphor'),
    iconoir: () => import('./iconSets/iconoir'),
    heroicons: () => import('./iconSets/heroicons'),
};
const loaded: Partial<Record<SetName, SetRender>> = {};
const loading: Partial<Record<SetName, Promise<unknown>>> = {};

function useSet(set: SiteLook['icons']) {
    const [, rerender] = useState(0);
    useEffect(() => {
        if (set === 'lucide' || loaded[set]) return;
        loading[set] ??= LOADERS[set]().then((module) => { loaded[set] = module.default; });
        let gone = false;
        void loading[set].then(() => { if (!gone) rerender((n) => n + 1); }, () => {});
        return () => { gone = true; };
    }, [set]);
    return set === 'lucide' ? null : loaded[set] ?? null;
}

function icon(name: keyof typeof lucide & string): LucideIcon {
    const Lucide = lucide[name] as unknown as React.ComponentType<Record<string, unknown>>;
    function Icon({ size = 24, strokeWidth, className, style, fill, ...rest }: IconProps) {
        const look = siteLook(useAppearance());
        const set = useSet(look.icons);
        // The caller's width is the shape it was drawn for; the person's choice scales it.
        const stroke = (strokeWidth ?? 2) * (look.iconWeight / 2);
        const pixels = typeof size === 'number' ? size : parseFloat(size) || 24;
        const drawn = set?.(name, { size: pixels, stroke, fill: look.iconFill || Boolean(fill) }) ?? null;
        return (
            <span data-icon className={className} style={{ display: 'inline-flex', lineHeight: 0, ...style }} aria-hidden={rest['aria-hidden'] ?? true}>
                {drawn ?? <Lucide size={size} strokeWidth={stroke} fill={fill ?? 'none'} aria-hidden />}
            </span>
        );
    }
    Icon.displayName = `Icon(${name})`;
    return Icon;
}

export const ArrowDownUp = icon('ArrowDownUp');
export const ArrowLeft = icon('ArrowLeft');
export const ArrowRightLeft = icon('ArrowRightLeft');
export const Award = icon('Award');
export const Bell = icon('Bell');
export const BellRing = icon('BellRing');
export const BookOpen = icon('BookOpen');
export const BookmarkPlus = icon('BookmarkPlus');
export const Check = icon('Check');
export const ChevronDown = icon('ChevronDown');
export const ChevronLeft = icon('ChevronLeft');
export const ChevronRight = icon('ChevronRight');
export const ChevronUp = icon('ChevronUp');
export const Ellipsis = icon('Ellipsis');
export const FileUp = icon('FileUp');
export const Flag = icon('Flag');
export const History = icon('History');
export const Home = icon('Home');
export const ImagePlus = icon('ImagePlus');
export const Inbox = icon('Inbox');
export const Info = icon('Info');
export const Languages = icon('Languages');
export const Link2 = icon('Link2');
export const List = icon('List');
export const MessageCircle = icon('MessageCircle');
export const Minus = icon('Minus');
export const PenLine = icon('PenLine');
export const Plus = icon('Plus');
export const Redo2 = icon('Redo2');
export const RefreshCw = icon('RefreshCw');
export const Search = icon('Search');
export const Send = icon('Send');
export const SlidersHorizontal = icon('SlidersHorizontal');
export const Sparkles = icon('Sparkles');
export const Trash2 = icon('Trash2');
export const Type = icon('Type');
export const Undo2 = icon('Undo2');
export const User = icon('User');
export const Users = icon('Users');
export const X = icon('X');
