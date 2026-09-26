package space.panrid.novelka.source;

/**
 * One chapter in the site's table of contents.
 *
 * @param number    position in the list from 1: the address of the chapter on this site
 * @param ref       the site's own id for the chapter page
 * @param label     the number readers see there, such as «437.2»; null when it is the position
 * @param volume    the volume's name, if the site groups chapters so
 * @param available free to read; locked (paid) chapters are never taken
 */
public record SourceEntry(int number, String ref, String label, String title, String volume, boolean available) {
}
