export function novelPath(id: string) {
    return '/novels/' + encodeURIComponent(id);
}

export function chapterPath(id: string, number: number) {
    return novelPath(id) + '/chapters/' + number;
}
