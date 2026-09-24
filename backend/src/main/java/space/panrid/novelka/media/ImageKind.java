package space.panrid.novelka.media;

import java.util.List;

/**
 * What a picture is for decides its shape and sizes.
 *
 * @param aspect width / height to crop to, or 0 to keep the original shape
 * @param widths widths of the stored copies, smallest first
 */
public enum ImageKind {
    AVATAR("avatar", 1.0, 64, List.of(96, 256, 512)),
    GROUP_AVATAR("group_avatar", 1.0, 64, List.of(96, 256, 512)),
    COVER("cover", 2.0 / 3.0, 160, List.of(240, 480, 960)),
    ILLUSTRATION("illustration", 0, 200, List.of(640, 1280, 1920)),
    MESSAGE("message", 0, 32, List.of(640, 1280));

    private final String code;
    private final double aspect;
    private final int minSide;
    private final List<Integer> widths;

    ImageKind(String code, double aspect, int minSide, List<Integer> widths) {
        this.code = code;
        this.aspect = aspect;
        this.minSide = minSide;
        this.widths = widths;
    }

    public String code() {
        return code;
    }

    public double aspect() {
        return aspect;
    }

    public int minSide() {
        return minSide;
    }

    public List<Integer> widths() {
        return widths;
    }
}
