package panrid.space.novelka.server.mail;

/** One outgoing email: plain text is always sent; the HTML part mirrors it for mail clients that prefer HTML. */
public record Letter(String to, String subject, String text, String html) {
}
