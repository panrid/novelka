-- Етап 20, крок 6 (doc/redesign/README.md): the site chat gets channels by topic, «Зараз на
-- сайті» with a way to stay unseen, and notifications are chosen per topic — on the site and
-- in Telegram — instead of four switches of the Telegram chat alone.

CREATE TABLE chat_channel
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug       TEXT        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9-]{1,40}$'), -- in the address: /inbox/chat?channel=…
    name       TEXT        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 40),
    about      TEXT        NOT NULL DEFAULT '',
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO chat_channel (slug, name, about, position)
VALUES ('general', 'загальний', 'Про все, що стосується читання й сайту', 1),
       ('translation', 'переклад-і-словники', 'Перекладачам: імена, терміни, як краще', 2),
       ('what-to-read', 'що-почитати', 'Порадьте новелу — або попросіть пораду', 3),
       ('site', 'сайт-і-пропозиції', 'Помилки й побажання до Новелки', 4);

-- Everything said before channels was said in «загальний».
ALTER TABLE chat_message ADD COLUMN channel_id BIGINT REFERENCES chat_channel (id);
UPDATE chat_message SET channel_id = (SELECT id FROM chat_channel WHERE slug = 'general');
ALTER TABLE chat_message ALTER COLUMN channel_id SET NOT NULL;
CREATE INDEX chat_message_by_channel ON chat_message (channel_id, id);

-- How far each person has read a channel: the number beside its name.
CREATE TABLE chat_channel_read
(
    account_id   BIGINT NOT NULL REFERENCES account (id),
    channel_id   BIGINT NOT NULL REFERENCES chat_channel (id),
    last_read_id BIGINT NOT NULL,
    PRIMARY KEY (account_id, channel_id)
);

-- People who would rather not be listed in «Зараз на сайті».
CREATE TABLE chat_unseen
(
    account_id BIGINT PRIMARY KEY REFERENCES account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Where each topic of notifications goes. No row: on the site and in Telegram (when tied).
CREATE TABLE notification_choice
(
    account_id  BIGINT  NOT NULL REFERENCES account (id),
    topic       TEXT    NOT NULL CHECK (topic IN ('chapters', 'replies', 'messages', 'edits', 'source', 'other')),
    on_site     BOOLEAN NOT NULL DEFAULT TRUE,
    in_telegram BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (account_id, topic)
);

-- What tied Telegram chats chose so far carries over topic by topic.
INSERT INTO notification_choice (account_id, topic, in_telegram)
SELECT l.account_id,
       t.topic,
       CASE t.topic
           WHEN 'chapters' THEN l.notify_chapters
           WHEN 'messages' THEN l.notify_messages
           WHEN 'source' THEN l.notify_source
           ELSE l.notify_inbox END
FROM telegram_link l
         CROSS JOIN (VALUES ('chapters'), ('replies'), ('messages'), ('edits'), ('source'), ('other')) AS t (topic)
WHERE NOT (l.notify_inbox AND l.notify_chapters AND l.notify_messages AND l.notify_source);

ALTER TABLE telegram_link
    DROP COLUMN notify_inbox,
    DROP COLUMN notify_chapters,
    DROP COLUMN notify_messages,
    DROP COLUMN notify_source;
