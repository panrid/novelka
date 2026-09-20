package panrid.space.novelka.core.service.translation;

import panrid.space.novelka.core.model.Block;

import java.util.ArrayList;
import java.util.List;

public final class Segments {
    public static List<List<Block>> split(List<Block> blocks, int maxChars) {
        var result = new ArrayList<List<Block>>();
        var current = new ArrayList<Block>();
        int size = 0;
        for (var b : blocks) {
            if (b.text().length() > maxChars)
                throw new IllegalArgumentException(
                        "Paragraph " + b.id() + " exceeds segment limit; increase NOVELKA_SEGMENT_CHARS");
            if (size + b.text().length() > maxChars && !current.isEmpty()) {
                result.add(List.copyOf(current));
                current.clear();
                size = 0;
            }
            current.add(b);
            size += b.text().length();
        }
        if (!current.isEmpty()) result.add(List.copyOf(current));
        return result;
    }

    public static void validate(List<Block> source, List<Block> result) {
        if (source.size() != result.size())
            throw new IllegalArgumentException("Missing or extra translated blocks");
        for (int i = 0; i < source.size(); i++)
            if (!source.get(i).id().equals(result.get(i).id())
                    || !source.get(i).kind().equals(result.get(i).kind())
                    || result.get(i).text() == null
                    || result.get(i).text().isBlank())
                throw new IllegalArgumentException("Invalid translated block " + i);
    }
}
