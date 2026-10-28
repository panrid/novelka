import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { relativeTime } from '../../lib/dates';
import {
    studioApi,
    type DocumentDetails,
    type DocumentSummary,
    type WorkspaceRevision,
} from '../../studio/api';
import { Button } from '../../ui/Button';
import { Loading } from '../../ui/Loading';
import { TextInput } from '../../ui/TextInput';
import { useEditionId } from './EditionPage';
import { EditionShell } from './EditionShell';
import styles from './workspace.module.css';

const KINDS: { id: string; label: string }[] = [
    { id: 'all', label: 'Усі' },
    { id: 'canon', label: 'Канон' },
    { id: 'timeline', label: 'Хронологія' },
    { id: 'plan', label: 'Плани' },
    { id: 'decision', label: 'Рішення й реткони' },
    { id: 'status', label: 'Стан роботи' },
    { id: 'meta', label: 'Службове' },
    { id: 'note', label: 'Нотатки' },
];

const KIND_NAMES: Record<string, string> = {
    canon: 'Канон',
    timeline: 'Хронологія',
    plan: 'План',
    decision: 'Рішення',
    status: 'Стан роботи',
    meta: 'Службове',
    note: 'Нотатка',
};

export function WorkspacePage() {
    const editionId = useEditionId();
    const queryClient = useQueryClient();

    const [selectedKind, setSelectedKind] = useState<string>('all');
    const [searchQuery, setSearchQuery] = useState<string>('');
    const [activeSlug, setActiveSlug] = useState<string | null>(null);

    // Form fields
    const [title, setTitle] = useState<string>('');
    const [kind, setKind] = useState<string>('canon');
    const [slug, setSlug] = useState<string>('');
    const [content, setContent] = useState<string>('');
    const [version, setVersion] = useState<number | null>(null);

    const [isPreview, setIsPreview] = useState<boolean>(false);
    const [conflictError, setConflictError] = useState<string | null>(null);
    const [showHistory, setShowHistory] = useState<boolean>(false);

    // Queries
    const docsQuery = useQuery({
        queryKey: ['workspace-docs', editionId, selectedKind, searchQuery],
        queryFn: () => studioApi.workspaceDocuments(editionId, selectedKind, searchQuery),
    });

    const activeDocQuery = useQuery({
        queryKey: ['workspace-doc', editionId, activeSlug],
        queryFn: () => studioApi.workspaceDocument(editionId, activeSlug!),
        enabled: Boolean(activeSlug),
    });

    const revisionsQuery = useQuery({
        queryKey: ['workspace-revisions', editionId, activeSlug],
        queryFn: () => studioApi.workspaceRevisions(editionId, activeSlug!),
        enabled: Boolean(activeSlug && showHistory),
    });

    const attachmentsQuery = useQuery({
        queryKey: ['workspace-attachments', editionId],
        queryFn: () => studioApi.workspaceAttachments(editionId),
    });

    const filteredDocs = (docsQuery.data ?? []).filter((doc: DocumentSummary) => {
        if (selectedKind !== 'all' && doc.kind !== selectedKind) return false;
        if (searchQuery.trim()) {
            const q = searchQuery.toLowerCase();
            return doc.title.toLowerCase().includes(q) || (doc.snippet && doc.snippet.toLowerCase().includes(q));
        }
        return true;
    });

    // When an active document loads, populate the editor form
    const selectDocument = (doc: DocumentSummary) => {
        setActiveSlug(doc.slug);
        setConflictError(null);
        setIsPreview(false);
        studioApi.workspaceDocument(editionId, doc.slug).then((details) => {
            setTitle(details.title);
            setKind(details.kind);
            setSlug(details.slug);
            setContent(details.content);
            setVersion(details.version);
        });
    };

    const startNewDocument = () => {
        setActiveSlug(null);
        setTitle('');
        setKind(selectedKind !== 'all' ? selectedKind : 'canon');
        setSlug('');
        setContent('');
        setVersion(null);
        setConflictError(null);
        setIsPreview(false);
    };

    // Save mutation
    const saveMutation = useMutation({
        mutationFn: async () => {
            const saveSlug = slug.trim() || title.toLowerCase().replace(/[^a-z0-9а-яіїєґ]+/gi, '-');
            return studioApi.saveWorkspaceDocument(editionId, saveSlug, {
                title,
                kind,
                content,
                version,
            });
        },
        onSuccess: (savedDoc: DocumentDetails) => {
            setConflictError(null);
            setVersion(savedDoc.version);
            setActiveSlug(savedDoc.slug);
            setSlug(savedDoc.slug);
            queryClient.invalidateQueries({ queryKey: ['workspace-docs', editionId] });
            queryClient.invalidateQueries({ queryKey: ['workspace-doc', editionId, savedDoc.slug] });
        },
        onError: (err: Error) => {
            setConflictError(err.message);
        },
    });

    const deleteMutation = useMutation({
        mutationFn: (docSlug: string) => studioApi.deleteWorkspaceDocument(editionId, docSlug),
        onSuccess: () => {
            startNewDocument();
            queryClient.invalidateQueries({ queryKey: ['workspace-docs', editionId] });
        },
    });

    const restoreMutation = useMutation({
        mutationFn: (revId: number) => studioApi.restoreWorkspaceRevision(editionId, activeSlug!, revId),
        onSuccess: (restored: DocumentDetails) => {
            setShowHistory(false);
            setTitle(restored.title);
            setContent(restored.content);
            setVersion(restored.version);
            queryClient.invalidateQueries({ queryKey: ['workspace-docs', editionId] });
            queryClient.invalidateQueries({ queryKey: ['workspace-doc', editionId, activeSlug] });
        },
    });

    const handleSave = (e: FormEvent) => {
        e.preventDefault();
        saveMutation.mutate();
    };

    const handleReloadFromServer = () => {
        if (!activeSlug) return;
        studioApi.workspaceDocument(editionId, activeSlug).then((details) => {
            setTitle(details.title);
            setKind(details.kind);
            setContent(details.content);
            setVersion(details.version);
            setConflictError(null);
        });
    };

    const handleExport = async () => {
        const exported = await studioApi.exportWorkspace(editionId);
        const blob = new Blob([JSON.stringify(exported, null, 2)], { type: 'application/json;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `workspace-${editionId}.json`;
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <EditionShell tab="workspace">
            <div className={styles.page}>
                <header className={styles.header}>
                    <h2 className={styles.title}>Матеріали авторського простору</h2>
                    <div style={{ display: 'flex', gap: '8px' }}>
                        <Button onClick={handleExport} variant="secondary">
                            Експортувати
                        </Button>
                        <Button onClick={startNewDocument} variant="primary">
                            + Новий матеріал
                        </Button>
                    </div>
                </header>

                <div className={styles.filters} role="tablist" aria-label="Види матеріалів">
                    {KINDS.map((k) => (
                        <button
                            key={k.id}
                            type="button"
                            role="tab"
                            aria-selected={selectedKind === k.id}
                            className={`${styles.filterBtn} ${selectedKind === k.id ? styles.filterActive : ''}`}
                            onClick={() => setSelectedKind(k.id)}
                        >
                            {k.label}
                        </button>
                    ))}
                </div>

                <div className={styles.searchBar}>
                    <TextInput
                        label="Пошук"
                        value={searchQuery}
                        onChange={setSearchQuery}
                        placeholder="Пошук у каноні, планах, рішеннях..."
                        aria-label="Пошук матеріалів"
                    />
                </div>

                <div className={styles.layout}>
                    <aside className={styles.docList} aria-label="Список документів">
                        {docsQuery.isLoading && <Loading />}
                        {filteredDocs.length === 0 && (
                            <div className={styles.docItem} style={{ cursor: 'default' }}>
                                Документів не знайдено
                            </div>
                        )}
                        {filteredDocs.map((doc: DocumentSummary) => (
                            <div
                                key={doc.slug}
                                className={`${styles.docItem} ${activeSlug === doc.slug ? styles.docItemActive : ''}`}
                                onClick={() => selectDocument(doc)}
                                role="button"
                                tabIndex={0}
                            >
                                <div className={styles.docItemHead}>
                                    <span className={styles.docTitle}>{doc.title}</span>
                                    <span className={styles.kindBadge}>{KIND_NAMES[doc.kind] ?? doc.kind}</span>
                                </div>
                                {doc.snippet && <div className={styles.docSnippet}>{doc.snippet}</div>}
                                <div className={styles.docMeta}>
                                    v{doc.version} · {relativeTime(new Date(doc.updatedAt))}
                                    {doc.updatedByNick ? ` (${doc.updatedByNick})` : ''}
                                </div>
                            </div>
                        ))}
                    </aside>

                    <main className={styles.editorPanel}>
                        <form onSubmit={handleSave} className={styles.editorHeader}>
                            <TextInput
                                label="Назва матеріалу"
                                value={title}
                                onChange={setTitle}
                                placeholder="Наприклад: Закони Магії, Хронологія Епох..."
                                isRequired
                            />

                            <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
                                <div style={{ flex: 1, minWidth: '150px' }}>
                                    <label htmlFor="kind-select" style={{ display: 'block', fontSize: '12px', marginBottom: '4px', fontWeight: 600 }}>
                                        Вид документа
                                    </label>
                                    <select
                                        id="kind-select"
                                        value={kind}
                                        onChange={(e) => setKind(e.target.value)}
                                        style={{
                                            width: '100%',
                                            padding: '8px',
                                            borderRadius: 'var(--radius)',
                                            border: '1px solid var(--line)',
                                            background: 'var(--bg)',
                                            color: 'var(--text)',
                                        }}
                                    >
                                        <option value="canon">Канон і світобудова</option>
                                        <option value="timeline">Хронологія</option>
                                        <option value="plan">Плани та епохи</option>
                                        <option value="decision">Рішення й реткони</option>
                                        <option value="status">Стан роботи</option>
                                        <option value="meta">Службове</option>
                                        <option value="note">Нотатки</option>
                                    </select>
                                </div>
                                <div style={{ flex: 1, minWidth: '150px' }}>
                                    <TextInput
                                        label="Адреса (slug)"
                                        value={slug}
                                        onChange={setSlug}
                                        placeholder="автоматично з назви"
                                    />
                                </div>
                            </div>

                            {conflictError && (
                                <div className={styles.conflictAlert} role="alert">
                                    <strong>Конфлікт версій:</strong> {conflictError}
                                    <div className={styles.conflictActions}>
                                        <Button type="button" onClick={handleReloadFromServer}>
                                            Завантажити версію із сервера
                                        </Button>
                                        <Button
                                            type="button"
                                            onClick={() => {
                                                setVersion(null);
                                                saveMutation.mutate();
                                            }}
                                            variant="secondary"
                                        >
                                            Перезаписати своєю версією
                                        </Button>
                                    </div>
                                </div>
                            )}

                            <div className={styles.editorControls}>
                                <div style={{ display: 'flex', gap: '8px' }}>
                                    <Button
                                        type="button"
                                        variant={!isPreview ? 'primary' : 'secondary'}
                                        onClick={() => setIsPreview(false)}
                                    >
                                        Редагування
                                    </Button>
                                    <Button
                                        type="button"
                                        variant={isPreview ? 'primary' : 'secondary'}
                                        onClick={() => setIsPreview(true)}
                                    >
                                        Попередній перегляд
                                    </Button>
                                </div>
                                <div style={{ display: 'flex', gap: '8px' }}>
                                    {activeSlug && (
                                        <Button
                                            type="button"
                                            variant="secondary"
                                            onClick={() => setShowHistory(true)}
                                        >
                                            Історія змін
                                        </Button>
                                    )}
                                    <Button type="submit" variant="primary" isDisabled={saveMutation.isPending}>
                                        {saveMutation.isPending ? 'Збереження...' : 'Зберегти'}
                                    </Button>
                                    {activeSlug && (
                                        <Button
                                            type="button"
                                            variant="secondary"
                                            onClick={() => {
                                                if (window.confirm('Видалити цей документ?')) {
                                                    deleteMutation.mutate(activeSlug);
                                                }
                                            }}
                                        >
                                            Видалити
                                        </Button>
                                    )}
                                </div>
                            </div>

                            {!isPreview ? (
                                <textarea
                                    className={styles.textarea}
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    placeholder="Текст документа в форматі Markdown. Можна використовувати посилання виду [[Назва документа]] або [[Розділ 1]]."
                                    aria-label="Вміст документа"
                                />
                            ) : (
                                <div className={styles.preview}>
                                    {content.split('\n\n').map((paragraph, i) => (
                                        <p key={i}>{paragraph}</p>
                                    ))}
                                </div>
                            )}

                            {activeDocQuery.data?.links && activeDocQuery.data.links.length > 0 && (
                                <div className={styles.linksSection}>
                                    <span style={{ fontSize: '13px', fontWeight: 600 }}>Посилання:</span>
                                    <div className={styles.linksList}>
                                        {activeDocQuery.data.links.map((link, i) => (
                                            <span key={i} className={styles.linkTag}>
                                                {link}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </form>

                        {/* Attachments section */}
                        <div className={styles.attachmentsSection}>
                            <h3 style={{ fontSize: '16px', fontWeight: 600, margin: 0 }}>
                                Вкладення авторського простору
                            </h3>
                            <div className={styles.attachmentsGrid}>
                                {attachmentsQuery.data?.map((att) => (
                                    <div key={att.id} className={styles.attachmentCard}>
                                        {att.url && <img src={att.url} alt={att.name} className={styles.attachmentImg} />}
                                        <div className={styles.attachmentInfo}>
                                            <span className={styles.attachmentName}>{att.name}</span>
                                            {att.description && <span style={{ color: 'var(--muted)' }}>{att.description}</span>}
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </main>
                </div>

                {/* Revision history modal */}
                {showHistory && (
                    <div className={styles.historyModal} role="dialog" aria-modal="true" aria-label="Історія змін">
                        <div className={styles.historyCard}>
                            <div className={styles.historyHead}>
                                <strong>Історія змін документа</strong>
                                <Button type="button" onClick={() => setShowHistory(false)}>
                                    ✕
                                </Button>
                            </div>
                            <div className={styles.historyBody}>
                                {revisionsQuery.isLoading && <Loading />}
                                {revisionsQuery.data?.map((rev: WorkspaceRevision) => (
                                    <div key={rev.id} className={styles.historyItem}>
                                        <div>
                                            <strong>Версія {rev.version}</strong>
                                            <div style={{ fontSize: '12px', color: 'var(--muted)' }}>
                                                {relativeTime(new Date(rev.createdAt))}
                                                {rev.authorNick ? ` · ${rev.authorNick}` : ''}
                                                {rev.comment ? ` · ${rev.comment}` : ''}
                                            </div>
                                        </div>
                                        <Button
                                            type="button"
                                            onClick={() => restoreMutation.mutate(rev.id)}
                                            isDisabled={restoreMutation.isPending}
                                        >
                                            Відновити
                                        </Button>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </EditionShell>
    );
}
