package panrid.space.novelka.server.list;

import java.util.Map;

/** Common, bounded parameters for database-backed lists. Page numbers start at one. */
public record ListQuery(int page, int size, String q, String sort, String direction) {
    public ListQuery {
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("Некоректна сторінка або розмір сторінки (1–100).");
        q = q == null ? "" : q.strip();
        if (q.length() > 200) throw new IllegalArgumentException("Пошук має містити до 200 символів.");
        sort = sort == null ? "" : sort;
        direction = direction == null ? "desc" : direction.toLowerCase(java.util.Locale.ROOT);
        if (!direction.equals("asc") && !direction.equals("desc")) throw new IllegalArgumentException("Напрямок сортування: asc або desc.");
    }

    public int offset() {
        long offset = (long) (page - 1) * size;
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Номер сторінки завеликий.");
        return (int) offset;
    }

    public String order(Map<String, String> allowed, String fallback, String tieBreaker) {
        if (!sort.isEmpty() && !allowed.containsKey(sort)) throw new IllegalArgumentException("Невідоме поле сортування: " + sort);
        String column = sort.isEmpty() ? allowed.get(fallback) : allowed.get(sort);
        return " ORDER BY " + column + " " + direction + "," + tieBreaker + " " + direction;
    }

    public String pattern() {
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
