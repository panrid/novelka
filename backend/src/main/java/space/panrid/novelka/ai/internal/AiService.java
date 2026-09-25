package space.panrid.novelka.ai.internal;

import static space.panrid.novelka.jooq.Tables.AI_CALL;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import space.panrid.novelka.ai.Ai;
import space.panrid.novelka.ai.AiAnswer;
import space.panrid.novelka.ai.AiCredits;
import space.panrid.novelka.ai.AiException;
import space.panrid.novelka.ai.AiException.Kind;
import space.panrid.novelka.ai.AiImageRequest;
import space.panrid.novelka.ai.AiModel;
import space.panrid.novelka.ai.AiPicture;
import space.panrid.novelka.ai.AiRequest;
import space.panrid.novelka.ai.AiTransport;
import space.panrid.novelka.jooq.tables.records.AiCallRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The journal comes first: a {@code pending} row is committed before the request leaves, and
 * it becomes {@code complete}, {@code failed} (certainly unpaid or refused) or
 * {@code uncertain} (may be paid, the answer is lost). An identical request reuses a
 * complete answer; an identical request that is still pending or uncertain is refused
 * until the owner decides, so a lost answer is never paid for twice by accident.
 */
@Service
class AiService implements Ai {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);
    private static final Duration CREDITS_CACHE = Duration.ofMinutes(3);

    private final DSLContext db;
    private final AiTransport transport;
    private final JsonMapper json;
    private final Clock clock;
    private volatile Optional<AiCredits> credits = Optional.empty();
    private volatile Instant creditsAt = Instant.EPOCH;

    AiService(DSLContext db, AiTransport transport, JsonMapper json, Clock clock) {
        this.db = db;
        this.transport = transport;
        // Same request, same bytes, same hash: map keys are sorted so the order never depends on the run.
        this.json = json.rebuild().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
        this.clock = clock;
    }

    @Override
    public boolean configured() {
        return transport.configured();
    }

    @Override
    public AiAnswer ask(AiRequest request) {
        String body = json.writeValueAsString(body(request));
        String hash = sha256(body + "#" + request.attempt());
        AiCallRecord earlier = db.selectFrom(AI_CALL)
                .where(AI_CALL.REQUEST_HASH.eq(hash), AI_CALL.STATE.in("complete", "pending", "uncertain"))
                .orderBy(AI_CALL.ID.desc()).limit(1).fetchOne();
        if (earlier != null && earlier.getState().equals("complete")) {
            Parsed parsed = parse(earlier.getResponse().data());
            return new AiAnswer(earlier.getId(), parsed.content(), 0, true, parsed.cut());
        }
        if (earlier != null) {
            throw new AiException(Kind.UNCERTAIN, "Попередня відповідь на цей самий запит загубилася, і її могли вже оплатити.");
        }
        if (!transport.configured()) {
            throw new AiException(Kind.FAILED, "Ключ OpenRouter не налаштовано.");
        }
        long reserve = Math.round((body.getBytes(StandardCharsets.UTF_8).length * request.price().inputPerMillion()
                + request.maxTokens() * request.price().outputPerMillion()));
        long id = db.insertInto(AI_CALL)
                .set(AI_CALL.JOB_ID, request.tag().jobId())
                .set(AI_CALL.CHAPTER_NUMBER, request.tag().chapterNumber())
                .set(AI_CALL.STAGE, request.tag().stage())
                .set(AI_CALL.SEGMENT, request.tag().segment())
                .set(AI_CALL.MODEL, request.model())
                .set(AI_CALL.REQUEST_HASH, hash)
                .set(AI_CALL.REQUEST, JSONB.valueOf(body))
                .set(AI_CALL.COST_ESTIMATED_MUSD, reserve)
                .returning(AI_CALL.ID).fetchSingle().getId();

        AiTransport.Reply reply;
        try {
            reply = transport.chat(body);
        } catch (AiTransport.NotSent error) {
            finish(id, "failed", null, 0L, "не надіслано: " + error.getMessage());
            throw new AiException(Kind.UNPAID, "Не вдалося зʼєднатися з OpenRouter.");
        } catch (AiTransport.Lost error) {
            finish(id, "uncertain", null, null, "відповідь загубилася: " + error.getMessage());
            throw new AiException(Kind.UNCERTAIN, "Відповідь моделі загубилася дорогою. Її могли вже оплатити.");
        }
        return received(id, reply);
    }

    /** Any answer but 200 becomes a journal entry and an exception telling whether it may be paid. */
    private void requireSuccess(long id, AiTransport.Reply reply) {
        int status = reply.status();
        if (status == 429 || status == 502 || status == 503 || status == 408) {
            finish(id, "failed", reply.body(), 0L, "HTTP " + status);
            throw new AiException(Kind.UNPAID, status == 429 ? "Модель зараз перевантажена." : "Постачальник моделі тимчасово недоступний.");
        }
        if (status == 402) {
            finish(id, "failed", reply.body(), 0L, "HTTP 402");
            throw new AiException(Kind.FAILED, "На рахунку OpenRouter закінчилися кошти.");
        }
        if (status == 401 || status == 403) {
            finish(id, "failed", reply.body(), 0L, "HTTP " + status);
            throw new AiException(Kind.FAILED, "OpenRouter не приймає ключ.");
        }
        if (status == 404 && reply.body() != null && reply.body().contains("No endpoints found")) {
            finish(id, "failed", reply.body(), 0L, "HTTP 404");
            throw new AiException(Kind.FAILED, "Ця модель не підтримує потрібних параметрів запиту (структуровану відповідь). Оберіть іншу модель.");
        }
        if (status != 200) {
            finish(id, status >= 500 ? "uncertain" : "failed", reply.body(), status >= 500 ? null : 0L, "HTTP " + status);
            throw new AiException(status >= 500 ? Kind.UNCERTAIN : Kind.FAILED,
                    "OpenRouter відповів помилкою (%d).".formatted(status));
        }
    }

    private AiAnswer received(long id, AiTransport.Reply reply) {
        requireSuccess(id, reply);
        Parsed parsed;
        try {
            parsed = parse(reply.body());
        } catch (RuntimeException unreadable) {
            finish(id, "uncertain", null, null, "нечитабельна відповідь");
            throw new AiException(Kind.UNCERTAIN, "OpenRouter повернув нечитабельну відповідь. Її могли вже оплатити.");
        }
        if (parsed.error() != null) {
            finish(id, "failed", reply.body(), 0L, parsed.error());
            throw new AiException(Kind.UNPAID, "Постачальник моделі не впорався із запитом.");
        }
        JsonNode usage = json.readTree(reply.body()).path("usage");
        long cost = usage.has("cost") ? new BigDecimal(usage.path("cost").asString("0")).movePointRight(6).longValue() : -1;
        db.update(AI_CALL)
                .set(AI_CALL.STATE, "complete")
                .set(AI_CALL.RESPONSE, JSONB.valueOf(reply.body()))
                .set(AI_CALL.COST_ACTUAL_MUSD, cost < 0 ? null : cost)
                .set(AI_CALL.TOKENS_IN, usage.path("prompt_tokens").asInt(0))
                .set(AI_CALL.TOKENS_OUT, usage.path("completion_tokens").asInt(0))
                .set(AI_CALL.FINISHED_AT, now())
                .where(AI_CALL.ID.eq(id)).execute();
        return new AiAnswer(id, parsed.content(), Math.max(cost, 0), false, parsed.cut());
    }

    private record Parsed(String content, boolean cut, String error) {
    }

    @Override
    public AiPicture image(AiImageRequest request) {
        if (!transport.configured()) {
            throw new AiException(Kind.FAILED, "Ключ OpenRouter не налаштовано.");
        }
        Map<String, Object> request_ = new LinkedHashMap<>();
        request_.put("model", request.model());
        request_.put("messages", List.of(Map.of("role", "user", "content", request.prompt())));
        request_.put("modalities", List.of("image", "text"));
        request_.put("image_config", Map.of("aspect_ratio", request.aspectRatio()));
        request_.put("usage", Map.of("include", true));
        String body = json.writeValueAsString(request_);
        long id = db.insertInto(AI_CALL)
                .set(AI_CALL.JOB_ID, request.tag().jobId())
                .set(AI_CALL.CHAPTER_NUMBER, request.tag().chapterNumber())
                .set(AI_CALL.STAGE, request.tag().stage())
                .set(AI_CALL.MODEL, request.model())
                // A new picture every time: the hash never matches an earlier call.
                .set(AI_CALL.REQUEST_HASH, sha256(body + "#" + java.util.UUID.randomUUID()))
                .set(AI_CALL.REQUEST, JSONB.valueOf(body))
                .set(AI_CALL.COST_ESTIMATED_MUSD, request.estimateMicroUsd())
                .returning(AI_CALL.ID).fetchSingle().getId();
        AiTransport.Reply reply;
        try {
            reply = transport.chat(body);
        } catch (AiTransport.NotSent error) {
            finish(id, "failed", null, 0L, "не надіслано: " + error.getMessage());
            throw new AiException(Kind.UNPAID, "Не вдалося зʼєднатися з OpenRouter.");
        } catch (AiTransport.Lost error) {
            finish(id, "uncertain", null, null, "відповідь загубилася: " + error.getMessage());
            throw new AiException(Kind.UNCERTAIN, "Відповідь моделі загубилася дорогою. Її могли вже оплатити.");
        }
        requireSuccess(id, reply);
        JsonNode root;
        try {
            root = json.readTree(reply.body());
        } catch (RuntimeException unreadable) {
            finish(id, "uncertain", null, null, "нечитабельна відповідь");
            throw new AiException(Kind.UNCERTAIN, "OpenRouter повернув нечитабельну відповідь. Її могли вже оплатити.");
        }
        if (root.has("error")) {
            finish(id, "failed", reply.body(), 0L, root.path("error").path("message").asString("error"));
            throw new AiException(Kind.UNPAID, "Постачальник моделі не впорався із запитом.");
        }
        JsonNode usage = root.path("usage");
        long cost = usage.has("cost") ? new BigDecimal(usage.path("cost").asString("0")).movePointRight(6).longValue() : -1;
        String url = root.path("choices").path(0).path("message").path("images").path(0).path("image_url").path("url").asString("");
        java.util.regex.Matcher data = java.util.regex.Pattern.compile("^data:(image/[a-z+.-]+);base64,(.+)$", java.util.regex.Pattern.DOTALL)
                .matcher(url);
        byte[] content = null;
        String mime = null;
        if (data.matches()) {
            mime = data.group(1);
            content = java.util.Base64.getMimeDecoder().decode(data.group(2));
        }
        // The journal keeps the answer without the picture itself: megabytes of base64 help nobody there.
        String kept = reply.body().replace(url, content == null ? "" : "[%s, %d байтів]".formatted(mime, content.length));
        db.update(AI_CALL)
                .set(AI_CALL.STATE, "complete")
                .set(AI_CALL.RESPONSE, JSONB.valueOf(kept))
                .set(AI_CALL.COST_ACTUAL_MUSD, cost < 0 ? null : cost)
                .set(AI_CALL.TOKENS_IN, usage.path("prompt_tokens").asInt(0))
                .set(AI_CALL.TOKENS_OUT, usage.path("completion_tokens").asInt(0))
                .set(AI_CALL.ERROR, content == null ? "у відповіді немає картинки" : null)
                .set(AI_CALL.FINISHED_AT, now())
                .where(AI_CALL.ID.eq(id)).execute();
        if (content == null) {
            throw new AiException(Kind.FAILED, "Модель відповіла без картинки. Спробуйте змінити опис.");
        }
        return new AiPicture(id, content, mime, Math.max(cost, 0));
    }

    private Parsed parse(String body) {
        JsonNode root = json.readTree(body);
        if (root.has("error")) {
            return new Parsed(null, false, root.path("error").path("message").asString("error"));
        }
        JsonNode choice = root.path("choices").path(0);
        if (choice.has("error")) {
            return new Parsed(null, false, choice.path("error").path("message").asString("error"));
        }
        JsonNode content = choice.path("message").path("content");
        if (!content.isString()) {
            throw new IllegalStateException("no content");
        }
        return new Parsed(content.asString(), "length".equals(choice.path("finish_reason").asString("")), null);
    }

    private void finish(long id, String state, String response, Long cost, String error) {
        db.update(AI_CALL)
                .set(AI_CALL.STATE, state)
                .set(AI_CALL.RESPONSE, response == null ? null : jsonOrText(response))
                .set(AI_CALL.COST_ACTUAL_MUSD, cost)
                .set(AI_CALL.ERROR, error)
                .set(AI_CALL.FINISHED_AT, now())
                .where(AI_CALL.ID.eq(id)).execute();
        log.warn("AI call {} {}: {}", id, state, error);
    }

    private JSONB jsonOrText(String body) {
        try {
            json.readTree(body);
            return JSONB.valueOf(body);
        } catch (RuntimeException notJson) {
            return JSONB.valueOf(json.writeValueAsString(Map.of("text", body.length() > 2000 ? body.substring(0, 2000) : body)));
        }
    }

    private Map<String, Object> body(AiRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.system()),
                Map.of("role", "user", "content", request.user())));
        body.put("max_tokens", request.maxTokens());
        // Some models take no temperature; with require_parameters OpenRouter would find no endpoint for them.
        boolean temperature = models().stream().filter(model -> model.id().equals(request.model())).findFirst()
                .map(model -> model.accepts("temperature")).orElse(true);
        if (temperature) {
            body.put("temperature", 0.3);
        }
        body.put("usage", Map.of("include", true));
        if (request.schema() != null) {
            body.put("response_format", Map.of("type", "json_schema", "json_schema",
                    Map.of("name", request.schemaName(), "strict", true, "schema", request.schema())));
            body.put("provider", Map.of("require_parameters", true));
        }
        return body;
    }

    @Override
    public long spentMicroUsd(long jobId) {
        Long spent = db.select(DSL.sum(DSL.coalesce(AI_CALL.COST_ACTUAL_MUSD, AI_CALL.COST_ESTIMATED_MUSD)).cast(Long.class))
                .from(AI_CALL).where(AI_CALL.JOB_ID.eq(jobId)).fetchOne(0, Long.class);
        return spent == null ? 0 : spent;
    }

    @Override
    public void forgetUncertain(long jobId) {
        db.update(AI_CALL)
                .set(AI_CALL.STATE, "failed")
                .set(AI_CALL.ERROR, DSL.concat(DSL.coalesce(AI_CALL.ERROR, ""), DSL.inline("; власник дозволив нову спробу")))
                .where(AI_CALL.JOB_ID.eq(jobId), AI_CALL.STATE.in("pending", "uncertain"))
                .execute();
    }

    private volatile List<AiModel> models = List.of();
    private volatile Instant modelsAt = Instant.EPOCH;

    @Override
    public List<AiModel> models() {
        Instant now = clock.instant();
        if (!models.isEmpty() && modelsAt.plus(Duration.ofHours(1)).isAfter(now)) {
            return models;
        }
        try {
            AiTransport.Reply reply = transport.models();
            if (reply.status() == 200) {
                List<AiModel> fresh = new java.util.ArrayList<>();
                for (JsonNode model : json.readTree(reply.body()).path("data")) {
                    JsonNode pricing = model.path("pricing");
                    List<String> outputs = new java.util.ArrayList<>();
                    model.path("architecture").path("output_modalities").forEach(kind -> outputs.add(kind.asString()));
                    List<String> parameters = new java.util.ArrayList<>();
                    model.path("supported_parameters").forEach(parameter -> parameters.add(parameter.asString()));
                    fresh.add(new AiModel(model.path("id").asString(), model.path("name").asString(""),
                            perMillion(pricing.path("prompt")), perMillion(pricing.path("completion")),
                            model.path("context_length").asInt(0), outputs.isEmpty() ? List.of("text") : List.copyOf(outputs),
                            List.copyOf(parameters)));
                }
                fresh.sort(java.util.Comparator.comparing(AiModel::id));
                models = List.copyOf(fresh);
                modelsAt = now;
            }
        } catch (AiTransport.NotSent | AiTransport.Lost | RuntimeException error) {
            log.warn("OpenRouter models unavailable: {}", error.getMessage());
        }
        return models;
    }

    private static double perMillion(JsonNode price) {
        try {
            return new BigDecimal(price.asString("0")).movePointRight(6).doubleValue();
        } catch (NumberFormatException odd) {
            return 0;
        }
    }

    @Override
    public Optional<AiCredits> credits() {
        Instant now = clock.instant();
        if (creditsAt.plus(CREDITS_CACHE).isAfter(now)) {
            return credits;
        }
        Optional<AiCredits> fresh = Optional.empty();
        try {
            AiTransport.Reply reply = transport.credits();
            if (reply.status() == 200) {
                JsonNode data = json.readTree(reply.body()).path("data");
                fresh = Optional.of(new AiCredits(data.path("total_credits").decimalValue(), data.path("total_usage").decimalValue()));
            }
        } catch (AiTransport.NotSent | AiTransport.Lost | RuntimeException error) {
            log.warn("OpenRouter credits unavailable: {}", error.getMessage());
        }
        credits = fresh;
        creditsAt = now;
        return fresh;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
