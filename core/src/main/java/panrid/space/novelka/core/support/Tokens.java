package panrid.space.novelka.core.support;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

public final class Tokens {
    private static final Encoding ENCODING =
            Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE);

    public static int count(String text) {
        return ENCODING.countTokensOrdinary(text);
    }

    public static int source(java.util.List<panrid.space.novelka.core.model.Block> blocks) {
        return blocks.stream().mapToInt(b -> count(b.text())).sum();
    }
}
