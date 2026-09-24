package space.panrid.novelka.media.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.platform.web.UserFacingException;

class ImageProcessorTests {

    @Test
    void coversAreCroppedToTwoByThreeWithoutUpscaling() throws IOException {
        ImageProcessor.Processed result = ImageProcessor.process(encode(image(1000, 1000, false), "jpg"), ImageKind.COVER);

        assertThat(result.width()).isEqualTo(667);
        assertThat(result.height()).isEqualTo(1000);
        assertThat(result.variants()).extracting(ImageProcessor.Variant::width).containsExactly(240, 480, 667);
        BufferedImage largest = ImageIO.read(new ByteArrayInputStream(result.variants().getLast().bytes()));
        assertThat(largest.getHeight()).isEqualTo(1000);
    }

    @Test
    void transparentPicturesStayPng() throws IOException {
        ImageProcessor.Processed result = ImageProcessor.process(encode(image(300, 300, true), "png"), ImageKind.AVATAR);

        assertThat(result.mime()).isEqualTo("image/png");
        assertThat(result.extension()).isEqualTo("png");
    }

    @Test
    void tooSmallPicturesAreRefused() throws IOException {
        assertThatThrownBy(() -> ImageProcessor.process(encode(image(40, 40, false), "png"), ImageKind.AVATAR))
                .isInstanceOf(UserFacingException.class)
                .hasMessageContaining("Картинка замала");
    }

    @Test
    void aTinyFileClaimingGigapixelsIsRefusedBeforeDecoding() {
        assertThatThrownBy(() -> ImageProcessor.process(pngHeaderOnly(60_000, 60_000), ImageKind.ILLUSTRATION))
                .isInstanceOf(UserFacingException.class)
                .hasMessage("Картинка завелика: до 40 мегапікселів.");
    }

    private static BufferedImage image(int width, int height, boolean alpha) {
        return new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }

    /** A PNG signature and a header chunk declaring the given size, with no pixel data. */
    private static byte[] pngHeaderOnly(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 2)
                .put((byte) 0).put((byte) 0).put((byte) 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(type.getBytes());
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(type.getBytes());
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
