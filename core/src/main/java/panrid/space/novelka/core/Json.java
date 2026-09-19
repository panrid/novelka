package panrid.space.novelka.core;

import com.fasterxml.jackson.databind.*;

public final class Json {
  public static final ObjectMapper M =
      new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  public static String write(Object o) {
    try {
      return M.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public static JsonNode read(String s) {
    try {
      return M.readTree(s);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid JSON", e);
    }
  }

  public static <T> T decode(String s, Class<T> type) {
    try {
      return M.readValue(s, type);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
