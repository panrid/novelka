package space.panrid.novelka.platform.mail;

/** A plain-text email. Plain text keeps links readable in every mail app and needs no escaping. */
public record Mail(String to, String subject, String text) {
}
