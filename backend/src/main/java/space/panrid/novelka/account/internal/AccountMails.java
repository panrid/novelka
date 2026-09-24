package space.panrid.novelka.account.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import space.panrid.novelka.platform.SiteProperties;
import space.panrid.novelka.platform.mail.Mail;

/** Texts of the account letters. Links open pages of the web app, which call the API. */
@Component
class AccountMails {

    private final SiteProperties site;

    AccountMails(SiteProperties site) {
        this.site = site;
    }

    Mail verify(String email, String nick, String token) {
        return new Mail(email, "Підтвердіть пошту на Новелці", """
                Привіт, %s!

                Щоб завершити реєстрацію на Новелці, відкрийте посилання:
                %s

                Посилання діє добу. Якщо ви не реєструвались, просто проігноруйте цей лист.
                """.formatted(nick, link("/verify", token)));
    }

    Mail reset(String email, String nick, String token) {
        return new Mail(email, "Новий пароль для Новелки", """
                Привіт, %s!

                Щоб задати новий пароль, відкрийте посилання:
                %s

                Посилання діє 30 хвилин і спрацює один раз. Якщо ви не просили змінити пароль,
                нічого не робіть — старий пароль лишиться чинним.
                """.formatted(nick, link("/reset", token)));
    }

    Mail confirmNewEmail(String newEmail, String nick, String token) {
        return new Mail(newEmail, "Підтвердіть нову пошту на Новелці", """
                Привіт, %s!

                Ви вказали цю адресу як нову пошту свого акаунта. Щоб підтвердити, відкрийте посилання:
                %s

                Доки ви цього не зробите, лишається чинною стара адреса. Посилання діє добу.
                """.formatted(nick, link("/confirm-email", token)));
    }

    Mail emailChanged(String oldEmail, String nick, String newEmail) {
        return new Mail(oldEmail, "Пошту акаунта на Новелці змінено", """
                Привіт, %s!

                Пошту вашого акаунта змінено на %s. Якщо це були не ви, відновіть пароль
                через «Забули пароль?» на сторінці входу й напишіть адміністрації.
                """.formatted(nick, newEmail));
    }

    Mail passwordChanged(String email, String nick) {
        return new Mail(email, "Пароль на Новелці змінено", """
                Привіт, %s!

                Пароль вашого акаунта щойно змінено, а всі інші пристрої вийшли з акаунта.
                Якщо це були не ви, відновіть пароль через «Забули пароль?» на сторінці входу.
                """.formatted(nick));
    }

    private String link(String path, String token) {
        return site.link(path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }
}
