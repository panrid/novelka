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

    private String link(String path, String token) {
        return site.link(path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8));
    }
}
