package space.panrid.novelka.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import space.panrid.novelka.platform.mail.Mail;
import space.panrid.novelka.platform.mail.Mailer;

/** Collects letters instead of sending them. */
public class TestMailbox implements Mailer {

    private static final Pattern TOKEN = Pattern.compile("[?&]token=([A-Za-z0-9_-]+)");

    private final List<Mail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(Mail mail) {
        sent.add(mail);
    }

    public List<Mail> to(String email) {
        return sent.stream().filter(mail -> mail.to().equalsIgnoreCase(email)).toList();
    }

    /** The token from the newest letter to {@code email} whose link path is {@code path}. */
    public String tokenFrom(String email, String path) {
        List<Mail> letters = to(email);
        for (int i = letters.size() - 1; i >= 0; i--) {
            String text = letters.get(i).text();
            int at = text.indexOf(path + "?token=");
            if (at >= 0) {
                Matcher matcher = TOKEN.matcher(text.substring(at + path.length()));
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        throw new AssertionError("No letter with " + path + " link to " + email);
    }
}
