import { useMutation } from '@tanstack/react-query';
import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { api } from '../api/client';
import { Button } from '../ui/Button';
import { Notice } from '../ui/Notice';
import { Sheet } from '../ui/Sheet';
import { TextInput } from '../ui/TextInput';
import { Toggle } from '../ui/Toggle';

/**
 * «Скарга правовласника» (етап 19, п. 9): the translation is hidden at once and moderators look
 * at the complaint; «Правила сайту» say how.
 */
export function RightsComplaintSheet({ editionId, title, onClose }: { editionId: number; title: string; onClose: () => void }) {
    const [statement, setStatement] = useState('');
    const [contact, setContact] = useState('');
    const [holder, setHolder] = useState(false);
    const send = useMutation({
        mutationFn: () => api<{ hidden: boolean }>('/api/rights-complaints', {
            method: 'POST', body: JSON.stringify({ editionId, statement: statement.trim(), contact: contact.trim() }),
        }),
    });
    return (
        <Sheet open onClose={onClose} title="Скарга правовласника">
            {send.isSuccess ? (
                <>
                    <Notice tone="success">Переклад «{title}» приховано від читачів. Модератори розглянуть скаргу й напишуть вам.</Notice>
                    <Button wide onPress={onClose}>Готово</Button>
                </>
            ) : (
                <form style={{ display: 'grid', gap: 12 }} onSubmit={(event) => { event.preventDefault(); send.mutate(); }}>
                    <p>
                        Переклад одразу сховається від читачів, поки модератори розглядають скаргу.{' '}
                        <Link to="/rules">Правила сайту</Link>
                    </p>
                    <TextInput label="Чиї права порушено" value={statement} onChange={setStatement} multiline isRequired
                        hint="Твір, автор чи видавець, і чому переклад без дозволу." />
                    <TextInput label="Як з вами зв'язатися" value={contact} onChange={setContact} isRequired hint="Пошта, сайт видавця, ім'я." />
                    <Toggle label="Я правовласник або дію від його імені" isSelected={holder} onChange={setHolder} />
                    {send.isError && <Notice tone="error">{send.error.message}</Notice>}
                    <Button type="submit" wide pending={send.isPending} pendingLabel="Надсилаємо…"
                        isDisabled={!holder || !statement.trim() || !contact.trim()}>Надіслати скаргу й приховати переклад</Button>
                </form>
            )}
        </Sheet>
    );
}
