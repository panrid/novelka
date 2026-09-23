package panrid.space.novelka.server.list;

import java.util.List;

public record ListPage<T>(List<T> items, int page, int size, long total, long totalPages) {
    public static <T> ListPage<T> of(List<T> items, ListQuery query, long total) {
        return new ListPage<>(items, query.page(), query.size(), total, (total + query.size() - 1) / query.size());
    }
}
