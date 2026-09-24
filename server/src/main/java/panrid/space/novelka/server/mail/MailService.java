package panrid.space.novelka.server.mail;

import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends letters through SMTP ({@code MAIL_HOST}) on a background thread, so a slow provider never delays a request and
 * response time does not reveal whether an address exists. Without {@code MAIL_HOST} (development, tests) a letter is
 * only logged and kept in memory. Failures are logged without the recipient's address or the link.
 */
@Service
public final class MailService implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MailService.class);
    private final JavaMailSender sender;
    private final String from;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("mail").daemon().factory());
    private final Deque<Letter> recent = new ArrayDeque<>();

    public MailService(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.host:}") String host,
            @Value("${novelka.mail.from}") String from) throws Exception {
        this.sender = host.isBlank() ? null : sender.getIfAvailable();
        this.from = from;
        new InternetAddress(from, true);
        if (this.sender == null) LOG.info("MAIL_HOST is not set: letters are logged instead of sent");
    }

    public void send(Letter letter) {
        if (sender == null) {
            synchronized (recent) {
                recent.addFirst(letter);
                while (recent.size() > 50) recent.removeLast();
            }
            LOG.info("Letter not sent (no MAIL_HOST): {}", letter.subject());
            return;
        }
        executor.execute(() -> {
            try {
                var message = sender.createMimeMessage();
                var helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(new InternetAddress(from, true));
                helper.setTo(letter.to());
                helper.setSubject(letter.subject());
                helper.setText(letter.text(), letter.html());
                sender.send(message);
            } catch (Exception error) {
                LOG.warn("Letter '{}' was not sent: {}", letter.subject(), error.getClass().getSimpleName());
            }
        });
    }

    /** Letters kept when SMTP is not configured, newest first. Used by development tools and tests. */
    public List<Letter> recent() {
        synchronized (recent) { return List.copyOf(recent); }
    }

    @Override
    public void close() { executor.shutdown(); }
}
