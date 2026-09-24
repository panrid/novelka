package space.panrid.novelka.media.internal;

import static space.panrid.novelka.jooq.Tables.IMAGE;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import space.panrid.novelka.media.ImageKind;
import space.panrid.novelka.media.Images;
import space.panrid.novelka.media.StoredImage;
import space.panrid.novelka.platform.web.RateLimiter;
import space.panrid.novelka.platform.web.UserFacingException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class ImageService implements Images {

    private final DSLContext db;
    private final MediaStorage storage;
    private final JsonMapper json;
    private final Clock clock;
    private final RateLimiter uploads;
    private final SecureRandom random = new SecureRandom();

    ImageService(DSLContext db, MediaStorage storage, JsonMapper json, Clock clock) {
        this.db = db;
        this.storage = storage;
        this.json = json;
        this.clock = clock;
        this.uploads = new RateLimiter(60, Duration.ofHours(1), clock);
    }

    @Override
    @Transactional
    public StoredImage store(long ownerAccountId, ImageKind kind, byte[] content) {
        if (!uploads.tryAcquire(Long.toString(ownerAccountId))) {
            throw UserFacingException.tooManyRequests();
        }
        ImageProcessor.Processed processed = ImageProcessor.process(content, kind);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String base = "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), randomName());

        Map<Integer, String> keys = new TreeMap<>();
        for (ImageProcessor.Variant variant : processed.variants()) {
            String key = base + "-" + variant.width() + "." + processed.extension();
            storage.write(key, variant.bytes());
            keys.put(variant.width(), key);
        }
        long id = db.insertInto(IMAGE)
                .set(IMAGE.OWNER_ACCOUNT_ID, ownerAccountId)
                .set(IMAGE.KIND, kind.code())
                .set(IMAGE.VARIANTS, JSONB.valueOf(json.writeValueAsString(keys)))
                .set(IMAGE.MIME, processed.mime())
                .set(IMAGE.WIDTH, processed.width())
                .set(IMAGE.HEIGHT, processed.height())
                .set(IMAGE.SHA256, sha256(content))
                .returning(IMAGE.ID)
                .fetchOne(IMAGE.ID);
        return new StoredImage(id, urls(keys));
    }

    @Override
    public Optional<StoredImage> find(long imageId) {
        return db.select(IMAGE.VARIANTS)
                .from(IMAGE)
                .where(IMAGE.ID.eq(imageId).and(IMAGE.HIDDEN_AT.isNull()))
                .fetchOptional(r -> new StoredImage(imageId, urls(json.readValue(r.value1().data(),
                        new TypeReference<Map<Integer, String>>() { }))));
    }

    @Override
    public Map<Long, StoredImage> findAll(Collection<Long> imageIds) {
        List<Long> ids = imageIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return db.select(IMAGE.ID, IMAGE.VARIANTS)
                .from(IMAGE)
                .where(IMAGE.ID.in(ids).and(IMAGE.HIDDEN_AT.isNull()))
                .fetchMap(r -> r.value1(), r -> new StoredImage(r.value1(), urls(json.readValue(r.value2().data(),
                        new TypeReference<Map<Integer, String>>() { }))));
    }

    private TreeMap<Integer, String> urls(Map<Integer, String> keys) {
        return keys.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> storage.url(entry.getValue()), (a, b) -> a, TreeMap::new));
    }

    private String randomName() {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
