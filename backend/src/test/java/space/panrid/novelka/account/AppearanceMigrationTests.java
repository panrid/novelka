package space.panrid.novelka.account;

import static org.assertj.core.api.Assertions.assertThat;
import static space.panrid.novelka.jooq.Tables.ACCOUNT;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import space.panrid.novelka.support.Accounts;
import space.panrid.novelka.support.IntegrationTest;
import space.panrid.novelka.support.TestMailbox;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * #292: the site's twelve styles became themes, and the каркас took over fonts, shapes and the menu.
 * Whoever had set up the look keeps their colours; the migration is run here again over old appearances.
 */
@IntegrationTest
class AppearanceMigrationTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    TestMailbox mailbox;

    @Autowired
    DSLContext db;

    @Autowired
    DataSource dataSource;

    @Test
    void stylesBecomeThemesAndOwnColoursStay() throws Exception {
        // Never chose anything but the size: the site's theme from now on.
        long sizeOnly = with("""
                {"site": {"preset": "default", "custom": true, "base": "dark", "bg": 6, "accent": "#8ea6ff",
                          "ui": "golos", "radius": 12, "scale": 1.1, "anim": "full"},
                 "reader": {"size": 20}}""");
        // «Звичайний» lightened with the sun / moon button.
        long lightDefault = with("""
                {"site": {"preset": "default", "base": "light", "bg": 6, "accent": "#2a4bd7"}}""");
        // A ready style, as stored by choosing it: every value written out.
        long parchment = with("""
                {"site": {"preset": "parchment", "custom": false, "base": "light", "bg": 3, "accent": "#7a5a2b",
                          "ui": "merriweather", "head": "merriweather", "radius": 6, "cards": "border", "icons": "iconoir",
                          "iconWeight": 1.5, "nav": "top", "catalog": "shelf", "portraits": "square"}}""");
        // A dark style turned light with the sun / moon: the same theme, light.
        long oceanLight = with("""
                {"site": {"preset": "ocean", "base": "light", "bg": 2, "accent": "#4fb3d9", "nav": "side"}}""");
        // Only the style's name stored.
        long neon = with("""
                {"site": {"preset": "neon"}}""");
        // «Свій стиль»: another accent on the ready one.
        long own = with("""
                {"site": {"preset": "forest", "custom": true, "base": "dark", "bg": 3, "accent": "#c2456b", "density": "airy"},
                 "siteOwn": {"preset": "forest", "custom": true, "base": "dark", "bg": 3, "accent": "#c2456b"}}""");
        // Trying a ready style while one's own waits aside.
        long trying = with("""
                {"site": {"preset": "terminal", "custom": false},
                 "siteOwn": {"preset": "default", "custom": true, "base": "dark", "bg": 2, "accent": "#ff3ea5"},
                 "readerOwn": {"colors": "paper"}}""");
        // Already in the new shape: untouched.
        long already = with("""
                {"site": {"frame": "obl", "theme": "slyva", "mode": "light"}}""");

        migrate();
        migrate();

        assertThat(appearanceOf(sizeOnly)).isEqualTo(JSON.readTree("""
                {"site": {"scale": 1.1}, "reader": {"size": 20}}"""));
        assertThat(appearanceOf(lightDefault)).isEqualTo(JSON.readTree("""
                {"site": {"mode": "light"}}"""));
        assertThat(appearanceOf(parchment)).isEqualTo(JSON.readTree("""
                {"site": {"theme": "parchment", "mode": "light", "catalog": "shelf", "portraits": "square"}}"""));
        assertThat(appearanceOf(oceanLight)).isEqualTo(JSON.readTree("""
                {"site": {"theme": "ocean", "mode": "light"}}"""));
        assertThat(appearanceOf(neon)).isEqualTo(JSON.readTree("""
                {"site": {"theme": "neon"}}"""));
        assertThat(appearanceOf(own)).isEqualTo(JSON.readTree("""
                {"site": {"theme": "own", "own": {"bg": 3, "accent": "#c2456b"}}}"""));
        assertThat(appearanceOf(trying)).isEqualTo(JSON.readTree("""
                {"site": {"theme": "terminal", "own": {"bg": 2, "accent": "#ff3ea5"}}, "readerOwn": {"colors": "paper"}}"""));
        assertThat(appearanceOf(already)).isEqualTo(JSON.readTree("""
                {"site": {"frame": "obl", "theme": "slyva", "mode": "light"}}"""));
    }

    private long with(String appearance) {
        String nick = Accounts.signedIn(port, mailbox).nick();
        long id = db.select(ACCOUNT.ID).from(ACCOUNT).where(ACCOUNT.NICK.eq(nick)).fetchSingle(ACCOUNT.ID);
        db.update(ACCOUNT).set(ACCOUNT.APPEARANCE, JSONB.valueOf(appearance)).where(ACCOUNT.ID.eq(id)).execute();
        return id;
    }

    private JsonNode appearanceOf(long id) {
        return JSON.readTree(db.select(ACCOUNT.APPEARANCE).from(ACCOUNT).where(ACCOUNT.ID.eq(id)).fetchSingle(ACCOUNT.APPEARANCE).data());
    }

    /** The migration's own file, whatever number it has. */
    private void migrate() throws Exception {
        Resource[] found = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__appearance_frame_theme.sql");
        assertThat(found).hasSize(1);
        String sql = found[0].getContentAsString(StandardCharsets.UTF_8);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
