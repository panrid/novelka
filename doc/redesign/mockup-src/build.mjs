// Збирає файли полотна (.dc.html) у ../mockup/ і показ без полотна (preview) у вказану теку.
// node build.mjs [тека-для-показу]
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dcFile, previewHtml, KARKASY } from './lib.mjs';
import * as reader from './reader.mjs';
import * as novel from './novel.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '..', 'mockup');
const prev = process.argv[2];
export const SCREENS = [reader.ReaderT, reader.ReaderPanelsT, reader.ReaderP, reader.ReaderK, reader.ReaderPanelsK, novel.NovelT, novel.NovelP, novel.NovelK];

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
