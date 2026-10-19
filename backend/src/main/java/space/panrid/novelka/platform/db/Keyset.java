package space.panrid.novelka.platform.db;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SortField;
import org.jooq.impl.DSL;

import space.panrid.novelka.platform.web.UserFacingException;

/**
 * Lists read by key, not by page number (етап 21.5): «наступні 20 після цієї». The order is a
 * list of keys, nulls last, the last of them unique (an id); the cursor holds the last row's
 * values of the keys, and the next page starts strictly after them — no OFFSET to walk, and a
 * row added meanwhile neither repeats nor pushes another off the page.
 */
public final class Keyset {

    private static final char SEPARATOR = '\u001f';
    private static final String NULL = "~";

    private Keyset() {
    }

    /** One key of the order; nulls always go last. */
    public record Key(Field<?> field, boolean descending) {

        public static Key asc(Field<?> field) {
            return new Key(field, false);
        }

        public static Key desc(Field<?> field) {
            return new Key(field, true);
        }

        SortField<?> sort() {
            return (descending ? field.desc() : field.asc()).nullsLast();
        }
    }

    /** The ORDER BY of the keys. */
    public static List<SortField<?>> order(List<Key> keys) {
        return keys.stream().<SortField<?>>map(Key::sort).toList();
    }

    /** The keys as extra columns of the select, so {@link #cursor} can read them back. */
    public static List<Field<?>> columns(List<Key> keys) {
        List<Field<?>> out = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            out.add(keys.get(i).field().as(column(i)));
        }
        return out;
    }

    /** The cursor after this row: its values of the keys, selected with {@link #columns}. */
    public static String cursor(List<Key> keys, Record row) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                text.append(SEPARATOR);
            }
            Object value = row.get(column(i), keys.get(i).field().getType());
            text.append(value == null ? NULL : "=" + value);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Rows strictly after the cursor in the keys' order; a cursor of another order is refused. */
    public static Condition after(List<Key> keys, String cursor) {
        List<Object> values = parse(keys, cursor);
        Condition result = DSL.falseCondition();
        Condition tied = DSL.noCondition();
        for (int i = 0; i < keys.size(); i++) {
            @SuppressWarnings("unchecked")
            Field<Object> field = (Field<Object>) keys.get(i).field();
            Object value = values.get(i);
            // Nulls go last: after a null only rows tied on it and later in the next keys follow.
            Condition beyond = value == null ? DSL.falseCondition()
                    : (keys.get(i).descending() ? field.lt(value) : field.gt(value)).or(field.isNull());
            result = result.or(tied.and(beyond));
            tied = tied.and(value == null ? field.isNull() : field.eq(value));
        }
        return result;
    }

    private static List<Object> parse(List<Key> keys, String cursor) {
        String text;
        try {
            text = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw stale();
        }
        String[] parts = text.split(String.valueOf(SEPARATOR), -1);
        if (parts.length != keys.size()) {
            throw stale();
        }
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < parts.length; i++) {
            values.add(value(parts[i], keys.get(i).field().getType()));
        }
        return values;
    }

    private static Object value(String part, Class<?> type) {
        if (part.equals(NULL)) {
            return null;
        }
        if (!part.startsWith("=")) {
            throw stale();
        }
        String text = part.substring(1);
        try {
            if (type == Integer.class) {
                return Integer.valueOf(text);
            }
            if (type == Long.class) {
                return Long.valueOf(text);
            }
            if (type == Short.class) {
                return Short.valueOf(text);
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(text);
            }
            if (type == OffsetDateTime.class) {
                return OffsetDateTime.parse(text);
            }
            if (type == Boolean.class) {
                return Boolean.valueOf(text);
            }
            if (type == String.class) {
                return text;
            }
        } catch (RuntimeException error) {
            throw stale();
        }
        throw new IllegalArgumentException("A key of type " + type + " cannot go into a cursor");
    }

    private static String column(int index) {
        return "keyset_" + index;
    }

    private static UserFacingException stale() {
        return UserFacingException.badRequest("Список змінився — оновіть сторінку.");
    }
}
