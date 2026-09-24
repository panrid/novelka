package space.panrid.novelka.platform.mail;

public interface Mailer {

    /** Sends right away. Callers inside a transaction wrap this in {@code AfterCommit.run}. */
    void send(Mail mail);
}
