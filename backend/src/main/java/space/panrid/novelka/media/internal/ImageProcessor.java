package space.panrid.novelka.media.internal;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.platform.web.UserFacingException;

/** Pure image work, no storage: decode → check → crop → resize → encode. */
final class ImageProcessor {

    record Variant(int width, int height, byte[] bytes) {
    }

    record Processed(String extension, String mime, int width, int height, List<Variant> variants) {
    }

    static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> FORMATS = Set.of("jpeg", "jpg", "png", "webp");
    private static final float JPEG_QUALITY = 0.85f;

    private ImageProcessor() {
    }

    static Processed process(byte[] content, ImageKind kind) {
        BufferedImage source = decode(content, kind);
        BufferedImage shaped = crop(source, kind.aspect());
        boolean alpha = shaped.getColorModel().hasAlpha();
        String format = alpha ? "png" : "jpeg";

        List<Variant> variants = new ArrayList<>();
        for (int width : kind.widths()) {
            // Never upscale: a size above the original becomes one copy at the original size.
            int targetWidth = Math.min(width, shaped.getWidth());
            if (!variants.isEmpty() && variants.getLast().width() >= targetWidth) {
                break;
            }
            int targetHeight = Math.max(1, Math.round((float) shaped.getHeight() * targetWidth / shaped.getWidth()));
            BufferedImage resized = resize(shaped, targetWidth, targetHeight, alpha);
            variants.add(new Variant(targetWidth, targetHeight, encode(resized, format)));
        }
        return new Processed(alpha ? "png" : "jpg", alpha ? "image/png" : "image/jpeg",
                shaped.getWidth(), shaped.getHeight(), variants);
    }

    private static BufferedImage decode(byte[] content, ImageKind kind) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw notAPicture();
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw notAPicture();
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // Checked before decoding: a tiny file can claim gigapixels and exhaust memory.
                if ((long) width * height > MAX_PIXELS) {
                    throw UserFacingException.badRequest("Картинка завелика: до 40 мегапікселів.");
                }
                if (Math.min(width, height) < kind.minSide()) {
                    throw UserFacingException.badRequest(
                            "Картинка замала: потрібно щонайменше %d пікселів з кожного боку.".formatted(kind.minSide()));
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException error) {
            if (error instanceof UserFacingException userFacing) {
                throw userFacing;
            }
            throw notAPicture();
        }
    }

    /** Centre crop to the kind's shape. The web app already crops; this guards the API. */
    static BufferedImage crop(BufferedImage image, double aspect) {
        if (aspect <= 0) {
            return image;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        double current = (double) width / height;
        if (Math.abs(current - aspect) < 0.01) {
            return image;
        }
        if (current > aspect) {
            int newWidth = (int) Math.round(height * aspect);
            return image.getSubimage((width - newWidth) / 2, 0, newWidth, height);
        }
        int newHeight = (int) Math.round(width / aspect);
        return image.getSubimage(0, (height - newHeight) / 2, width, newHeight);
    }

    /** Halves in steps first: one big bicubic jump from 4000 px to 96 px looks grainy. */
    static BufferedImage resize(BufferedImage source, int width, int height, boolean alpha) {
        BufferedImage current = source;
        int currentWidth = source.getWidth();
        int currentHeight = source.getHeight();
        while (currentWidth / 2 >= width && currentHeight / 2 >= height) {
            currentWidth /= 2;
            currentHeight /= 2;
            current = draw(current, currentWidth, currentHeight, alpha, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return draw(current, width, height, alpha, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage draw(BufferedImage source, int width, int height, boolean alpha, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (format.equals("jpeg")) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(JPEG_QUALITY);
                params.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException error) {
            throw new IllegalStateException("Could not encode " + format, error);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static UserFacingException notAPicture() {
        return UserFacingException.badRequest("Це не схоже на картинку. Підходять JPEG, PNG і WebP.");
    }
}
