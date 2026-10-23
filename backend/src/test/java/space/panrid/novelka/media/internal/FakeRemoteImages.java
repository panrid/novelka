package space.panrid.novelka.media.internal;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import space.panrid.novelka.platform.web.UserFacingException;
import space.panrid.novelka.support.Pictures;

/**
 * Pictures by link without the network: any {@code https://pictures.test/…} is a small PNG, and
 * {@code https://pictures.test/missing…} answers 404 as a real site would. Every other link goes
 * the real way, with its checks.
 */
public class FakeRemoteImages extends RemoteImages {

    public static final String HOST = "https://pictures.test/";

    public final List<String> requested = new CopyOnWriteArrayList<>();

    @Override
    byte[] fetch(String rawUrl) {
        if (rawUrl == null || !rawUrl.startsWith(HOST)) {
            return super.fetch(rawUrl);
        }
        requested.add(rawUrl);
        if (rawUrl.startsWith(HOST + "missing")) {
            throw UserFacingException.badRequest("За посиланням немає картинки (сайт відповів 404).");
        }
        return Pictures.png(320, 240, new Color(rawUrl.hashCode() & 0xffffff));
    }
}
