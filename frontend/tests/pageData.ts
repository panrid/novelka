export function pageData<T>(items: T[], page = 1, size = 25, total = items.length) {
    return { items, page, size, total, totalPages: Math.ceil(total / size) };
}
