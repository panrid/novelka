package space.panrid.novelka.source;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import space.panrid.novelka.platform.web.UserFacingException;

/**
 * A novel on Syosetu: its code and whether it lives on the 18+ site.
 *
 * @param code  the novel's code, such as n0022gd
 * @param adult novel18.syosetu.com instead of ncode.syosetu.com
 */
public record SyosetuLink(String code, boolean adult) {

    private static final Pattern CODE = Pattern.compile("n\\d{4}[a-z]{1,3}");
    private static final Pattern URL = Pattern.compile(
            "(?:https?://)?(ncode|novel18)\\.syosetu\\.com/(n\\d{4}[a-z]{1,3})(?:/.*)?");

    public static SyosetuLink parse(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (CODE.matcher(value).matches()) {
            return new SyosetuLink(value, false);
        }
        Matcher url = URL.matcher(value);
        if (url.matches()) {
            return new SyosetuLink(url.group(2), url.group(1).equals("novel18"));
        }
        throw UserFacingException.badRequest("Це не посилання на новелу з Syosetu. Скопіюйте адресу сторінки новели.");
    }

    public String url() {
        return "https://%s.syosetu.com/%s/".formatted(adult ? "novel18" : "ncode", code);
    }

    /** Key in novel.source_key: the 18+ site has its own codes. */
    public String key() {
        return (adult ? "novel18:" : "ncode:") + code;
    }

    public static SyosetuLink fromKey(String key) {
        int colon = key.indexOf(':');
        return new SyosetuLink(key.substring(colon + 1), key.startsWith("novel18:"));
    }
}
