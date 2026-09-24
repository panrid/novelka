package space.panrid.novelka.platform.mail;

import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import space.panrid.novelka.platform.SiteProperties;

@Component
class SmtpMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailer.class);

    private final JavaMailSender sender;
    private final SiteProperties site;

    SmtpMailer(JavaMailSender sender, SiteProperties site) {
        this.sender = sender;
        this.site = site;
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
        } catch (MessagingException | MailException error) {
            // The reader can ask for the letter again; failing the request would not help them.
            log.error("Could not send \"{}\"", mail.subject(), error);
        }
    }
}
