package space.panrid.novelka.media;

import java.util.SortedMap;

/** A saved picture: its id and public URLs of each stored width. */
public record StoredImage(long id, SortedMap<Integer, String> urls) {

    /** The smallest copy at least {@code width} wide, or the largest there is. */
    public String url(int width) {
        return urls.tailMap(width).isEmpty() ? urls.get(urls.lastKey()) : urls.get(urls.tailMap(width).firstKey());
    }
}
