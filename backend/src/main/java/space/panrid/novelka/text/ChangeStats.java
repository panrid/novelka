package space.panrid.novelka.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import space.panrid.novelka.platform.text.Block;

/**
 * How much a revision changed compared with its parent: blocks added, removed or edited
 * (by id), and roughly how many characters. Feeds «внесок кожного».
 */
public record ChangeStats(int blocksChanged, int charsChanged) {

    public static ChangeStats between(List<Block> before, List<Block> after) {
        Map<String, Block> old = new HashMap<>();
        before.forEach(block -> old.put(block.id(), block));
        int blocks = 0;
        int chars = 0;
        for (Block block : after) {
            Block previous = old.remove(block.id());
            if (previous == null) {
                blocks++;
                chars += block.text().length();
            } else if (!previous.equals(block)) {
                blocks++;
                chars += Math.max(1, changedChars(previous.text(), block.text()));
            }
        }
        for (Block removed : old.values()) {
            blocks++;
            chars += removed.text().length();
        }
        return new ChangeStats(blocks, chars);
    }

    /** Length of the differing middle once the common start and end are cut off. */
    static int changedChars(String a, String b) {
        int start = 0;
        int limit = Math.min(a.length(), b.length());
        while (start < limit && a.charAt(start) == b.charAt(start)) {
            start++;
        }
        int endA = a.length();
        int endB = b.length();
        while (endA > start && endB > start && a.charAt(endA - 1) == b.charAt(endB - 1)) {
            endA--;
            endB--;
        }
        return Math.max(endA - start, endB - start);
    }
}
