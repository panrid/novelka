// Generated mapping (етап 18): the site's icon names (as in lucide-react) → this set's icons.
// Loaded only when someone picks the set; see ui/icons.tsx.

import type { ComponentType, ReactElement } from 'react';
import { IconAdjustmentsHorizontal, IconArchive, IconRestore, IconArrowBackUp, IconArrowForwardUp, IconArrowLeft, IconArrowsLeftRight, IconArrowsSort, IconAward, IconBell, IconBellRinging, IconBook, IconBookmarkPlus, IconCheck, IconChevronDown, IconChevronLeft, IconChevronRight, IconChevronUp, IconDots, IconEye, IconEyeOff, IconFileUpload, IconFlag, IconHistory, IconHome, IconInbox, IconInfoCircle, IconLanguage, IconBulb, IconLink, IconList, IconMessageCircle, IconMinus, IconPencil, IconPhotoPlus, IconLibraryPhoto, IconPlus, IconRefresh, IconSearch, IconSend, IconSparkles, IconSun, IconMoon, IconTrash, IconTypography, IconUser, IconUsers, IconX } from '@tabler/icons-react';
import type { SetIconProps } from '../icons';

function draw(Icon: ComponentType<Record<string, unknown>>, { size, stroke }: SetIconProps) {
    return <Icon size={size} stroke={stroke} aria-hidden />;
}

const ICONS: Record<string, ComponentType<Record<string, unknown>>> = {
    Archive: IconArchive,
    ArchiveRestore: IconRestore,
    ArrowDownUp: IconArrowsSort,
    ArrowLeft: IconArrowLeft,
    ArrowRightLeft: IconArrowsLeftRight,
    Award: IconAward,
    Bell: IconBell,
    BellRing: IconBellRinging,
    BookOpen: IconBook,
    BookmarkPlus: IconBookmarkPlus,
    Check: IconCheck,
    ChevronDown: IconChevronDown,
    ChevronLeft: IconChevronLeft,
    ChevronRight: IconChevronRight,
    ChevronUp: IconChevronUp,
    Eye: IconEye,
    EyeOff: IconEyeOff,
    FileUp: IconFileUpload,
    Ellipsis: IconDots,
    Flag: IconFlag,
    History: IconHistory,
    Home: IconHome,
    ImagePlus: IconPhotoPlus,
    Images: IconLibraryPhoto,
    Inbox: IconInbox,
    Info: IconInfoCircle,
    Languages: IconLanguage,
    Lightbulb: IconBulb,
    Link2: IconLink,
    List: IconList,
    MessageCircle: IconMessageCircle,
    Minus: IconMinus,
    PenLine: IconPencil,
    Plus: IconPlus,
    Redo2: IconArrowForwardUp,
    RefreshCw: IconRefresh,
    Search: IconSearch,
    Send: IconSend,
    SlidersHorizontal: IconAdjustmentsHorizontal,
    Sparkles: IconSparkles,
    Trash2: IconTrash,
    Type: IconTypography,
    Undo2: IconArrowBackUp,
    Sun: IconSun,
    Moon: IconMoon,
    User: IconUser,
    Users: IconUsers,
    X: IconX,
};

export default function render(name: string, props: SetIconProps): ReactElement | null {
    const Found = ICONS[name];
    return Found ? draw(Found, props) : null;
}
