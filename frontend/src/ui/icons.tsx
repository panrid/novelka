import * as lucide from 'lucide-react';
import type { CSSProperties } from 'react';

/**
 * The site's icons: Lucide, in the line width the site has drawn them with since етап 18. The names are
 * lucide-react's, so pages import from here as they did from it. The new design's own icons come with
 * the каркас (#293).
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

/** The line width the icons have had by default: 1,75 for a shape drawn at 2. */
const WEIGHT = 1.75;

function icon(name: keyof typeof lucide & string): LucideIcon {
    const Lucide = lucide[name] as unknown as React.ComponentType<Record<string, unknown>>;
    function Icon({ size = 24, strokeWidth, className, style, fill, ...rest }: IconProps) {
        // The caller's width is the shape it was drawn for.
        const stroke = (strokeWidth ?? 2) * (WEIGHT / 2);
        return (
            <span data-icon className={className} style={{ display: 'inline-flex', lineHeight: 0, ...style }} aria-hidden={rest['aria-hidden'] ?? true}>
                <Lucide size={size} strokeWidth={stroke} fill={fill ?? 'none'} aria-hidden />
            </span>
        );
    }
    Icon.displayName = `Icon(${name})`;
    return Icon;
}

export const Archive = icon('Archive');
export const ArchiveRestore = icon('ArchiveRestore');
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
export const Eye = icon('Eye');
export const EyeOff = icon('EyeOff');
export const FileUp = icon('FileUp');
export const Flag = icon('Flag');
export const History = icon('History');
export const Home = icon('Home');
export const ImagePlus = icon('ImagePlus');
export const Images = icon('Images');
export const Inbox = icon('Inbox');
export const Info = icon('Info');
export const Languages = icon('Languages');
export const Lightbulb = icon('Lightbulb');
export const Link2 = icon('Link2');
export const List = icon('List');
export const MessageCircle = icon('MessageCircle');
export const Minus = icon('Minus');
export const Moon = icon('Moon');
export const PenLine = icon('PenLine');
export const Plus = icon('Plus');
export const Redo2 = icon('Redo2');
export const RefreshCw = icon('RefreshCw');
export const Search = icon('Search');
export const Send = icon('Send');
export const SlidersHorizontal = icon('SlidersHorizontal');
export const Sparkles = icon('Sparkles');
export const Sun = icon('Sun');
export const Trash2 = icon('Trash2');
export const Type = icon('Type');
export const Undo2 = icon('Undo2');
export const User = icon('User');
export const Users = icon('Users');
export const X = icon('X');
