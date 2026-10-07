-- Sign-in with Google: the Google account («sub») tied to ours, and whether the person
-- ever chose a password (an account made through Google has only a random one).
ALTER TABLE account
    ADD COLUMN google_sub   TEXT UNIQUE,
    ADD COLUMN password_set BOOLEAN NOT NULL DEFAULT TRUE;
