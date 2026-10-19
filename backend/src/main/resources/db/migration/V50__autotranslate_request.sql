-- «Попросити доступ» to «Автопереклад із сайту» (етап 19): set while the site owner has not answered.
ALTER TABLE account ADD COLUMN autotranslate_requested_at TIMESTAMPTZ;
