-- «Студія в головному меню»: translators who work every day keep it one tap away.
ALTER TABLE account ADD COLUMN studio_in_menu BOOLEAN NOT NULL DEFAULT FALSE;
