/** Stored texts carry mentions as <@account-id> tokens; readers get a map of ids to current nicknames. */
export type Names = Record<string, string>;

export const TOKEN = /<@([0-9a-f-]{36})>/g;

/** Text for editing: tokens become @nick again, so the server re-links them on save. */
export function editableText(body: string, names: Names) {
    return body.replace(TOKEN, (token, id: string) => names[id] ? '@' + names[id] : token);
}

/**
 * A quote to insert into a composer: every line prefixed with "> ". Mentions become plain nicknames,
 * so quoting never notifies the quoted people again.
 */
export function quote(text: string, author: string, names: Names) {
    const plain = text.replace(TOKEN, (_token, id: string) => names[id] ?? 'користувач').trim();
    return `> ${author}:\n` + plain.split('\n').map(line => '> ' + line).join('\n') + '\n\n';
}

/** Adds text to what the person is typing, keeping a blank line between blocks. */
export function append(current: string, addition: string) {
    if (!current.trim()) return addition;
    return current.replace(/\s*$/, '') + '\n\n' + addition;
}
