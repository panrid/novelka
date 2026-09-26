package space.panrid.novelka.source.internal;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import space.panrid.novelka.source.SourceLink;

/**
 * A novel on Syosetu: its code and whether it lives on the 18+ site.
 *
 * @param code  the novel's code, such as n0022gd
 * @param adult novel18.syosetu.com instead of ncode.syosetu.com
 */
record SyosetuLink(String code, boolean adult) {

    private static final Pattern CODE = Pattern.compile("n\\d{4}[a-z]{1,3}");
    private static final Pattern URL = Pattern.compile(
            "(?:https?://)?(ncode|novel18)\\.syosetu\\.com/(n\\d{4}[a-z]{1,3})(?:/.*)?");

    static Optional<SyosetuLink> parse(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (CODE.matcher(value).matches()) {
            return Optional.of(new SyosetuLink(value, false));
        }
        Matcher url = URL.matcher(value);
        if (url.matches()) {
            return Optional.of(new SyosetuLink(url.group(2), url.group(1).equals("novel18")));
        }
        return Optional.empty();
    }

    String url() {
        return "https://%s.syosetu.com/%s/".formatted(adult ? "novel18" : "ncode", code);
    }

    /** Key in novel.source_key: the 18+ site has its own codes. */
    String key() {
        return (adult ? "novel18:" : "ncode:") + code;
    }

    static SyosetuLink fromKey(String key) {
        int colon = key.indexOf(':');
        return new SyosetuLink(key.substring(colon + 1), key.startsWith("novel18:"));
    }

    SourceLink link() {
        return new SourceLink(SyosetuProvider.ID, key(), url(), adult);
    }
}
