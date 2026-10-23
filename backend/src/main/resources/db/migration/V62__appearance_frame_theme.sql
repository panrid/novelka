-- Етап 20 (#292): каркас and тема instead of one site style. The twelve styles become themes (рішення 33,
-- 38); fonts, corners, density, cards, the menu and the icons now come with the каркас, so those choices
-- go. Nobody loses their colours:
--   * the style's own colours (its tone and accent, also after the sun / moon button) → that theme;
--     «Звичайний» (the colours of Графіт) → no theme, the site's default (Пульт · Сланець);
--   * colours of one's own («Свій стиль», also one kept aside in siteOwn) → «Свої кольори»: {bg, accent};
--   * a light base → mode «light»; size, catalog, portraits and animations stay («Повні» → «Як у системі»).
-- Only appearances in the old shape are touched, so running this again changes nothing.

-- The old styles' colours (frontend SITE_PRESETS over SITE_DEFAULTS): base, tone index, accent.
CREATE FUNCTION appearance_v62_colors(site JSONB, OUT style TEXT, OUT base TEXT, OUT bg INT, OUT accent TEXT, OUT own BOOLEAN)
    LANGUAGE plpgsql AS
$$
DECLARE
    preset RECORD;
BEGIN
    SELECT *
    INTO preset
    FROM (VALUES ('default', 'dark', 6, '#8ea6ff'),
                 ('light', 'light', 0, '#4f7a40'),
                 ('bookish', 'light', 2, '#2f4a6b'),
                 ('parchment', 'light', 3, '#7a5a2b'),
                 ('night', 'dark', 1, '#8ea6ff'),
                 ('contrast', 'dark', 1, '#ffd400'),
                 ('minimal', 'light', 1, '#111111'),
                 ('sakura', 'light', 4, '#c2456b'),
                 ('ocean', 'dark', 2, '#4fb3d9'),
                 ('forest', 'dark', 3, '#d8b45a'),
                 ('neon', 'dark', 4, '#ff3ea5'),
                 ('terminal', 'dark', 5, '#8cff9c')) AS styles(s_name, s_base, s_bg, s_accent)
    -- An unknown style showed as «Звичайний».
    WHERE s_name IN (coalesce(site ->> 'preset', 'default'), 'default')
    ORDER BY s_name = 'default'
    LIMIT 1;
    style := preset.s_name;
    base := CASE WHEN site ->> 'base' IN ('light', 'dark') THEN site ->> 'base' ELSE preset.s_base END;
    bg := CASE WHEN jsonb_typeof(site -> 'bg') = 'number' THEN (site ->> 'bg')::NUMERIC::INT ELSE preset.s_bg END;
    IF bg NOT BETWEEN 0 AND 6 THEN
        bg := 0;
    END IF;
    accent := lower(coalesce(site ->> 'accent', preset.s_accent));
    -- The sun / moon button kept the tone and the accent; only «Звичайний» swapped its accent for the light pair.
    own := NOT (bg = preset.s_bg AND (accent = preset.s_accent OR (preset.s_name = 'default' AND accent = '#2a4bd7')));
END
$$;

CREATE FUNCTION appearance_v62_site(appearance JSONB) RETURNS JSONB
    LANGUAGE plpgsql AS
$$
DECLARE
    old  JSONB := coalesce(appearance -> 'site', '{}');
    look RECORD;
    kept RECORD;
    site JSONB := '{}';
BEGIN
    SELECT * INTO look FROM appearance_v62_colors(old);
    IF look.own THEN
        site := site || jsonb_build_object('theme', 'own', 'own', jsonb_build_object('bg', look.bg, 'accent', look.accent));
    ELSIF look.style <> 'default' THEN
        site := site || jsonb_build_object('theme', look.style);
    END IF;
    IF look.base = 'light' THEN
        site := site || '{"mode": "light"}';
    END IF;
    IF NOT look.own AND jsonb_typeof(appearance -> 'siteOwn') = 'object' THEN
        SELECT * INTO kept FROM appearance_v62_colors(appearance -> 'siteOwn');
        IF kept.own THEN
            site := site || jsonb_build_object('own', jsonb_build_object('bg', kept.bg, 'accent', kept.accent));
        END IF;
    END IF;
    IF jsonb_typeof(old -> 'scale') = 'number' THEN
        site := site || jsonb_build_object('scale', old -> 'scale');
    END IF;
    IF old ->> 'catalog' IN ('rows', 'grid', 'shelf') THEN
        site := site || jsonb_build_object('catalog', old -> 'catalog');
    END IF;
    IF old ->> 'portraits' IN ('round', 'square') THEN
        site := site || jsonb_build_object('portraits', old -> 'portraits');
    END IF;
    IF old ->> 'anim' IN ('light', 'off') THEN
        site := site || jsonb_build_object('anim', old -> 'anim');
    END IF;
    RETURN site;
END
$$;

UPDATE account
SET appearance = CASE
                     WHEN appearance_v62_site(appearance) = '{}' THEN appearance - 'site' - 'siteOwn'
                     ELSE (appearance - 'site' - 'siteOwn') || jsonb_build_object('site', appearance_v62_site(appearance))
    END
WHERE jsonb_exists(appearance, 'siteOwn')
   OR jsonb_exists_any(appearance -> 'site', ARRAY ['preset', 'custom', 'base', 'bg', 'accent', 'ui', 'head', 'radius',
    'density', 'cards', 'icons', 'iconWeight', 'iconColor', 'iconFill', 'nav'])
   OR appearance -> 'site' ->> 'anim' = 'full';

DROP FUNCTION appearance_v62_site(JSONB);
DROP FUNCTION appearance_v62_colors(JSONB);
