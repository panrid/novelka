-- Who hears about new chapters: readers who rang the bell on the translation's page, not
-- everyone who keeps it in the library. Readers who heard so far (the translation in «Читаю»
-- or «В планах») start subscribed, so nobody loses the news silently; the bell lets them go.
CREATE TABLE edition_subscription
(
    account_id BIGINT      NOT NULL REFERENCES account (id),
    edition_id BIGINT      NOT NULL REFERENCES edition (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, edition_id)
);

CREATE INDEX edition_subscription_by_edition ON edition_subscription (edition_id);

INSERT INTO edition_subscription (account_id, edition_id, created_at)
SELECT account_id, edition_id, updated_at
FROM library_entry
WHERE list IN ('reading', 'planned');
