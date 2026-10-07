-- Ready sets of models for autotranslation, each judged by a test translation (2026-10-07,
-- «Меджик Мейкер» chapters 21 and 40). For now Claude adds them; the owner only picks one.
-- The cost of each step is measured, not guessed from token prices: models that think first
-- spend several times what the prices alone promise, and the quote must not stop them.
CREATE TABLE autotranslate_preset
(
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    position                INT          NOT NULL,
    name                    TEXT         NOT NULL UNIQUE CHECK (char_length(name) BETWEEN 1 AND 60),
    summary                 TEXT         NOT NULL CHECK (char_length(summary) <= 300),
    rating                  NUMERIC(2, 1) NOT NULL CHECK (rating BETWEEN 1 AND 5),
    analyze_model           TEXT         NOT NULL,
    translate_model         TEXT         NOT NULL,
    -- NULL: no proofreading
    proofread_model         TEXT,
    -- What a step costs per 1000 characters of the original, in millionths of a dollar.
    analyze_musd_per_kchar   BIGINT       NOT NULL CHECK (analyze_musd_per_kchar >= 0),
    translate_musd_per_kchar BIGINT       NOT NULL CHECK (translate_musd_per_kchar >= 0),
    proofread_musd_per_kchar BIGINT       NOT NULL DEFAULT 0 CHECK (proofread_musd_per_kchar >= 0),
    active                  BOOLEAN      NOT NULL DEFAULT TRUE
);

INSERT INTO autotranslate_preset (position, name, summary, rating, analyze_model, translate_model, proofread_model,
                                  analyze_musd_per_kchar, translate_musd_per_kchar, proofread_musd_per_kchar)
VALUES (1, 'Копійка',
        'Найдешевше. Переклад живий, тире в репліках на місці, але бувають дрібні неточності. Повільно: близько 8 хвилин на главу.',
        3.5, 'deepseek/deepseek-v4-pro', 'deepseek/deepseek-v4-flash', NULL, 340, 900, 0),
       (2, 'Копійка+',
        'Як «Копійка», але словник складає Sonnet: імена й терміни точніші, менше зайвих записів.',
        3.5, 'anthropic/claude-sonnet-5.5', 'deepseek/deepseek-v4-flash', NULL, 4500, 900, 0),
       (3, 'Швидкий',
        'Grok перекладає швидко й точно, але не ставить тире, коли репліка продовжується в наступному абзаці, і часом спрощує.',
        3.5, 'deepseek/deepseek-v4-pro', 'x-ai/grok-4.3', NULL, 340, 8400, 0),
       (4, 'Швидкий з вичиткою',
        'Переклад Grok, вичитка DeepSeek додає пропущені тире й виправляє дрібниці.',
        4.0, 'deepseek/deepseek-v4-pro', 'x-ai/grok-4.3', 'deepseek/deepseek-v4-flash', 340, 8400, 1900),
       (5, 'Швидкий+',
        'Найкращий з дешевих: словник складає Sonnet, перекладає Grok, вичитує DeepSeek.',
        4.0, 'anthropic/claude-sonnet-5.5', 'x-ai/grok-4.3', 'deepseek/deepseek-v4-flash', 4500, 8400, 1900),
       (6, 'Економ',
        'Gemini 3.8 Flash: природна мова й правильні тире; іноді вільніше за оригінал.',
        4.0, 'deepseek/deepseek-v4-pro', 'google/gemini-3.8-flash', NULL, 340, 26000, 0),
       (7, 'Збалансований',
        'Sonnet усюди: найприродніша мова серед недорогих, точний словник; тире в продовженні репліки ставить не завжди.',
        4.0, 'anthropic/claude-sonnet-5.5', 'anthropic/claude-sonnet-5.5', NULL, 4500, 28000, 0),
       (8, 'Якісний',
        'Словник від Opus, переклад і вичитка Sonnet: мало помилок змісту, вичитка ловить неточності.',
        4.5, 'anthropic/claude-opus-5.5', 'anthropic/claude-sonnet-5.5', 'anthropic/claude-sonnet-5.5', 7800, 28000, 18000),
       (9, 'Максимум',
        'Opus на кожному кроці: найточніші переклад і словник, найменше правок. Найдорожче.',
        5.0, 'anthropic/claude-opus-5.5', 'anthropic/claude-opus-5.5', 'anthropic/claude-opus-5.5', 7800, 56000, 30000);
