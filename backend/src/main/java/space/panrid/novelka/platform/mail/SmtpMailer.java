package space.panrid.novelka.platform.mail;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import space.panrid.novelka.platform.SiteProperties;

@Component
class SmtpMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

    private final JavaMailSender sender;
    private final SiteProperties site;
    // Letters for Prometheus (план, етап 23, п. 5): novelka_mail_sent_total{result="sent|failed"}.
    private final Counter sent;
    private final Counter failed;

    SmtpMailer(JavaMailSender sender, SiteProperties site, MeterRegistry registry) {
        this.sender = sender;
        this.site = site;
        this.sent = Counter.builder("novelka.mail.sent").tag("result", "sent").description("Letters handed to the mail server")
                .register(registry);
        this.failed = Counter.builder("novelka.mail.sent").tag("result", "failed").description("Letters handed to the mail server")
                .register(registry);
    }

    @Override
    public void send(Mail mail) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(site.mailFrom());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), false);
            sender.send(message);
            sent.increment();
        } catch (MessagingException | MailException error) {
            failed.increment();
            // The reader can ask for the letter again; failing the request would not help them.
            log.error("Could not send \"{}\"", mail.subject(), error);
        }
    }
}
