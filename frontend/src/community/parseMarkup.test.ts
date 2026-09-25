import { describe, expect, it } from 'vitest';
import { inline, parse } from './parseMarkup';

describe('markup', () => {
    it('reads the same markers as chapter files', () => {
        expect(inline('**жирний** і __теж__, *курсив* і _теж_, ++під++ ~~закр~~ ||спойлер||')).toEqual([
            { kind: 'bold', children: [{ kind: 'text', text: 'жирний' }] },
            { kind: 'text', text: ' і ' },
            { kind: 'bold', children: [{ kind: 'text', text: 'теж' }] },
            { kind: 'text', text: ', ' },
            { kind: 'italic', children: [{ kind: 'text', text: 'курсив' }] },
            { kind: 'text', text: ' і ' },
            { kind: 'italic', children: [{ kind: 'text', text: 'теж' }] },
            { kind: 'text', text: ', ' },
            { kind: 'underline', children: [{ kind: 'text', text: 'під' }] },
            { kind: 'text', text: ' ' },
            { kind: 'strike', children: [{ kind: 'text', text: 'закр' }] },
            { kind: 'text', text: ' ' },
            { kind: 'spoiler', children: [{ kind: 'text', text: 'спойлер' }] },
        ]);
    });

    it('keeps nicks, snake_case and lone markers as they are', () => {
        expect(inline('привіт @user_ab_cd і $panrid, 2 * 3 = 6')).toEqual([
            { kind: 'text', text: 'привіт ' },
            { kind: 'mention', nick: 'user_ab_cd' },
            { kind: 'text', text: ' і ' },
            { kind: 'team', handle: 'panrid' },
            { kind: 'text', text: ', 2 * 3 = 6' },
        ]);
        expect(inline('пошта a@b.com')).toEqual([{ kind: 'text', text: 'пошта a@b.com' }]);
    });

    it('finds links and nests marks', () => {
        expect(inline('див. https://novelka.panrid.space/n/x.')).toEqual([
            { kind: 'text', text: 'див. ' },
            { kind: 'link', url: 'https://novelka.panrid.space/n/x' },
            { kind: 'text', text: '.' },
        ]);
        expect(inline('**дуже *важливо***')).toEqual([
            { kind: 'bold', children: [{ kind: 'text', text: 'дуже ' }, { kind: 'italic', children: [{ kind: 'text', text: 'важливо' }] }] },
        ]);
    });

    it('groups quote lines and paragraphs', () => {
        const paragraphs = parse('> він сказав\n> двічі\nа я відповів\n\nновий абзац');
        expect(paragraphs.map((p) => [p.quote, p.lines.length])).toEqual([[true, 2], [false, 1], [false, 1]]);
    });
});
