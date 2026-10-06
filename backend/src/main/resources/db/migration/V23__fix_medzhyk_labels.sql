-- «Меджик Мейкер»: the original title of chapter 22 («第二の魔法…», «the second magic») was taken for
-- «chapter 2», and after it every chapter without a number in its title was left unnumbered.
-- The novel numbers its chapters by order: those chapters get their positions back.
UPDATE chapter c
SET label = NULL, label_manual = FALSE
FROM edition e
         JOIN novel n ON n.id = e.novel_id
WHERE c.edition_id = e.id
  AND n.slug = 'medzhyk-meiker-yak-stvoriuvaty-mahiiu-v-inshomu-sviti'
  AND c.number >= 22
  AND c.label IN ('', '2');

UPDATE chapter_analysis a
SET label = NULL
FROM edition e
         JOIN novel n ON n.id = e.novel_id
WHERE a.edition_id = e.id
  AND n.slug = 'medzhyk-meiker-yak-stvoriuvaty-mahiiu-v-inshomu-sviti'
  AND a.number >= 22
  AND a.label IN ('', '2')
  AND NOT a.edited;
