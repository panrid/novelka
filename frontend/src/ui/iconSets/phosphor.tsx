// Generated mapping (етап 18): the site's icon names (as in lucide-react) → this set's icons.
// Loaded only when someone picks the set; see ui/icons.tsx.

import type { ComponentType, ReactElement } from 'react';
import { ArrowClockwise, ArrowCounterClockwise, ArrowLeft, ArrowsClockwise, ArrowsDownUp, ArrowsLeftRight, Bell, BellRinging, BookOpen, BookmarkSimple, CaretDown, CaretLeft, CaretRight, CaretUp, ChatCircle, Check, ClockCounterClockwise, FileArrowUp, Flag, House, ImageSquare, Info, Link, List, MagnifyingGlass, Medal, Minus, PaperPlaneRight, PencilSimple, Plus, SlidersHorizontal, Sparkle, TextAa, Translate, Trash, Tray, User, Users, X } from '@phosphor-icons/react';
import type { SetIconProps } from '../icons';

/** Phosphor has weights instead of a line width: the closest one to the chosen width. */
function draw(Icon: ComponentType<Record<string, unknown>>, { size, stroke, fill }: SetIconProps) {
    const weight = fill ? 'fill' : stroke <= 1.25 ? 'thin' : stroke <= 1.6 ? 'light' : stroke <= 2.1 ? 'regular' : 'bold';
    return <Icon size={size} weight={weight} aria-hidden />;
}

const ICONS: Record<string, ComponentType<Record<string, unknown>>> = {
    ArrowDownUp: ArrowsDownUp,
    ArrowLeft: ArrowLeft,
    ArrowRightLeft: ArrowsLeftRight,
    Award: Medal,
    Bell: Bell,
    BellRing: BellRinging,
    BookOpen: BookOpen,
    BookmarkPlus: BookmarkSimple,
    Check: Check,
    ChevronDown: CaretDown,
    ChevronLeft: CaretLeft,
    ChevronRight: CaretRight,
    ChevronUp: CaretUp,
    FileUp: FileArrowUp,
    Flag: Flag,
    History: ClockCounterClockwise,
    Home: House,
    ImagePlus: ImageSquare,
    Inbox: Tray,
    Info: Info,
    Languages: Translate,
    Link2: Link,
    List: List,
    MessageCircle: ChatCircle,
    Minus: Minus,
    PenLine: PencilSimple,
    Plus: Plus,
    Redo2: ArrowClockwise,
    RefreshCw: ArrowsClockwise,
    Search: MagnifyingGlass,
    Send: PaperPlaneRight,
    SlidersHorizontal: SlidersHorizontal,
    Sparkles: Sparkle,
    Trash2: Trash,
    Type: TextAa,
    Undo2: ArrowCounterClockwise,
    User: User,
    Users: Users,
    X: X,
};

export default function render(name: string, props: SetIconProps): ReactElement | null {
    const Found = ICONS[name];
    return Found ? draw(Found, props) : null;
}
