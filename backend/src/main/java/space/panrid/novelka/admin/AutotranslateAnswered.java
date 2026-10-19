package space.panrid.novelka.admin;

/** The site owner opened «Автопереклад із сайту» to the person who asked, or refused. */
public record AutotranslateAnswered(long requesterId, boolean granted) {
}
