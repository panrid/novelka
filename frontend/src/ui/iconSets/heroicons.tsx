// Generated mapping (етап 18): the site's icon names (as in lucide-react) → this set's icons.
// Loaded only when someone picks the set; see ui/icons.tsx.

import type { ComponentType, ReactElement } from 'react';
import * as outline from '@heroicons/react/24/outline';
import * as solid from '@heroicons/react/24/solid';
import type { SetIconProps } from '../icons';

const NAMES: Record<string, string> = {
    ArrowDownUp: 'ArrowsUpDownIcon',
    ArrowLeft: 'ArrowLeftIcon',
    ArrowRightLeft: 'ArrowsRightLeftIcon',
    Award: 'TrophyIcon',
    Bell: 'BellIcon',
    BellRing: 'BellAlertIcon',
    BookOpen: 'BookOpenIcon',
    BookmarkPlus: 'BookmarkIcon',
    Check: 'CheckIcon',
    ChevronDown: 'ChevronDownIcon',
    ChevronLeft: 'ChevronLeftIcon',
    ChevronRight: 'ChevronRightIcon',
    ChevronUp: 'ChevronUpIcon',
    Ellipsis: 'EllipsisHorizontalIcon',
    FileUp: 'DocumentArrowUpIcon',
    Flag: 'FlagIcon',
    History: 'ClockIcon',
    Home: 'HomeIcon',
    ImagePlus: 'PhotoIcon',
    Inbox: 'InboxIcon',
    Info: 'InformationCircleIcon',
    Languages: 'LanguageIcon',
    Link2: 'LinkIcon',
    List: 'ListBulletIcon',
    MessageCircle: 'ChatBubbleOvalLeftIcon',
    Minus: 'MinusIcon',
    PenLine: 'PencilIcon',
    Plus: 'PlusIcon',
    Redo2: 'ArrowUturnRightIcon',
    RefreshCw: 'ArrowPathIcon',
    Search: 'MagnifyingGlassIcon',
    Send: 'PaperAirplaneIcon',
    SlidersHorizontal: 'AdjustmentsHorizontalIcon',
    Sparkles: 'SparklesIcon',
    Trash2: 'TrashIcon',
    Type: 'LanguageIcon',
    Undo2: 'ArrowUturnLeftIcon',
    User: 'UserIcon',
    Users: 'UsersIcon',
    X: 'XMarkIcon',
};

export default function render(name: string, { size, stroke, fill }: SetIconProps): ReactElement | null {
    const key = NAMES[name];
    const Outline = key ? (outline as Record<string, ComponentType<Record<string, unknown>>>)[key] : undefined;
    const Solid = key ? (solid as Record<string, ComponentType<Record<string, unknown>>>)[key] : undefined;
    if (!Outline) return null;
    return fill && Solid
        ? <Solid width={size} height={size} aria-hidden />
        : <Outline width={size} height={size} strokeWidth={stroke} aria-hidden />;
}
