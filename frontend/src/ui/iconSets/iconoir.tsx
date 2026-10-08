// Generated mapping (етап 18): the site's icon names (as in lucide-react) → this set's icons.
// Loaded only when someone picks the set; see ui/icons.tsx.

import type { ComponentType, ReactElement } from 'react';
import { Archive, ArrowLeft, Bell, BellNotification, BookmarkBook, ChatBubble, Check, ClockRotateRight, DataTransferBoth, EditPencil, Group, Home, InfoCircle, Language, Link, List, Medal, MediaImagePlus, Minus, NavArrowDown, NavArrowLeft, NavArrowRight, NavArrowUp, OpenBook, Plus, Redo, Refresh, Search, Send, Settings, Sort, Sparks, Text, Trash, TriangleFlag, Undo, Upload, User, Xmark } from 'iconoir-react';
import type { SetIconProps } from '../icons';

function draw(Icon: ComponentType<Record<string, unknown>>, { size, stroke }: SetIconProps) {
    return <Icon width={size} height={size} strokeWidth={stroke} aria-hidden />;
}

const ICONS: Record<string, ComponentType<Record<string, unknown>>> = {
    ArrowDownUp: Sort,
    ArrowLeft: ArrowLeft,
    ArrowRightLeft: DataTransferBoth,
    Award: Medal,
    Bell: Bell,
    BellRing: BellNotification,
    BookOpen: OpenBook,
    BookmarkPlus: BookmarkBook,
    Check: Check,
    ChevronDown: NavArrowDown,
    ChevronLeft: NavArrowLeft,
    ChevronRight: NavArrowRight,
    ChevronUp: NavArrowUp,
    FileUp: Upload,
    Flag: TriangleFlag,
    History: ClockRotateRight,
    Home: Home,
    ImagePlus: MediaImagePlus,
    Inbox: Archive,
    Info: InfoCircle,
    Languages: Language,
    Link2: Link,
    List: List,
    MessageCircle: ChatBubble,
    Minus: Minus,
    PenLine: EditPencil,
    Plus: Plus,
    Redo2: Redo,
    RefreshCw: Refresh,
    Search: Search,
    Send: Send,
    SlidersHorizontal: Settings,
    Sparkles: Sparks,
    Trash2: Trash,
    Type: Text,
    Undo2: Undo,
    User: User,
    Users: Group,
    X: Xmark,
};

export default function render(name: string, props: SetIconProps): ReactElement | null {
    const Found = ICONS[name];
    return Found ? draw(Found, props) : null;
}
