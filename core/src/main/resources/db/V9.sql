-- Last successful model list per AI provider. It is a cache and a fallback when the provider API is down.
CREATE TABLE model_catalogs (
    provider text PRIMARY KEY,
    models jsonb NOT NULL,
    refreshed_at timestamptz NOT NULL
)
