import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, useBlocker, useNavigate } from '@tanstack/react-router';
import { WorkspaceMarkdown } from './WorkspaceMarkdown';
import { download, saveFile } from '../../api/client';
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
    const navigate = useNavigate();
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
    const [loadingDocument, setLoadingDocument] = useState(false);
    const [attachmentError, setAttachmentError] = useState<string | null>(null);
    const [savedForm, setSavedForm] = useState(JSON.stringify(['', 'canon', '', '']));
    const requestSerial = useRef(0);
    const formSnapshot = JSON.stringify([title, kind, slug, content]);
    const dirty = savedForm !== '' && savedForm !== formSnapshot;

    useEffect(() => {
        const beforeUnload = (event: BeforeUnloadEvent) => {
            if (dirty) { event.preventDefault(); }
        };
        window.addEventListener('beforeunload', beforeUnload);
        return () => window.removeEventListener('beforeunload', beforeUnload);
    }, [dirty]);

    const allowSwitch = () => !dirty || window.confirm('Є незбережені зміни. Перейти й відкинути їх?');
    useBlocker({ shouldBlockFn: () => dirty && !window.confirm('Є незбережені зміни. Залишити сторінку й відкинути їх?') });

    // Queries
    const docsQuery = useInfiniteQuery({
        queryKey: ['workspace-docs', editionId, selectedKind, searchQuery],
        initialPageParam: 0,
        queryFn: ({ pageParam }) => studioApi.workspaceDocuments(editionId, selectedKind, searchQuery, pageParam),
        getNextPageParam: (last, pages) => last.length === 20 ? pages.length * 20 : undefined,
    });
    const documents = docsQuery.data?.pages.flat() ?? [];

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

    const filteredDocs = documents.filter((doc: DocumentSummary) => {
        if (selectedKind !== 'all' && doc.kind !== selectedKind) return false;
        if (searchQuery.trim()) {
            const q = searchQuery.toLowerCase();
            return doc.title.toLowerCase().includes(q) || (doc.snippet && doc.snippet.toLowerCase().includes(q));
        }
        return true;
    });

    // When an active document loads, populate the editor form
    const selectDocument = async (doc: Pick<DocumentSummary, 'slug'>, discardApproved = false) => {
        if (saveMutation.isPending || restoreMutation.isPending || (!discardApproved && !allowSwitch())) return;
        const serial = ++requestSerial.current;
        setLoadingDocument(true);
        setConflictError(null);
        try {
            const details = await studioApi.workspaceDocument(editionId, doc.slug);
            if (serial !== requestSerial.current) return;
            setActiveSlug(details.slug);
            setIsPreview(false);
            setTitle(details.title);
            setKind(details.kind);
            setSlug(details.slug);
            setContent(details.content);
            setVersion(details.version);
            setSavedForm(JSON.stringify([details.title, details.kind, details.slug, details.content]));
        } catch (error) {
            if (serial === requestSerial.current) setConflictError((error as Error).message);
        } finally {
            if (serial === requestSerial.current) setLoadingDocument(false);
        }
    };

    const openWiki = async (target: string) => {
        const [path, anchor] = target.split('#');
        if (!path && anchor) {
            document.getElementById(`workspace-heading-${anchor}`)?.scrollIntoView();
            return;
        }
        const chapter = /(?:розділ|глава)\s*(\d+)/iu.exec(path ?? '');
        if (chapter) {
            void navigate({ to: '/studio/$editionId/chapters/$number', params: { editionId: String(editionId), number: chapter[1]! } });
            return;
        }
        if (saveMutation.isPending || restoreMutation.isPending || !allowSwitch()) return;
        const serial = ++requestSerial.current;
        setLoadingDocument(true);
        const name = (path ?? '').split('/').pop()!.replace(/\.md$/, '').normalize('NFC');
        try {
            const matches = await studioApi.workspaceDocuments(editionId, undefined, name);
            if (serial !== requestSerial.current) return;
            const doc = matches.find((item) => item.slug === name || item.title.normalize('NFC') === name);
            if (doc) await selectDocument(doc, true);
            else setConflictError('Документ не знайдено. Знайдіть його за назвою у списку матеріалів.');
        } catch (error) {
            if (serial === requestSerial.current) setConflictError((error as Error).message);
        } finally {
            if (serial === requestSerial.current) setLoadingDocument(false);
        }
    };

    const startNewDocument = () => {
        if (saveMutation.isPending || restoreMutation.isPending || !allowSwitch()) return;
        ++requestSerial.current;
        setLoadingDocument(false);
        setSavedForm(JSON.stringify(['', selectedKind !== 'all' ? selectedKind : 'canon', '', '']));
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
            setSavedForm(JSON.stringify([savedDoc.title, savedDoc.kind, savedDoc.slug, savedDoc.content]));
            setTitle(savedDoc.title);
            setKind(savedDoc.kind);
            setContent(savedDoc.content);
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
            setSavedForm('');
            startNewDocument();
            queryClient.invalidateQueries({ queryKey: ['workspace-docs', editionId] });
        },
    });

    const restoreMutation = useMutation({
        mutationFn: (revId: number) => studioApi.restoreWorkspaceRevision(editionId, activeSlug!, revId, version!),
        onSuccess: (restored: DocumentDetails) => {
            setSavedForm(JSON.stringify([restored.title, restored.kind, restored.slug, restored.content]));
            setKind(restored.kind);
            setShowHistory(false);
            setTitle(restored.title);
            setContent(restored.content);
            setVersion(restored.version);
            queryClient.invalidateQueries({ queryKey: ['workspace-docs', editionId] });
            queryClient.invalidateQueries({ queryKey: ['workspace-doc', editionId, activeSlug] });
        },
    });

    const uploadAttachments = useMutation({
        mutationFn: async (files: File[]) => {
            setAttachmentError(null);
            for (const file of files) {
                const uploaded = await studioApi.uploadImage(file, 'illustration');
                await studioApi.addWorkspaceAttachment(editionId, { imageId: uploaded.id, name: file.name,
                    ...(activeSlug ? { documentSlug: activeSlug } : {}) });
            }
        },
        onSettled: () => { void queryClient.invalidateQueries({ queryKey: ['workspace-attachments', editionId] }); },
        onError: (error: Error) => setAttachmentError(error.message),
    });
    const removeAttachment = useMutation({
        mutationFn: (id: number) => studioApi.deleteWorkspaceAttachment(editionId, id),
        onSuccess: () => { void queryClient.invalidateQueries({ queryKey: ['workspace-attachments', editionId] }); },
        onError: (error: Error) => setAttachmentError(error.message),
    });

    const handleSave = (e: FormEvent) => {
        e.preventDefault();
        saveMutation.mutate();
    };

    const handleReloadFromServer = () => {
        if (activeSlug) void selectDocument({ slug: activeSlug });
    };

    const handleExport = async () => {
        try {
            const file = await download(`/api/studio/editions/${editionId}/workspace/export.zip`);
            saveFile(file.blob, file.name);
        } catch (error) {
            setConflictError((error as Error).message);
        }
    };

    return (
        <EditionShell tab="workspace">
            <div className={styles.page}>
                <header className={styles.header}>
                    <h2 className={styles.title}>Матеріали авторського простору</h2>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                        <Button onClick={handleExport} variant="secondary">
                            Завантажити архів
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
                            <button type="button"
                                key={doc.slug}
                                className={`${styles.docItem} ${activeSlug === doc.slug ? styles.docItemActive : ''}`}
                                onClick={() => selectDocument(doc)}
                                disabled={saveMutation.isPending || restoreMutation.isPending}
                            >
                                <div className={styles.docItemHead}>
                                    <span className={styles.docTitle}>{doc.title}</span>
                                    <span className={styles.kindBadge}>{KIND_NAMES[doc.kind] ?? doc.kind}</span>
                                </div>
                                {doc.snippet && <div className={styles.docSnippet}>{doc.snippet}</div>}
                                <div className={styles.docMeta}>
                                    Версія {doc.version} · {relativeTime(new Date(doc.updatedAt))}
                                    {doc.updatedByNick ? ` (${doc.updatedByNick})` : ''}
                                </div>
                            </button>
                        ))}
                    {docsQuery.hasNextPage && <Button type="button" onClick={() => { void docsQuery.fetchNextPage(); }} isDisabled={docsQuery.isFetchingNextPage}>Ще матеріали</Button>}
                    </aside>

                    <main className={styles.editorPanel}>
                        <form onSubmit={handleSave} className={styles.editorHeader}>
                            <fieldset disabled={loadingDocument || saveMutation.isPending} style={{ display: 'contents' }}>
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
                                                saveFile(new Blob([content], { type: 'text/markdown;charset=utf-8' }), `${slug || 'document'}-моя-версія.md`);
                                            }}
                                            variant="secondary"
                                        >
                                            Завантажити мій текст
                                        </Button>
                                    </div>
                                </div>
                            )}

                            <div className={styles.editorControls}>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
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
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                                    {activeSlug && (
                                        <Button
                                            type="button"
                                            variant="secondary"
                                            onClick={() => setShowHistory(true)}
                                        >
                                            Історія змін
                                        </Button>
                                    )}
                                    <Button type="submit" variant="primary" isDisabled={saveMutation.isPending || loadingDocument}>
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
                                    <WorkspaceMarkdown text={content} open={(target) => { void openWiki(target); }} image={(target) => attachmentsQuery.data?.find((item) => item.name === target.split('/').pop())?.url} />
                                </div>
                            )}

                            {activeDocQuery.data?.links && activeDocQuery.data.links.length > 0 && (
                                <div className={styles.linksSection}>
                                    <span style={{ fontSize: '13px', fontWeight: 600 }}>Посилання:</span>
                                    <div className={styles.linksList}>
                                        {activeDocQuery.data.links.map((link, i) => (
                                            /(?:розділ|глава)\s*(\d+)/iu.test(link) ? <Link key={i}
                                                to="/studio/$editionId/chapters/$number"
                                                params={{ editionId: String(editionId), number: /(?:розділ|глава)\s*(\d+)/iu.exec(link)![1]! }}
                                                className={styles.linkTag}>{link}</Link> : <button type="button" key={i} className={styles.linkTag} onClick={() => { void openWiki(link); }}>{link}</button>
                                        ))}
                                    </div>
                                </div>
                            )}
                            </fieldset>
                        </form>

                        {/* Attachments section */}
                        <div className={styles.attachmentsSection}>
                            <h3 style={{ fontSize: '16px', fontWeight: 600, margin: 0 }}>
                                Вкладення авторського простору
                            </h3>
                            <label>
                                Додати ілюстрації
                                <input type="file" accept="image/jpeg,image/png,image/webp" multiple
                                    disabled={uploadAttachments.isPending} aria-label="Додати ілюстрації"
                                    onChange={(event) => {
                                        const files = Array.from(event.target.files ?? []);
                                        if (files.length) uploadAttachments.mutate(files);
                                        event.target.value = '';
                                    }} />
                            </label>
                            {uploadAttachments.isPending && <p role="status">Завантажуємо ілюстрації…</p>}
                            {attachmentError && <p role="alert">{attachmentError}</p>}
                            <div className={styles.attachmentsGrid}>
                                {attachmentsQuery.data?.map((att) => (
                                    <div key={att.id} className={styles.attachmentCard}>
                                        {att.url && <img src={att.url} alt={att.name} className={styles.attachmentImg} />}
                                        <div className={styles.attachmentInfo}>
                                            <span className={styles.attachmentName}>{att.name}</span>
                                            {att.description && <span style={{ color: 'var(--muted)' }}>{att.description}</span>}
                                            <Button type="button" variant="secondary" isDisabled={removeAttachment.isPending}
                                                onClick={() => removeAttachment.mutate(att.id)} aria-label={`Вилучити ${att.name}`}>Вилучити</Button>
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
