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
    private final RemoteImages remote;
    private final JsonMapper json;
    private final Clock clock;
    private final RateLimiter uploads;
    private final SecureRandom random = new SecureRandom();

    ImageService(DSLContext db, MediaStorage storage, RemoteImages remote, JsonMapper json, Clock clock) {
        this.db = db;
        this.storage = storage;
        this.remote = remote;
        this.json = json;
        this.clock = clock;
        this.uploads = new RateLimiter(300, Duration.ofHours(1), clock);
    }

    @Override
    @Transactional
    public StoredImage store(long ownerAccountId, ImageKind kind, byte[] content) {
        if (content.length > RemoteImages.MAX_BYTES) {
            throw UserFacingException.badRequest("Картинка завелика: до 5 МБ.");
        }
        if (!uploads.tryAcquire(Long.toString(ownerAccountId))) {
            throw UserFacingException.tooManyRequests();
        }
        return save(ownerAccountId, kind, content);
    }

    @Override
    @Transactional
    public StoredImage store(long ownerAccountId, ImageKind kind, byte[] content, String fileName) {
        StoredImage image = store(ownerAccountId, kind, content);
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (!name.isEmpty()) {
            db.update(IMAGE).set(IMAGE.FILE_NAME, name.length() > 200 ? name.substring(0, 200) : name)
                    .where(IMAGE.ID.eq(image.id())).execute();
        }
        return image;
    }

    @Override
    public StoredImage storeDrawn(long ownerAccountId, byte[] content, String prompt, String fragment, long aiCallId) {
        StoredImage image = save(ownerAccountId, ImageKind.ILLUSTRATION, content);
        db.update(IMAGE).set(IMAGE.PROMPT, prompt).set(IMAGE.FRAGMENT, fragment).set(IMAGE.AI_CALL_ID, aiCallId)
                .where(IMAGE.ID.eq(image.id())).execute();
        return image;
    }

    @Override
    public void requireUsable(long imageId, long editionId, Long accountId) {
        var image = db.select(IMAGE.OWNER_ACCOUNT_ID, IMAGE.WORKSPACE_EDITION_ID, IMAGE.KIND)
                .from(IMAGE).where(IMAGE.ID.eq(imageId), IMAGE.HIDDEN_AT.isNull()).fetchOptional();
        if (image.isEmpty() || !java.util.Set.of("illustration", "cover").contains(image.get().value3())) {
            throw UserFacingException.notFound("Такої картинки немає.");
        }
        Long bound = image.get().value2();
        if (bound != null && bound != editionId) {
            throw UserFacingException.badRequest("Ця картинка належить іншому авторському простору. Завантажте окрему копію.");
        }
        Long only = tokenEdition();
        if (only != null) {
            var revision = space.panrid.novelka.jooq.Tables.REVISION;
            var chapter = space.panrid.novelka.jooq.Tables.CHAPTER;
            var edition = space.panrid.novelka.jooq.Tables.EDITION;
            boolean elsewhere = db.fetchExists(org.jooq.impl.DSL.selectOne().from(revision)
                    .join(chapter).on(chapter.ID.eq(revision.CHAPTER_ID)).join(edition).on(edition.ID.eq(chapter.EDITION_ID))
                    .where(edition.ID.ne(only), edition.VISIBILITY.eq("private"),
                            org.jooq.impl.DSL.condition("{0} @> {1}::jsonb", revision.BLOCKS,
                                    org.jooq.impl.DSL.val("[{\"imageId\":" + imageId + "}]"))));
            if (only != editionId || elsewhere) {
                throw UserFacingException.notFound("Ця картинка недоступна цьому токену.");
            }
        }
        boolean owner = java.util.Objects.equals(image.get().value1(), accountId);
        // Team editors may retain illustrations already in this edition, not claim an arbitrary id.
        boolean existing = db.fetchExists(org.jooq.impl.DSL.selectOne()
                .from(space.panrid.novelka.jooq.Tables.REVISION)
                .join(space.panrid.novelka.jooq.Tables.CHAPTER).on(space.panrid.novelka.jooq.Tables.CHAPTER.ID
                        .eq(space.panrid.novelka.jooq.Tables.REVISION.CHAPTER_ID))
                .where(space.panrid.novelka.jooq.Tables.CHAPTER.EDITION_ID.eq(editionId),
                        org.jooq.impl.DSL.condition("{0} @> {1}::jsonb", space.panrid.novelka.jooq.Tables.REVISION.BLOCKS,
                                org.jooq.impl.DSL.val("[{\"imageId\":" + imageId + "}]"))));
        if (!owner && !java.util.Objects.equals(bound, editionId) && !existing) {
            throw UserFacingException.notFound("Ця картинка вам недоступна.");
        }
    }

    private static Long tokenEdition() {
        if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                instanceof org.springframework.web.context.request.ServletRequestAttributes request) {
            return space.panrid.novelka.platform.security.TokenGrant.edition(request.getRequest());
        }
        return null;
    }

    @Override
    @Transactional
    public void bindWorkspace(long imageId, long editionId, long accountId) {
        requireUsable(imageId, editionId, accountId);
        int changed = db.update(IMAGE).set(IMAGE.RESTRICTED, true).set(IMAGE.WORKSPACE_EDITION_ID, editionId)
                .where(IMAGE.ID.eq(imageId), IMAGE.WORKSPACE_EDITION_ID.isNull().or(IMAGE.WORKSPACE_EDITION_ID.eq(editionId)))
                .execute();
        if (changed != 1) {
            throw UserFacingException.conflict("Картинку вже прикріпили до іншого авторського простору.");
        }
    }

    @Override
    public void hide(long imageId, long moderatorId, String reason) {
        db.update(IMAGE).set(IMAGE.HIDDEN_AT, org.jooq.impl.DSL.currentOffsetDateTime()).set(IMAGE.HIDDEN_BY, moderatorId)
                .set(IMAGE.HIDDEN_REASON, reason).where(IMAGE.ID.eq(imageId), IMAGE.HIDDEN_AT.isNull()).execute();
    }

    @Override
    public void restore(long imageId) {
        db.update(IMAGE).setNull(IMAGE.HIDDEN_AT).setNull(IMAGE.HIDDEN_BY).setNull(IMAGE.HIDDEN_REASON)
                .where(IMAGE.ID.eq(imageId)).execute();
    }

    private StoredImage save(long ownerAccountId, ImageKind kind, byte[] content) {
        ImageProcessor.Processed processed = ImageProcessor.process(content, kind);
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        String base = "%d/%02d/%s".formatted(today.getYear(), today.getMonthValue(), randomName());

        Map<Integer, String> keys = new TreeMap<>();
        for (ImageProcessor.Variant variant : processed.variants()) {
            String key = base + "-" + variant.width() + "." + processed.extension();
            storage.write(key, variant.bytes());
            keys.put(variant.width(), key);
        }
        String originalKey = base + "-original.bin";
        storage.write(originalKey, content);
        long id = db.insertInto(IMAGE)
                .set(IMAGE.OWNER_ACCOUNT_ID, ownerAccountId)
                .set(IMAGE.KIND, kind.code())
                .set(IMAGE.RESTRICTED, kind == ImageKind.ILLUSTRATION || kind == ImageKind.COVER)
                .set(IMAGE.VARIANTS, JSONB.valueOf(json.writeValueAsString(keys)))
                .set(IMAGE.ORIGINAL_KEY, originalKey)
                .set(IMAGE.MIME, processed.mime())
                .set(IMAGE.WIDTH, processed.width())
                .set(IMAGE.HEIGHT, processed.height())
                .set(IMAGE.SHA256, sha256(content))
                .returning(IMAGE.ID)
                .fetchOne(IMAGE.ID);
        // Every file's key leads back to its picture: /media/ checks private ones (етап 24.1).
        for (String key : java.util.stream.Stream.concat(keys.values().stream(), java.util.stream.Stream.of(originalKey)).toList()) {
            db.insertInto(space.panrid.novelka.jooq.Tables.IMAGE_FILE)
                    .set(space.panrid.novelka.jooq.Tables.IMAGE_FILE.KEY, key)
                    .set(space.panrid.novelka.jooq.Tables.IMAGE_FILE.IMAGE_ID, id)
                    .onConflictDoNothing().execute();
        }
        return new StoredImage(id, urls(keys));
    }

    @Override
    @Transactional
    public StoredImage storeFromUrl(long ownerAccountId, ImageKind kind, String url) {
        if (!uploads.tryAcquire(Long.toString(ownerAccountId))) {
            throw UserFacingException.tooManyRequests();
        }
        StoredImage image = store(ownerAccountId, kind, remote.fetch(url));
        db.update(IMAGE).set(IMAGE.SOURCE_URL, url).where(IMAGE.ID.eq(image.id())).execute();
        return image;
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
    public Optional<space.panrid.novelka.media.ImageFile> file(long imageId, int width) {
        return db.select(IMAGE.VARIANTS).from(IMAGE).where(IMAGE.ID.eq(imageId).and(IMAGE.HIDDEN_AT.isNull()))
                .fetchOptional(r -> new TreeMap<>(json.readValue(r.value1().data(), new TypeReference<Map<Integer, String>>() { })))
                .flatMap(keys -> {
                    String key = keys.tailMap(width).isEmpty() ? keys.get(keys.lastKey()) : keys.get(keys.tailMap(width).firstKey());
                    boolean png = key.endsWith(".png");
                    return storage.read(key).map(bytes -> new space.panrid.novelka.media.ImageFile(bytes,
                            png ? "image/png" : "image/jpeg", png ? "png" : "jpg"));
                });
    }

    @Override
    public Optional<byte[]> original(long imageId) {
        String key = db.select(IMAGE.ORIGINAL_KEY).from(IMAGE).where(IMAGE.ID.eq(imageId), IMAGE.HIDDEN_AT.isNull())
                .fetchOne(IMAGE.ORIGINAL_KEY);
        return key == null ? file(imageId, Integer.MAX_VALUE).map(space.panrid.novelka.media.ImageFile::content) : storage.read(key);
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
