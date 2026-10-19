-- «Найкраща · Висока · Швидка чернетка» (етап 19): which ready set of models people get at each
-- quality; the site owner changes it in «Налаштування сайту». A set without it is the owner's alone.
ALTER TABLE autotranslate_preset ADD COLUMN quality TEXT UNIQUE CHECK (quality IN ('best', 'high', 'draft'));
UPDATE autotranslate_preset SET quality = 'best' WHERE name = 'Максимум';
UPDATE autotranslate_preset SET quality = 'high' WHERE name = 'Якісний';
UPDATE autotranslate_preset SET quality = 'draft' WHERE name = 'Швидкий з вичиткою';

-- «Запам'ятати для наступних»: what a person picked for their runs, offered first next time.
CREATE TABLE autotranslate_choice
(
    account_id BIGINT PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    preset_id  BIGINT REFERENCES autotranslate_preset (id) ON DELETE SET NULL,
    steps      TEXT[]  NOT NULL,
    publish    BOOLEAN NOT NULL
);
