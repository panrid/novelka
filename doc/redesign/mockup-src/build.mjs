// Збирає файли полотна (.dc.html) у ../mockup/ і показ без полотна (preview) у вказану теку.
// node build.mjs [тека-для-показу]
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dcFile, previewHtml, KARKASY } from './lib.mjs';
import * as reader from './reader.mjs';
import * as novel from './novel.mjs';
import * as site from './site.mjs';
import * as comm from './comm.mjs';
import * as studio from './studio.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', 'mockup');
const prev = process.argv[2];
export const SCREENS = [reader.ReaderT, reader.ReaderPanelsT, reader.ReaderP, reader.ReaderK, reader.ReaderPanelsK, novel.NovelT, novel.NovelP, novel.NovelK,
  site.HomeT, site.HomeK, site.CatalogT, site.CatalogK, site.LibraryT, site.LibraryK, site.ProposalsT, site.ProposalsK,
  site.ProfileT, site.ProfileK, site.AuthT, site.AuthK, site.ErrorsT, site.ErrorsK, site.InfoK,
  comm.NotificationsT, comm.NotificationsK, comm.MessagesT, comm.MessagesK, comm.MeT, comm.SettingsT, comm.SettingsK,
  comm.MySuggestionsT, comm.MySuggestionsK, comm.PicturesT, comm.PicturesK, comm.ShahsT, comm.ShahsK,
  studio.StudioT, studio.StudioP, studio.StudioK, studio.RunsT, studio.RunsK, studio.LiveT, studio.LiveK, studio.NewT, studio.NewK,
  studio.TrT, studio.TrP, studio.TrK, studio.OriginalK, studio.TranslateP, studio.TranslateK, studio.AutoT, studio.AutoK];

for (const s of SCREENS) {
  writeFileSync(join(out, s.file), dcFile(s));
  if (prev) {
    mkdirSync(prev, { recursive: true });
    for (const [kname, kid] of KARKASY) for (const st of s.stany) {
      const name = `${s.file.replace('.dc.html', '')}__${kid}__${s.ids[s.stany.indexOf(st)]}.html`;
      writeFileSync(join(prev, name), previewHtml(s, { karkas: kname, tema: { pult: 'Сланець', zv: 'Графіт', pal: 'Сукно', obl: 'Слива' }[kid], stan: st }));
    }
  }
}
console.log('ok', SCREENS.length, 'screens');
