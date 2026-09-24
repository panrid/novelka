package space.panrid.novelka.account.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import space.panrid.novelka.platform.web.UserFacingException;

class AccountRulesTests {

    @ParameterizedTest
    @ValueSource(strings = {"panrid", "Mika_2", "мавка", "Лисиця-9", "kitsune-team", "123abc"})
    void acceptsLatinOrCyrillicNicks(String nick) {
        assertThat(AccountRules.nick("  " + nick + " ")).isEqualTo(nick);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "_mika", "mi ka", "mika!", "раnrid", "admin", "Новелка", "ミカ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsBadNicks(String nick) {
        assertThatThrownBy(() -> AccountRules.nick(nick)).isInstanceOf(UserFacingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "a@b", "a b@c.d", "@example.com"})
    void rejectsBadEmails(String email) {
        assertThatThrownBy(() -> AccountRules.email(email)).isInstanceOf(UserFacingException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"короткий", "ґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґґ"}) // 37 two-byte letters = 74 bytes > 72
    void rejectsPasswordsBCryptCannotHandle(String password) {
        assertThatThrownBy(() -> AccountRules.password(password)).isInstanceOf(UserFacingException.class);
    }
}
