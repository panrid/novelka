// Студія, частина 1: шапка Студії, мої переклади, хід перекладу (усі запуски й наживо), новий
// переклад, переклад (огляд і глави з бічним меню), оригінал глави, «Перекласти з ШІ», автопереклад із сайту.
import { ic, iff, kk } from './lib.mjs';
import { NOVELS, ROWS } from './content.mjs';
import { CHROME_CSS } from './chrome.mjs';
import { READER_CSS } from './reader.mjs';
import { SITE_CSS } from './site.mjs';
import { COMM_CSS } from './comm.mjs';

export const STUDIO_CSS = `
.pager{display:flex;align-items:center;justify-content:space-between;font-size:13px;color:var(--dim);padding-top:8px}
.pager .row a{width:34px;height:34px;display:grid;place-items:center;border-radius:var(--rb);box-shadow:inset 0 0 0 1px var(--line);font-size:13px}
.pager .row a.on{background:var(--acc);color:var(--onacc);box-shadow:none}
.st-tag{height:22px;padding:0 8px;display:inline-flex;align-items:center;font:700 11px/1 var(--f-ui);letter-spacing:.08em;background:var(--s2);color:var(--acc);border-radius:var(--rc)}
.k-pult .st-tag{font-family:var(--f-num);font-weight:500;border-radius:0;background:var(--acc);color:var(--onacc)}
.k-pal .st-tag{background:none;font:500 16px/1 var(--f-h);font-variant:all-small-caps;letter-spacing:.12em;padding:0}
.k-obl .st-tag{background:var(--acc2);color:var(--onacc2);font-family:var(--f-h);font-size:10px}
.bal-c{display:inline-flex;align-items:center;gap:6px;height:30px;padding:0 10px;border-radius:var(--rc);box-shadow:inset 0 0 0 1px var(--line);font-size:13px;color:var(--dim);white-space:nowrap}
.bal-c b{color:var(--strong);font-variant-numeric:tabular-nums}
.k-pult .bal-c{border-radius:0;font-family:var(--f-num);font-size:12px}
/* шапка Студії: комп'ютер */
.sh-p .s-path .k{width:200px}
.st-head-z{height:64px;display:flex;align-items:center;gap:8px;padding:0 20px;border-bottom:1px solid var(--line)}
.st-head-z nav{display:flex;gap:4px;margin-left:18px}
.st-head-z nav a{height:38px;padding:0 12px;border-radius:10px;display:flex;align-items:center;gap:6px;font-size:14.5px;color:var(--dim)}
.st-head-z nav a.on{background:var(--s2);color:var(--strong);font-weight:600}
.st-head-z nav .n{font-size:12px;color:var(--acc);font-weight:700}
.st-head-z .sp{flex:1}
.st-mast{display:grid;grid-template-columns:1fr auto 1fr;align-items:center;padding:12px 28px;border-bottom:1px solid var(--line)}
.st-mast .c{display:flex;flex-direction:column;align-items:center;gap:6px}
.st-mast nav{display:flex;gap:22px}
.st-mast nav a{font-variant:all-small-caps;letter-spacing:.1em;font-size:16px;color:var(--dim)}
.st-mast nav a.on{color:var(--acc);border-bottom:1px solid var(--acc)}
.st-mast .l{display:flex;gap:14px;align-items:center;font-size:15px;color:var(--dim)}
.st-mast .r{display:flex;gap:6px;align-items:center;justify-content:flex-end}
.s-side .newb{margin:10px 0}
.s-side .bal-c{align-self:flex-start;margin-left:8px}
/* телефон */
.st-top{height:56px;flex:none;display:flex;align-items:center;gap:6px;padding:0 6px 0 4px;border-bottom:1px solid var(--line)}
.st-top .t{flex:1;min-width:0;font-weight:700;font-size:15px;color:var(--strong);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.k-pult .st-top{background:var(--s0);height:48px}
.k-pult .st-top .t{font:500 13px/1 var(--f-num);color:var(--tx)}
.k-pal .st-top .t{text-align:center;font:600 20px/1 var(--f-h)}
.k-obl .st-top{border:0;height:64px}
.k-obl .st-top .t{font:700 15px/1.2 var(--f-h)}
/* мої переклади */
.runc{display:grid;grid-template-columns:48px minmax(0,1fr) auto;gap:6px 14px;padding:14px;align-items:center}
.runc .cover{width:48px;height:68px;grid-row:1/4;font-size:0;padding:0}
.runc .cover b{display:none}
.runc b{color:var(--strong);font-size:15.5px}
.runc .btn{grid-column:3;grid-row:1/4}
.ph .runc .btn{grid-column:2;grid-row:auto;justify-self:start}
.k-pal .runc{padding:14px 0}
.att{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.dk .att,.tb .att{grid-template-columns:repeat(4,minmax(0,1fr))}
.at{display:flex;flex-direction:column;gap:4px;padding:12px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.at b{font:var(--wh) 24px/1 var(--f-num);color:var(--strong)}
.at span{font-size:12.5px;color:var(--dim);line-height:1.35}
.at .acc{font-weight:600;font-size:12.5px}
.k-pal .at{background:transparent;box-shadow:none;border-top:1px solid var(--line);border-radius:0;padding:10px 0}
.k-obl .at{box-shadow:none}
.k-obl .at b{color:var(--acc)}
.trc{display:grid;grid-template-columns:64px minmax(0,1fr);gap:14px;padding:14px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.trc .cover{width:64px;height:92px;font-size:0;padding:0}
.trc .cover b{display:none}
.trc b{font:var(--wh) 17px/1.25 var(--f-h);color:var(--strong)}
.trc .chips{display:flex;gap:6px;flex-wrap:wrap}
.trc .chip{height:24px;font-size:12px;padding:0 8px}
.k-pal .trc{background:transparent;box-shadow:none;border-bottom:1px dotted var(--line);border-radius:0;padding:14px 0}
.k-obl .trc{box-shadow:none;border-radius:24px}
.trs{display:grid;grid-template-columns:minmax(0,1fr);gap:10px}
.dk .trs{grid-template-columns:repeat(2,minmax(0,1fr))}
/* хід перекладу: усі запуски */
.rn{display:grid;grid-template-columns:minmax(0,1.6fr) minmax(0,1fr) 110px auto;gap:14px;align-items:center;padding:12px 0;border-bottom:1px solid var(--line);font-size:14px}
.rn b{color:var(--strong);font-size:14.5px}
.rn .m{font-size:12.5px;color:var(--faint)}
.rn .cost{font-variant-numeric:tabular-nums;color:var(--dim);font-size:13px;text-align:right}
.ph .rn{grid-template-columns:minmax(0,1fr) auto;gap:6px 10px}
.ph .rn .cost{display:none}
.k-pult .rn{font-family:var(--f-ui)}
.k-pult .rn .m,.k-pult .rn .cost{font-family:var(--f-num);font-size:11.5px}
.k-pal .rn{border-bottom:1px dotted var(--line)}
.k-obl .rn{border:0;background:var(--s1);border-radius:20px;padding:12px 14px;margin-bottom:6px}
.state.err i{background:var(--del)} .state.run i{background:var(--acc);box-shadow:0 0 0 3px color-mix(in srgb,var(--acc) 30%,transparent)}
/* хід наживо */
.lv-h{display:flex;align-items:center;gap:12px;flex-wrap:wrap}
.stages{display:flex;gap:0;align-items:center;flex-wrap:wrap}
.stg{display:flex;align-items:center;gap:6px;font-size:13.5px;color:var(--faint);padding:0 10px 0 0}
.stg::after{content:"";width:22px;height:1px;background:var(--line);margin-left:4px}
.stg:last-child::after{display:none}
.stg i{width:18px;height:18px;border-radius:50%;box-shadow:inset 0 0 0 1.5px var(--faint);display:grid;place-items:center}
.stg.done{color:var(--dim)} .stg.done i{background:var(--acc2);box-shadow:none;color:var(--onacc2)}
.stg.cur{color:var(--strong);font-weight:600} .stg.cur i{box-shadow:inset 0 0 0 5px var(--acc)}
.k-pult .stg i{border-radius:0}
.lv-g{display:grid;grid-template-columns:220px minmax(0,1fr) 300px;gap:24px;align-items:start;min-height:0}
.lv-ch{display:flex;flex-direction:column;gap:2px}
.lv-ch div{display:flex;align-items:center;gap:8px;padding:8px 10px;border-radius:var(--rb);font-size:14px;color:var(--dim)}
.lv-ch div.cur{background:var(--s2);color:var(--strong);font-weight:600}
.lv-ch .ok{color:var(--acc2)}
.lv-p{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:0 20px}
.lv-p>div{padding:10px 0;border-bottom:1px solid var(--line);font:15px/1.6 var(--f-rd)}
.lv-p .o{color:var(--dim);font-size:14px}
.lv-p .cur{background:color-mix(in srgb,var(--acc) 10%,transparent)}
.lv-p .pend{color:var(--faint)}
.lv-r{display:flex;flex-direction:column;gap:14px}
.lv-w{display:flex;flex-direction:column;gap:2px;padding:10px 12px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);font-size:14px}
.lv-w.new{box-shadow:inset 0 0 0 1.5px var(--acc2)}
.lv-w span{font-size:12.5px;color:var(--faint)}
.k-pal .lv-w{background:none;box-shadow:none;border-bottom:1px dotted var(--line);border-radius:0;padding:8px 0}
.vz{font-size:14px;line-height:1.5}
.vz del{color:var(--del);background:var(--delbg)} .vz ins{color:var(--ins);background:var(--insbg);text-decoration:none}
.banner{display:flex;gap:12px;align-items:flex-start;padding:14px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--warn);font-size:14px;line-height:1.5}
.banner .i{color:var(--warn)}
.log{display:grid;grid-template-columns:70px 120px minmax(0,1fr) 90px;gap:10px;padding:9px 0;border-bottom:1px solid var(--line);font-size:13.5px;align-items:center}
.log .s{font:12px 'IBM Plex Mono',monospace;color:var(--faint)}
/* новий переклад */
.ways{display:grid;grid-template-columns:minmax(0,1fr);gap:10px}
.dk .ways{grid-template-columns:repeat(2,minmax(0,1fr))}
.way{display:flex;flex-direction:column;gap:8px;padding:16px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.way.on{box-shadow:inset 0 0 0 2px var(--acc)}
.way b{font:var(--wh) 18px/1.25 var(--f-h);color:var(--strong)}
.way p{font-size:14px;line-height:1.5;color:var(--dim)}
.way .who{font-size:12.5px;color:var(--acc2);font-weight:600}
.way.lock{background:transparent}
.k-pal .way{background:transparent;box-shadow:none;border-top:1px solid var(--line);border-radius:0;padding:14px 0}
.k-pal .way.on{border-top:2px solid var(--acc)}
.k-obl .way{box-shadow:none;border-radius:24px}
.k-obl .way.on{box-shadow:0 0 0 2px var(--acc)}
.nw-g{display:flex;flex-direction:column;gap:22px}
.dk .nw-g{display:grid;grid-template-columns:minmax(0,1fr) 320px;gap:40px;align-items:start}
.next{display:flex;flex-direction:column;gap:10px;padding:16px}
.next ol{display:flex;flex-direction:column;gap:8px;counter-reset:n}
.next li{display:grid;grid-template-columns:24px 1fr;gap:8px;font-size:14px;line-height:1.45;counter-increment:n}
.next li::before{content:counter(n);width:22px;height:22px;border-radius:50%;background:var(--s2);display:grid;place-items:center;font-size:12px;font-weight:700;color:var(--acc)}
.k-pal .next{padding:16px 0}
.steps{display:flex;gap:8px;align-items:center;font-size:13.5px;color:var(--faint)}
.steps b{color:var(--strong)}
.found{display:grid;grid-template-columns:64px minmax(0,1fr);gap:14px;padding:14px;align-items:center}
.found .cover{width:64px;height:92px;font-size:0;padding:0}
.found .cover b{display:none}
/* переклад: бічне меню */
.tr-g{display:flex;flex-direction:column;gap:18px}
.dk .tr-g{display:grid;grid-template-columns:240px minmax(0,1fr);gap:36px;align-items:start}
.tb .tr-g{display:grid;grid-template-columns:200px minmax(0,1fr);gap:22px;align-items:start}
.trm{display:flex;flex-direction:column;gap:2px}
.trm a{display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:var(--rb);font-size:14.5px;color:var(--dim)}
.trm a .n{margin-left:auto;font-size:12px;font-weight:700;color:var(--acc)}
.trm a.on{background:var(--s2);color:var(--strong);font-weight:600}
.trm a.dis{opacity:.55}
.trm a small{margin-left:auto;font-size:11.5px;color:var(--faint)}
.trm .sep{height:1px;background:var(--line);margin:6px 0}
.k-pult .trm a{border-radius:0;font-size:14px}
.k-pult .trm a.on{box-shadow:inset 2px 0 0 var(--acc)}
.k-pal .trm a{font-variant:all-small-caps;letter-spacing:.06em;font-size:17px}
.k-pal .trm a.on{background:none;color:var(--acc)}
.k-obl .trm a.on{background:var(--acc);color:var(--onacc)}
.k-obl .trm a.on .n{color:var(--onacc)}
.ph .trm a{padding:14px 4px;border-bottom:1px solid var(--line);border-radius:0;font-size:15.5px}
.tr-h{display:grid;grid-template-columns:56px minmax(0,1fr) auto;gap:4px 14px;align-items:center}
.tr-h .cover{width:56px;height:80px;grid-row:1/3;font-size:0;padding:0}
.tr-h .cover b{display:none}
.tr-h .row{grid-column:3;grid-row:1/3}
.ph .tr-h{grid-template-columns:56px minmax(0,1fr)}
.ph .tr-h .row{grid-column:1/3;grid-row:auto}
.ct{display:grid;grid-template-columns:44px minmax(0,1fr) 130px 80px 110px auto;gap:12px;align-items:center;padding:10px 4px;border-bottom:1px solid var(--line);font-size:14.5px}
.ct.h{font-size:12px;color:var(--faint);padding:8px 4px}
.ct .n{color:var(--faint);font-variant-numeric:tabular-nums}
.ct .d{font-size:12.5px;color:var(--faint)}
.ct .p{font-size:12.5px;color:var(--acc);font-weight:600}
.tb .ct{grid-template-columns:36px minmax(0,1fr) 110px 70px auto}
.tb .ct .d{display:none}
.k-pult .ct{padding:8px 4px;font-size:14px}
.k-pult .ct .n,.k-pult .ct .d{font-family:var(--f-num);font-size:12px}
.k-pal .ct{border-bottom:1px dotted var(--line)}
.k-pal .ct .n{font:600 17px/1 var(--f-h)}
.k-obl .ct{border:0;background:var(--s1);border-radius:16px;margin-bottom:4px;padding:10px 12px}
.k-obl .ct.h{background:none}
.newm{right:40px;top:150px;width:360px;padding:8px}
.newm a{display:flex;gap:12px;align-items:flex-start;padding:10px;border-radius:var(--rb);font-size:14.5px}
.newm a:hover{background:var(--s2)}
.newm small{display:block;font-size:12.5px;color:var(--faint)}
/* оригінал */
.or-g{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:28px;align-items:start}
.or-ed{display:flex;flex-direction:column;gap:14px}
.or-ta{min-height:360px;padding:14px 16px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);font:15px/1.65 var(--f-rd);color:var(--tx);display:flex;flex-direction:column;gap:10px}
.k-pal .or-ta{background:transparent}
.ol{display:flex;flex-direction:column;gap:2px}
.ol a{display:grid;grid-template-columns:34px minmax(0,1fr) auto;gap:8px;align-items:center;padding:8px 10px;border-radius:var(--rb);font-size:14px}
.ol a.on{background:var(--s2);font-weight:600;color:var(--strong)}
.ol .n{color:var(--faint);font-variant-numeric:tabular-nums}
/* перекласти з ШІ */
.ai-g{display:flex;flex-direction:column;gap:20px}
.dk .ai-g,.tb .ai-g{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:28px;align-items:start}
.tb .ai-g{grid-template-columns:minmax(0,1fr) 300px}
.qc{display:grid;grid-template-columns:22px minmax(0,1fr) auto;gap:2px 10px;padding:12px;border-radius:var(--rin);box-shadow:inset 0 0 0 1px var(--line);align-items:center;font-size:14.5px}
.qc.on{box-shadow:inset 0 0 0 2px var(--acc);background:var(--s1)}
.qc small{grid-column:2;font-size:12px;color:var(--faint)}
.qc .pr{font-weight:700;color:var(--strong);font-variant-numeric:tabular-nums;font-size:14px;padding:0;border:0;display:block}
.k-pal .qc{box-shadow:none;border-bottom:1px dotted var(--line);border-radius:0}
.k-pal .qc.on{box-shadow:none;background:none;border-bottom:1px solid var(--acc)}
.mdl{display:grid;grid-template-columns:100px minmax(0,1fr);gap:8px;align-items:center;font-size:13.5px}
.mdl .field{height:34px;font-size:13px;color:var(--tx)}
.mdl .chg{font-size:11.5px;color:var(--acc);font-weight:700}
.sum{display:flex;align-items:baseline;justify-content:space-between;padding-top:6px;font-size:14px}
.sum b{font:var(--wh) 26px/1 var(--f-num);color:var(--strong)}
.cht{display:grid;grid-template-columns:24px 40px minmax(0,1fr) 90px 110px;gap:10px;align-items:center;padding:9px 4px;border-bottom:1px solid var(--line);font-size:14px}
.cht .n{color:var(--faint)}
.cht .z{font-size:12.5px;color:var(--faint);text-align:right;font-variant-numeric:tabular-nums}
.tb .cht{grid-template-columns:24px 34px minmax(0,1fr) 100px}
.tb .cht .z{display:none}
.k-pal .cht{border-bottom:1px dotted var(--line)}
.k-obl .cht{border:0;background:var(--s1);border-radius:14px;margin-bottom:4px;padding:9px 12px}
`;
const CSS = READER_CSS + CHROME_CSS + SITE_CSS + COMM_CSS + STUDIO_CSS;
const N = Object.fromEntries(NOVELS.map(n => [n.id, n]));
const cov = (n) => `<span class="cover ${n.c}"><b>${n.t}</b></span>`;
const themeBtn = `<button type="button" class="ib" onClick="{{ toggleLight }}" aria-label="Світла чи темна тема">${iff('dark', ic('sun', 19), true)}${iff('light', ic('moon', 19))}</button>`;
const bell = `<a href="NotificationsK.dc.html" class="ib" aria-label="Сповіщення, нових 3">${ic('bell', 20)}<span class="dot">3</span></a>`;

// ── Шапка Студії (рішення 18): «СТУДІЯ», Мої переклади · Хід перекладу · Команди · Кошик, баланс, «+ Новий переклад», «На сайт»
const SNAV = [['layers', 'Мої переклади', 'StudioK', ''], ['pulse', 'Хід перекладу', 'RunsK', '1'], ['users', 'Команди', 'TrMoreK', ''], ['trash', 'Кошик', 'TrMoreK', '']];
export function studioFrame({ active = 0, path = '', main }) {
  return `<div class="site">
${kk({
  pult: `<nav class="s-rail" aria-label="Студія"><span class="lg">н</span>${SNAV.map(([i, l, f, n], k) => `<a href="${f}.dc.html" class="${k === active ? 'on' : ''}" aria-label="${l}">${ic(i, 21)}${n ? `<span class="dot">${n}</span>` : ''}</a>`).join('')}<span class="sp"></span><a href="HomeK.dc.html" aria-label="На сайт">${ic('home', 20)}</a>${bell.replace('class="ib" ', '')}${themeBtn}<span class="av">М</span></nav><div class="s-path"><span class="st-tag">СТУДІЯ</span>${path}<span class="k" style="width:200px">${ic('search', 15)} Переклад, глава<kbd>⌘K</kbd></span><span class="bal-c"><b>86</b> ш</span><a href="NewK.dc.html" class="btn sm pri">${ic('plus', 15)} Новий переклад</a></div>`,
  zv: `<header class="st-head-z"><span class="lg-z">новелка<i>.</i></span><span class="st-tag">СТУДІЯ</span><nav>${SNAV.map(([i, l, f, n], k) => `<a href="${f}.dc.html" class="${k === active ? 'on' : ''}">${l}${n ? ` <span class="n">${n}</span>` : ''}</a>`).join('')}</nav><span class="sp"></span><span class="bal-c"><b>86</b> шагів</span><a href="NewK.dc.html" class="btn sm pri">${ic('plus', 16)} Новий переклад</a><a href="HomeK.dc.html" class="btn sm out">На сайт</a>${bell}${themeBtn}<span class="av">М</span></header>`,
  pal: `<header class="st-mast"><span class="l"><a href="HomeK.dc.html" class="row">${ic('back', 16)} На сайт</a></span><span class="c"><span class="row" style="gap:10px"><span class="lg-a">Новелка</span><span class="st-tag">студія</span></span><nav>${SNAV.map(([i, l, f, n], k) => `<a href="${f}.dc.html" class="${k === active ? 'on' : ''}">${l}${n ? ` · ${n}` : ''}</a>`).join('')}</nav></span><span class="r"><span class="bal-c"><b>86</b> шагів</span><a href="NewK.dc.html" class="btn sm pri">Новий переклад</a>${bell}${themeBtn}</span></header>`,
  obl: `<aside class="s-side"><span class="row" style="gap:8px;padding:0 8px 6px"><span class="lg-o" style="padding:0">новелка<i>.</i></span><span class="st-tag">СТУДІЯ</span></span><a href="NewK.dc.html" class="btn pri newb">${ic('plus', 18)} Новий переклад</a>${SNAV.map(([i, l, f, n], k) => `<a href="${f}.dc.html" class="nv-i ${k === active ? 'on' : ''}">${ic(i, 20)}<span>${l}</span>${n ? `<span class="n">${n}</span>` : ''}</a>`).join('')}<span class="bal-c" style="margin-top:12px"><b>86</b> шагів</span><span class="sp"></span><a href="HomeK.dc.html" class="nv-i">${ic('home', 20)}<span>На сайт</span></a><div class="me"><span class="av">М</span><span class="grow" style="font-weight:700;font-size:14px">marta_k</span>${bell}${themeBtn}</div></aside>`
})}
<main class="s-main">${main}</main>
</div>`;
}
// Телефон: окремий застосунок без нижнього меню; назад — до «Я» чи на попередній екран
export function studioPhone({ back = 'MeT.dc.html', title, actions = '', main }) {
  return `<div class="site"><header class="st-top"><a href="${back}" class="ib" aria-label="Назад">${ic('back', 20)}</a>${kk({ pult: `<span class="t">СТУДІЯ › ${title}</span>`, zv: `<span class="t">${title}</span>`, pal: `<span class="t">${title}</span>`, obl: `<span class="t">${title}</span>` })}<span class="bal-c"><b>86</b> ш</span>${actions}</header><main class="s-main">${main}</main></div>`;
}

// ── Мої переклади ──
const runCard = `<a href="LiveK.dc.html" class="card runc">${cov(N.n1)}<span class="lab">Перекладається зараз</span><b>Доглядач маяка · глави 41–45</b><span class="col" style="gap:6px"><span class="dim" style="font-size:13px">Переклад · глава 43, частина 3 з 7 · 52 %</span><span class="prog"><i style="width:52%"></i></span></span><span class="btn sm pri">Дивитися наживо</span></a>`;
const att = `<div class="att">${[['3', 'правки читачів чекають', 'ReviewK', 'Перевірити'], ['5', 'нових слів у словнику', 'GlossaryK', 'Погодити'], ['2', 'ваші чернетки', 'TrK', 'Відкрити'], ['2', 'нові глави в оригіналі', 'AutoK', 'Перекласти']].map(([n, t, f, a]) => `<a href="${f}.dc.html" class="at"><b>${n}</b><span>${t}</span><span class="acc">${a} ›</span></a>`).join('')}</div>`;
const trc = (n, line, pct, chips, st) => `<a href="TrK.dc.html" class="trc">${cov(n)}<span class="col" style="gap:6px;min-width:0"><b>${n.t}</b><span class="dim" style="font-size:13px">${line}</span><span class="prog"><i style="width:${pct}%"></i></span><span class="chips">${st ? `<span class="state ${st[0]}"><i></i>${st[1]}</span>` : ''}${chips.map(c => `<span class="chip">${c}</span>`).join('')}</span></span></a>`;
const trs = `<div class="trs">${trc(N.n1, '40 з 62 опубліковано · ШІ з правками', 64, ['чернетки 2', 'правки 3', 'нові слова 5'], ['run', 'перекладається'])}${trc(N.n4, '23 з 80 · команда «Солона вода»', 29, ['правки 1'], ['pub', 'в роботі'])}${trc(N.n5, '15 глав · свій твір', 100, [], ['dr', 'пишеться'])}</div>`;
const studioBody = (dev) => `<div class="page">
${iff('is.list', `${dev !== 'ph' ? '<div class="bar-act"><h1 class="h1">Мої переклади</h1><span class="field" style="max-width:300px">' + ic('search', 16) + ' Пошук перекладу</span></div>' : ''}${runCard}<section class="sec"><div class="sec-h"><h2 class="h2">Чекає на вас</h2></div>${att}</section><section class="sec"><div class="sec-h"><h2 class="h2">Переклади · 3</h2><a href="NewK.dc.html" class="more">+ новий</a></div>${trs}</section>`, true)}
${iff('is.empty', `<div class="err" style="padding-top:50px">${ic('layers', 42, 1.4)}<b>Тут будуть ваші переклади</b><p>Вставте оригінал глави — ШІ перекладе його зі словником новели. Або перекладайте самі, чи пишіть свій твір.</p><a href="NewK.dc.html" class="btn pri">${ic('plus', 17)} Новий переклад</a></div>`)}
${iff('is.load', `<span class="sk" style="height:96px;border-radius:var(--rcard)"></span><div class="att">${[1, 2, 3, 4].map(() => '<span class="sk" style="height:84px;border-radius:var(--rcard)"></span>').join('')}</div><span class="sk" style="height:120px;border-radius:var(--rcard)"></span>`)}
</div>`;
const SS = ['переклади', 'порожньо', 'заготовка'], SI = ['list', 'empty', 'load'];
export const StudioT = { file: 'StudioT.dc.html', title: 'Студія: мої переклади — телефон', w: 390, h: 844, dev: 'ph', stany: SS, ids: SI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ title: 'Мої переклади', actions: `<a href="NewT.dc.html" class="ib" aria-label="Новий переклад">${ic('plus', 21)}</a>`, main: studioBody('ph') })}</div>` };
export const StudioP = { file: 'StudioP.dc.html', title: 'Студія: мої переклади — планшет', w: 834, h: 1194, dev: 'tb', stany: SS, ids: SI, extra: CSS, root: `<div class="{{ cls }}" style="width:834px;height:1194px">${studioFrame({ active: 0, path: '<b>мої переклади</b>', main: studioBody('tb') })}</div>` };
export const StudioK = { file: 'StudioK.dc.html', title: 'Студія: мої переклади — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: SS, ids: SI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: '<b>мої переклади</b>', main: studioBody('dk') })}</div>` };

// ── Хід перекладу: усі запуски ──
const RUNS = [
  [N.n1, 'Глави 41–45 · переклад і вичитка', 'Висока · 2 хв тому', 52, ['run', 'іде · гл. 43'], '9 / 18 ш', ['Пауза', 'Скасувати']],
  [N.n4, 'Глава 24 · переклад', 'Найкраща · 20 хв тому', 30, ['err', 'зупинено: відповідь могла загубитися'], '2 / 6 ш', ['Продовжити']],
  [N.n2, 'Глави 71–72 · автопереклад із сайту', 'щотижня · учора', 100, ['pub', 'завершено · опубліковано'], '11 ш', ['Відкрити']],
  [N.n1, 'Глави 36–40 · лише словник', 'Швидка чернетка · 3 дні тому', 100, ['pub', 'завершено · 7 нових слів'], '2 ш', ['Словник']],
  [N.n4, 'Глави 20–23 · переклад', 'Висока · тиждень тому', 60, ['q', 'скасовано на гл. 22'], '9 ш', ['Відновити']]
];
const rn = ([n, t, m, p, st, c, acts]) => `<div class="rn"><span class="col" style="gap:3px;min-width:0"><b class="ell">${n.t}</b><span class="dim" style="font-size:13.5px">${t}</span><span class="m">${m}</span></span><span class="col" style="gap:6px"><span class="state ${st[0]}"><i></i>${st[1]}</span><span class="prog"><i style="width:${p}%"></i></span></span><span class="cost">${c}</span><span class="row" style="gap:6px">${acts.map((a, i) => `<a href="${a === 'Продовжити' || a === 'Відкрити' ? 'LiveK.dc.html' : '#'}" class="btn sm ${i === 0 && a !== 'Скасувати' ? 'out' : 'out'}">${a}</a>`).join('')}</span></div>`;
const runsBody = (dk) => `<div class="page"><div class="col" style="gap:12px">${dk ? '<h1 class="h1">Хід перекладу</h1>' : ''}
<div class="flt-row"><span class="chip on">Усі · 5</span><span class="chip">Ідуть · 1</span><span class="chip">Зупинені · 1</span><span class="chip">Завершені · 2</span><span class="chip">Скасовані · 1</span></div>
${dk ? '<div class="row"><div class="seg" style="flex:none"><span class="on">Будь-які</span><span>Переклад з ШІ</span><span>Автопереклад із сайту</span><span>Лише словник</span><span>Лише вичитка</span></div><span class="field" style="max-width:260px;margin-left:auto">' + ic('search', 16) + ' Назва новели</span></div>' : ''}
${iff('is.all', RUNS.map(rn).join(''), true)}
${iff('is.empty', `<div class="err" style="padding-top:40px">${ic('pulse', 40, 1.4)}<b>Запусків ще не було</b><p>Тут видно кожен переклад з ШІ: етапи, витрачені шаги, зупинки й повтори.</p></div>`)}
</div></div>`;
export const RunsT = { file: 'RunsT.dc.html', title: 'Хід перекладу: усі запуски — телефон', w: 390, h: 844, dev: 'ph', stany: ['усі', 'порожньо'], ids: ['all', 'empty'], extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'StudioT.dc.html', title: 'Хід перекладу', main: runsBody(false) })}</div>` };
export const RunsK = { file: 'RunsK.dc.html', title: 'Хід перекладу: усі запуски — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['усі', 'порожньо'], ids: ['all', 'empty'], extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 1, path: '<b>хід перекладу</b>', main: runsBody(true) })}</div>` };

// ── Хід перекладу наживо (рішення 17) ──
const stages = (cur) => `<div class="stages">${['Словник', 'Переклад', 'Вичитка', 'Готово'].map((t, i) => `<span class="stg ${i < cur ? 'done' : i === cur ? 'cur' : ''}"><i>${i < cur ? ic('check', 12, 2.4) : ''}</i>${t}</span>`).join('')}</div>`;
const live43 = [
  ['The keeper’s log lay open beside the lamp. The last entry was nine days old.', 'Журнал доглядача лежав розгорнутим біля лампи. Останній запис зробили дев’ять днів тому.', ''],
  ['“Wind from the north,” Lina read aloud. “Lens restless. Do not trust the fog.”', '— Вітер з півночі, — прочитала вголос Ліна. — Лінза неспокійна. Не вір туману.', ''],
  ['Orest took the book from her hands and closed it gently, as if it could wake.', 'Орест забрав у неї книгу й тихо закрив, ніби вона могла прокинутися<span class="caret"></span>', 'cur'],
  ['Outside, the bell rang twice.', '', 'pend']
];
const liveBody = (dk) => `<div class="page" style="gap:18px">
<div class="lv-h"><div class="col grow" style="gap:4px"><span class="lab">Хід перекладу · запуск з 2 хв тому</span><b class="h1" style="font-size:${dk ? 24 : 20}px">Доглядач маяка · глави 41–45</b></div>${iff('not.done', `<button type="button" class="btn out">Пауза</button><button type="button" class="btn out">Скасувати</button>`, true)}${iff('is.done', `<a href="WorkK.dc.html" class="btn">Відкрити в робочому місці</a><button type="button" class="btn pri">Опублікувати 5 глав</button>`)}</div>
${iff('is.run', stages(1), true)}${iff('is.done', stages(4))}${iff('is.err', stages(1))}${iff('is.log', stages(1))}
${iff('is.err', `<div class="banner">${ic('alert', 20)}<span class="col" style="gap:6px"><b class="strong">Запуск зупинено на главі 43</b><span class="dim">Відповідь моделі могла загубитися вже оплаченою. Щоб не заплатити двічі, ми зупинились — «Продовжити» надішле запит ще раз.</span><span class="row"><button type="button" class="btn sm pri">Продовжити</button><button type="button" class="btn sm out">Скасувати й повернути резерв</button></span></span></div>`)}
${iff('not.log', `<div class="${dk ? 'lv-g' : 'col'}" style="gap:18px">
${dk ? `<aside class="col" style="gap:12px"><div class="lv-ch">${[[41, 'ok'], [42, 'ok'], [43, 'cur'], [44, ''], [45, '']].map(([n, s]) => `<div class="${s === 'cur' ? 'cur' : ''}">${s === 'ok' ? `<span class="ok">${ic('check', 15)}</span>` : s === 'cur' ? ic('ring', 15) : '<span style="width:15px"></span>'}Глава ${n}</div>`).join('')}</div><span class="prog"><i style="width:52%"></i></span><span class="faint" style="font-size:12.5px">52 % · витрачено 9 з 18 шагів резерву</span></aside>` : '<div class="row" style="justify-content:space-between;font-size:13px" class="dim"><span class="dim">Глава 43 з 41–45 · частина 3 з 7</span><span class="dim">9 / 18 ш</span></div><span class="prog"><i style="width:52%"></i></span>'}
<div class="col" style="gap:8px"><div class="lv-p"><div class="lab" style="border:0">Оригінал · гл. 43</div><div class="lab" style="border:0">Переклад</div>${(dk ? live43 : live43.slice(1, 3)).map(([o, u, s]) => `<div class="o ${s === 'cur' ? 'cur' : ''}">${o}</div><div class="${s === 'cur' ? 'cur' : s === 'pend' ? 'pend' : ''}">${u || '…'}</div>`).join('')}</div></div>
<aside class="lv-r"><span class="lab">Словник глави</span><div class="lv-w">майстер Орест<span>Master Orest · персонаж</span></div><div class="lv-w new">журнал доглядача<span>the keeper’s log · нове · знайшов ШІ щойно</span></div>${dk ? '<div class="lv-w new">північний вітер<span>wind from the north · нове</span></div>' : ''}<span class="lab">Вичитка змінила · гл. 42</span><p class="vz">Ліна <del>подивилась</del> <ins>глянула</ins> на лінзу.</p><p class="vz">Туман <del>прийшов</del> <ins>підкрався</ins> з моря.</p></aside>
</div>`, true)}
${iff('is.log', `<div class="col"><div class="log" style="color:var(--faint);font-size:12px"><span>Глава</span><span>Крок</span><span>Що сталося</span><span>Шаги</span></div>${[['41', 'словник', '3 нові записи, 2 факти «Хто це»', '0,4'], ['41', 'переклад', '6 частин; частина 4 — повтор: рядки зсунулися', '1,2'], ['41', 'вичитка', 'змінено 4 абзаци · рід, кличний відмінок', '0,5'], ['41', 'публікація', 'чернетка · 2 картинки з оригіналу', '—'], ['42', 'переклад', '7 частин, без повторів', '1,3'], ['43', 'переклад', 'частина 3 з 7 іде', '…']].map(([g, k, t, c]) => `<div class="log"><span class="s">гл. ${g}</span><span>${k}</span><span>${t}</span><span class="s">${c}</span></div>`).join('')}</div>`)}
</div>`;
const LS = ['іде', 'готово', 'зупинено', 'подробиці'], LI = ['run', 'done', 'err', 'log'];
export const LiveT = { file: 'LiveT.dc.html', title: 'Хід перекладу наживо — телефон', w: 390, h: 844, dev: 'ph', stany: LS.slice(0, 3), ids: LI.slice(0, 3), extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'RunsT.dc.html', title: 'Наживо · гл. 41–45', main: liveBody(false) })}</div>` };
export const LiveK = { file: 'LiveK.dc.html', title: 'Хід перекладу наживо — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: LS, ids: LI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 1, path: 'хід перекладу › <b>Доглядач маяка · 41–45</b>', main: liveBody(true) })}</div>` };

// ── Новий переклад (рішення 19) ──
const WAYS = [
  ['spark', 'Вставлю оригінал — перекладе ШІ', 'Вставляєте главу оригіналу, ШІ складає словник, перекладає й вичитує. Ви правите й публікуєте.', 'для всіх · за шаги', 1],
  ['relay', 'Автопереклад із сайту', 'Посилання на новелу на сайті оригіналу — глави беруться самі, нові щотижня, «до глави N».', 'з дозволу власника сайту', 0, 1],
  ['pen', 'Перекладаю сам', 'Створюєте переклад і пишете глави в редакторі чи завантажуєте з файлу.', 'для всіх · безкоштовно'],
  ['book', 'Свій твір', 'Власна історія: «Автор @нік», без оригіналу й автоперекладу.', 'для всіх']
];
const wayCards = (asked) => `<div class="ways">${WAYS.map(([i, t, d, w, on, lock]) => `<div class="way ${on ? 'on' : ''} ${lock ? 'lock' : ''}"><span class="row" style="gap:10px"><span style="color:var(--acc)">${ic(lock ? 'lock' : i, 22)}</span><b>${t}</b></span><p>${d}</p><span class="who">${w}</span>${lock ? (asked ? `<span class="state q"><i></i>Запит надіслано · відповідь прийде у «Сповіщення»</span>` : `<button type="button" class="btn sm out" style="align-self:flex-start" onClick="{{ go.asked }}">Попросити доступ</button>`) : ''}</div>`).join('')}</div>`;
const nextBox = `<aside class="card next"><b class="h3">Що буде далі</b><ol><li>Розповісте про новелу: назва, автор, мова, теги.</li><li>Вставите оригінал першої глави.</li><li>«Перекласти з ШІ»: виберете якість — ціну видно до запуску.</li><li>Правите в робочому місці й публікуєте.</li></ol><span class="dim" style="font-size:13px">Глава ≈ 10 тис. знаків — від <b class="strong">[ціна]</b> шагів</span><a href="RunsK.dc.html" class="acc" style="font-size:13.5px">Продовжити покинутий переклад · 2 ›</a></aside>`;
const aboutForm = `<div class="form" style="max-width:none"><div class="steps"><b>1. Про новелу</b> › 2. Оригінал першої глави</div>
<div class="fm"><label>Назва українською</label><span class="field">Море скляних дзвонів</span></div>
<div class="fm"><label>Оригінальна назва й автор</label><span class="field">The Sea of Glass Bells · R. Ellery</span></div>
<div class="fm"><label>Мова оригіналу</label><div class="seg" style="max-width:420px"><span class="on">Англійська</span><span>Японська</span><span>Китайська</span><span>Корейська</span></div></div>
<div class="fm"><label>Теги</label><div class="row" style="gap:6px;flex-wrap:wrap"><span class="chip on">Фентезі ${ic('x', 13)}</span><span class="chip on">Море ${ic('x', 13)}</span><span class="chip">+ зі списку сайту</span></div></div>
<div class="row" style="gap:16px"><span class="upl" style="border-radius:var(--rin);width:84px;height:120px;align-self:auto">${ic('img', 22)}</span><span class="col" style="gap:8px"><b class="strong" style="font-size:14px">Обкладинка</b><span class="faint" style="font-size:12.5px">2:3, обріжете після завантаження</span><span class="row" style="gap:8px;font-size:14px"><span class="tog"></span>18+</span></span></div>
<span class="ck on" style="align-items:flex-start;font-size:14px"><i></i><span style="margin:0;color:var(--tx);font-size:14px">Я маю право перекладати цей текст і викладати переклад</span></span>
<div class="row"><a href="OriginalK.dc.html" class="btn pri">Далі — оригінал першої глави</a><button type="button" class="btn" onClick="{{ go.way }}">Назад</button></div></div>`;
const linkForm = `<div class="form" style="max-width:none"><div class="fm"><label>Посилання на новелу</label><span class="field">https://ncode.syosetu.com/n4027kx/</span><span class="ok-line">${ic('check', 16)} Знайдено: 62 глави, оригінал виходить</span></div>
<div class="card found">${`<span class="cover c3"><b>x</b></span>`}<span class="col" style="gap:4px"><b class="h3">Сто днів у вежі Аліти</b><span class="dim" style="font-size:13px">塔の百日 → перекладено · японська · 62 глави</span><span class="faint" style="font-size:12.5px">назву, автора й опис переклали самі — можна поправити</span></span></div>
<div class="row" style="gap:16px;flex-wrap:wrap"><div class="fm"><label>Перекладати до глави</label><span class="field" style="width:120px">10</span></div><div class="fm"><label>Якість</label><div class="seg"><span>Найкраща</span><span class="on">Висока</span><span>Швидка</span></div></div></div>
${'<div class="set-r" style="max-width:520px"><span>Нові глави оригіналу — щотижня<small>і дзвоник «нові глави в оригіналі»</small></span><span class="tog on"></span></div>'}
<span class="ck on" style="align-items:flex-start"><i></i><span style="margin:0;color:var(--tx);font-size:14px">Я маю право перекладати цей текст і викладати переклад</span></span>
<div class="sum" style="max-width:520px"><span class="dim">Кошторис · 10 глав, резерв із запасом</span><b>[ціна] ш</b></div>
<div class="row"><button type="button" class="btn pri">Створити й перекласти 10 глав</button></div></div>`;
const newBody = (dk) => `<div class="page"><div class="col" style="gap:6px">${dk ? '<h1 class="h1">Новий переклад</h1>' : ''}<p class="dim" style="font-size:14.5px">Як ви перекладатимете?</p></div><div class="nw-g"><div class="col" style="gap:16px">
${iff('is.way', wayCards(false), true)}${iff('is.asked', wayCards(true))}${iff('is.about', aboutForm)}${iff('is.link', linkForm)}</div>${dk ? nextBox : ''}</div></div>`;
const WS = ['спосіб', 'запит надіслано', 'про новелу', 'за посиланням (з дозволом)'], WI = ['way', 'asked', 'about', 'link'];
export const NewT = { file: 'NewT.dc.html', title: 'Новий переклад — телефон', w: 390, h: 844, dev: 'ph', stany: WS, ids: WI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'StudioT.dc.html', title: 'Новий переклад', main: newBody(false) })}</div>` };
export const NewK = { file: 'NewK.dc.html', title: 'Новий переклад — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: WS, ids: WI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'мої переклади › <b>новий переклад</b>', main: newBody(true) })}</div>` };

// ── Переклад: огляд і глави (рішення 25) ──
const TRM = [['TrK', 'cols', 'Огляд і глави', ''], ['OriginalK', 'book', 'Оригінал', ''], ['TranslateK', 'spark', 'Перекласти з ШІ', ''], ['AutoK', 'relay', 'Автопереклад із сайту', ''], ['GlossaryK', 'list', 'Словник', '5'], ['ReviewK', 'pen', 'Правки', '3'], ['sep'], ['TrMoreK', 'type', 'Назви глав', ''], ['TrMoreK', 'layers', 'Структура й томи', ''], ['TrMoreK', 'dl', 'Глави з файлу', ''], ['TrMoreK', 'relay', 'Естафета', ''], ['TrMoreK', 'gear', 'Дані й налаштування', '']];
export const trMenu = (on, phone = false) => `<nav class="trm" aria-label="Переклад">${TRM.map(([f, i, t, n], k) => f === 'sep' ? '<span class="sep"></span>' : `<a href="${phone ? f.replace(/K$/, 'T') : f}.dc.html" class="${k === on ? 'on' : ''}${phone && ['OriginalK', 'TranslateK', 'TrMoreK'].includes(f) ? ' dis' : ''}">${ic(i, 18)}${t}${n ? `<span class="n">${n}</span>` : ''}${phone && ['OriginalK', 'TranslateK', 'TrMoreK'].includes(f) ? '<small>на комп’ютері</small>' : ''}</a>`).join('')}</nav>`;
const trHead = (dk) => `<div class="tr-h">${cov(N.n1)}<b class="h1" style="font-size:${dk ? 26 : 21}px">Доглядач маяка</b><span class="dim" style="font-size:13.5px">40 з 62 опубліковано · в роботі · «Солона вода»</span><span class="row"><a href="TranslateK.dc.html" class="btn">${ic('spark', 16)} Перекласти з ШІ</a><button type="button" class="btn pri" onClick="{{ go.new }}">${ic('plus', 16)} Нова глава</button></span></div>`;
const CTS = [[46, '—', 'немає оригіналу', 'q', '', ''], [45, 'Морська сіль', 'перекладається', 'run', '', 'щойно'], [44, 'Пусті години', 'чернетка', 'dr', '', 'годину тому'], [43, 'Лампа без вогню', 'чернетка · ваша', 'dr', '2', 'учора'], [42, 'Північний вітер', 'опубліковано', 'pub', '1', '2 дні тому'], [41, 'Друга лампа', 'опубліковано', 'pub', '', '3 дні тому'], [40, 'Те, що пам’ятає море', 'опубліковано', 'pub', '', 'тиждень тому']];
const chTable = `<div class="col"><div class="row" style="justify-content:space-between;flex-wrap:wrap;gap:8px"><div class="flt-row"><span class="chip on">Усі · 46</span><span class="chip">З правками · 2</span><span class="chip">Мої чернетки · 2</span><span class="chip">Не опубліковані · 6</span></div><span class="row" style="gap:8px"><button type="button" class="btn sm out">${ic('sort', 15)} Від останньої</button><a href="TrMoreK.dc.html" class="btn sm out">${ic('trash', 15)} Кошик · 2</a></span></div>
<div class="ct h"><span>№</span><span>Назва</span><span>Стан</span><span>Правки</span><span>Оновлено</span><span></span></div>
${CTS.map(([n, t, s, k, p, d]) => `<div class="ct"><span class="n">${n}</span><span class="ell">${t}</span><span class="state ${k}"><i></i>${s}</span><span class="p">${p ? p + ' правки' : ''}</span><span class="d">${d}</span><a href="WorkK.dc.html" class="btn sm out">Редагувати</a></div>`).join('')}
<div class="pager"><span>сторінка 1 з 3 · глави 46–27</span><span class="row" style="gap:4px"><a href="#" class="on">1</a><a href="#">2</a><a href="#">3</a></span></div></div>`;
const newMenu = `<div class="pop newm" role="menu"><a href="TranslateK.dc.html">${ic('spark', 20)}<span>Перекласти з ШІ наступні<small>46–50 · оригінал є для 46 · ≈ [ціна] шагів</small></span></a><a href="WorkK.dc.html">${ic('pen', 20)}<span>Написати чи вставити вручну<small>у редакторі, з форматуванням і картинками</small></span></a><a href="TrMoreK.dc.html">${ic('dl', 20)}<span>З файлу .txt чи .md<small>одна глава чи кілька — перевірите розбиття</small></span></a></div>`;
const trBody = (dev) => `<div class="page"><div class="tr-g">${dev === 'ph' ? '' : trMenu(0)}<div class="col" style="gap:20px;min-width:0">${trHead(dev !== 'ph')}
${iff('not.load', `<div class="att">${[['52 %', 'іде запуск 41–45', 'LiveK', 'Наживо'], ['3', 'правки читачів', 'ReviewK', 'Перевірити'], ['5', 'нових слів', 'GlossaryK', 'Погодити'], ['61–62', 'нові глави в оригіналі', 'AutoK', 'Перекласти']].map(([n, t, f, a]) => `<a href="${f}.dc.html" class="at"><b>${n}</b><span>${t}</span><span class="acc">${a} ›</span></a>`).join('')}</div>${dev === 'ph' ? trMenu(0, true).replace(' class="on"', '') : chTable}`, true)}
${iff('is.load', `<div class="att">${[1, 2, 3, 4].map(() => '<span class="sk" style="height:84px;border-radius:var(--rcard)"></span>').join('')}</div>${[1, 2, 3, 4, 5].map(() => '<span class="sk" style="height:18px"></span>').join('')}`)}
</div></div>${iff('is.new', newMenu)}</div>`;
const TS = ['огляд', 'нова глава', 'заготовка'], TI = ['ov', 'new', 'load'];
export const TrT = { file: 'TrT.dc.html', title: 'Переклад: огляд — телефон', w: 390, h: 844, dev: 'ph', stany: ['огляд', 'заготовка'], ids: ['ov', 'load'], extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'StudioT.dc.html', title: 'Доглядач маяка', main: trBody('ph') })}</div>` };
export const TrP = { file: 'TrP.dc.html', title: 'Переклад: огляд і глави — планшет', w: 834, h: 1194, dev: 'tb', stany: TS, ids: TI, extra: CSS, root: `<div class="{{ cls }}" style="width:834px;height:1194px;position:relative">${studioFrame({ active: 0, path: 'мої переклади › <b>Доглядач маяка</b>', main: trBody('tb') })}</div>` };
export const TrK = { file: 'TrK.dc.html', title: 'Переклад: огляд і глави — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: TS, ids: TI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'мої переклади › <b>Доглядач маяка</b>', main: trBody('dk') })}</div>` };

// ── Оригінал глави (рішення 8) ──
const OL = [[44, 'Пусті години', 'перекладено'], [45, 'Морська сіль', 'перекладається'], [46, 'The Second Bell', 'не перекладено', 1], [47, '—', 'немає оригіналу'], [48, '—', 'немає оригіналу']];
const origBody = `<div class="page"><div class="tr-g">${trMenu(1)}<div class="col" style="gap:16px;min-width:0"><div class="bar-act"><h1 class="h1">Оригінал глави</h1><span class="row"><button type="button" class="btn sm out">Перевести глави на іншу мову</button><button type="button" class="btn sm out">${ic('trash', 15)} Видалити оригінали</button></span></div>
${iff('is.ch', `<div class="or-g"><div class="or-ed"><div class="row" style="gap:12px;flex-wrap:wrap"><div class="fm"><label>Мова</label><div class="seg"><span class="on">Англійська</span><span>Японська</span><span>Китайська</span><span>Корейська</span></div></div><div class="fm"><label>Номер</label><span class="field" style="width:90px">46</span></div></div>
<div class="fm"><label>Заголовок</label><span class="field">Chapter 46: The Second Bell</span></div>
<div class="fm"><label>Текст</label><div class="or-ta"><p>The bell rang a second time before dawn, and this time Lina was already awake.</p><p>She found Orest on the gallery, the log open on his knees, a pencil behind his ear.</p><p>“It’s not the harbour bell,” he said without looking up. “The harbour bell has a crack in it. This one doesn’t.”</p><p class="faint">2 340 знаків · 18 абзаців</p></div><small>Оригінал бачить лише команда — ніколи читачі й пошукові системи.</small></div>
<div class="row"><button type="button" class="btn">Зберегти</button><a href="TranslateK.dc.html" class="btn pri">${ic('spark', 16)} Зберегти й перекласти</a><button type="button" class="btn out">Зберегти й до наступної</button></div></div>
<aside class="card" style="padding:12px;display:flex;flex-direction:column;gap:8px"><span class="lab">Глави</span><div class="ol">${OL.map(([n, t, s, on]) => `<a href="#" class="${on ? 'on' : ''}"><span class="n">${n}</span><span class="col"><span class="ell">${t}</span><span class="faint" style="font-size:12px;font-weight:400">${s}</span></span>${on ? ic('pen', 15) : ''}</a>`).join('')}</div><button type="button" class="btn sm out">${ic('plus', 15)} Нова глава</button></aside></div>`, true)}
${iff('is.empty', `<div class="err" style="padding-top:30px">${ic('book', 40, 1.4)}<b>Оригіналу ще немає</b><p>Вставте главу мовою автора: окремо заголовок і текст. ШІ перекладе її зі словником новели.</p><button type="button" class="btn pri">Вставити першу главу</button></div>`)}
</div></div></div>`;
export const OriginalK = { file: 'OriginalK.dc.html', title: 'Оригінал глави — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['глава', 'порожньо'], ids: ['ch', 'empty'], extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'Доглядач маяка › <b>оригінал</b>', main: origBody })}</div>` };

// ── Перекласти з ШІ (рішення 21, 22) ──
const CHT = [[46, 'The Second Bell', '2 340', 1], [47, 'Salt and Iron', '3 120', 1], [48, 'The Ferryman’s Debt', '2 880', 1], [49, 'Low Tide', '2 410', 1], [50, 'Lamp Oil', '1 960', 1], [51, '—', 'немає оригіналу', 0]];
const quality = (custom) => `<div class="col" style="gap:8px"><span class="lab">Якість</span>
${[['Найкраща', 'Claude Opus · вичитка Claude Sonnet', '[ціна]'], ['Висока', 'Claude Sonnet · вичитка Haiku', '[ціна]', !custom], ['Швидка чернетка', 'Haiku · без вичитки', '[ціна]'], ['Мій набір «Острови»', 'мій · збережено 12 жовтня', '[ціна]']].map(([t, m, p, on]) => `<div class="qc ${on ? 'on' : ''}"><span class="rad ${on ? 'on' : ''}"></span><span>${t}</span><span class="pr">${p} ш/гл</span><small>${m}</small></div>`).join('')}
${custom ? `<div class="qc on" style="grid-template-columns:22px minmax(0,1fr) auto"><span class="rad on"></span><span>Свій · змінено з «Висока» <span class="chg acc">змінено</span></span><span class="pr">[ціна] ш/гл</span><div style="grid-column:2/4;display:flex;flex-direction:column;gap:6px;margin-top:8px">${[['Словник', 'Claude Haiku'], ['Переклад', 'Claude Opus', 1], ['Вичитка', 'Claude Sonnet']].map(([s, m, ch]) => `<div class="mdl"><span>${s}</span><span class="field">${m}${ch ? ' <span class="chg">змінено</span>' : ''}</span></div>`).join('')}<button type="button" class="btn sm out" style="align-self:flex-start">Зберегти як мій набір</button></div></div>` : ''}</div>`;
const aiSide = (custom, low) => `<aside class="card" style="padding:16px;display:flex;flex-direction:column;gap:16px">${quality(custom)}
<div class="col"><span class="lab">Етапи</span>${[['Словник', 'імена й терміни, факти «Хто це»', 1], ['Переклад', '', 1], ['Вичитка', 'перечитування ШІ', 1], ['Одразу опублікувати', 'інакше — ваші чернетки', 0], ['Зробити заново', 'перекласти й готові глави', 0], ['Запам’ятати для наступних', 'якість і етапи', 1]].map(([t, s, on]) => `<div class="set-r" style="padding:8px 0"><span>${t}${s ? `<small>${s}</small>` : ''}</span>${`<span class="tog ${on ? 'on' : ''}"></span>`}</div>`).join('')}</div>
<div class="sum"><span class="dim">Разом · 5 глав</span><b>[ціна] ш</b></div>
${low ? `<div class="banner" style="box-shadow:inset 0 0 0 1px var(--del)">${ic('alert', 18)}<span class="col" style="gap:4px"><b class="strong">Не вистачає шагів</b><span class="dim" style="font-size:13px">На рахунку 86, потрібно [ціна] разом із запасом. Виберіть менше глав або швидшу якість.</span></span></div><button type="button" class="btn pri wide dis" aria-disabled="true">Перекласти 5 глав</button>` : '<a href="LiveK.dc.html" class="btn pri wide">Перекласти 5 глав</a><span class="faint" style="font-size:12px">Резервується кошторис із запасом; після — списується фактичне, решта повертається.</span>'}</aside>`;
const aiBody = (dev) => `<div class="page"><div class="tr-g">${dev === 'dk' ? trMenu(2) : ''}<div class="col" style="gap:16px;min-width:0"><div class="bar-act"><h1 class="h1">Перекласти з ШІ</h1><a href="LiveK.dc.html" class="btn sm out">${ic('pulse', 15)} Зараз іде 41–45 · Дивитися наживо</a></div>
<div class="ai-g"><div class="col"><div class="row" style="justify-content:space-between"><span class="dim" style="font-size:13.5px">Від першої неперекладеної: <b class="strong">з 46 по 50</b></span><span class="row" style="gap:6px"><span class="field" style="width:70px;height:34px">46</span>–<span class="field" style="width:70px;height:34px">50</span></span></div>
<div class="cht" style="font-size:12px;color:var(--faint)"><span></span><span>№</span><span>Оригінал</span><span class="z">Знаків</span><span>Стан</span></div>
${CHT.map(([n, t, z, ok]) => `<div class="cht"><span class="ck2 ${ok ? 'on' : ''}">${ok ? ic('check', 14) : ''}</span><span class="n">${n}</span><span class="ell">${t}</span><span class="z">${z}</span><span class="state ${ok ? 'q' : ''}"><i></i>${ok ? 'не перекладено' : 'пропуститься'}</span></div>`).join('')}</div>
${iff('is.pick', aiSide(false, false), true)}${iff('is.custom', aiSide(true, false))}${iff('is.low', aiSide(false, true))}</div></div></div></div>`;
const AS = ['вибір', 'свій набір', 'не вистачає шагів'], AI = ['pick', 'custom', 'low'];
export const TranslateP = { file: 'TranslateP.dc.html', title: 'Перекласти з ШІ — планшет', w: 834, h: 1194, dev: 'tb', stany: AS, ids: AI, extra: CSS, root: `<div class="{{ cls }}" style="width:834px;height:1194px">${studioFrame({ active: 0, path: 'Доглядач маяка › <b>перекласти з ШІ</b>', main: aiBody('tb') })}</div>` };
export const TranslateK = { file: 'TranslateK.dc.html', title: 'Перекласти з ШІ — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: AS, ids: AI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'Доглядач маяка › <b>перекласти з ШІ</b>', main: aiBody('dk') })}</div>` };

// ── Автопереклад із сайту (рішення 14, 26: і на телефоні) ──
const autoBody = (dk) => `<div class="page"><div class="tr-g">${dk ? trMenu(3) : ''}<div class="col" style="gap:16px;min-width:0;max-width:760px">${dk ? '<h1 class="h1">Автопереклад із сайту</h1>' : ''}
${iff('is.on', `<div class="card" style="padding:14px;display:flex;flex-direction:column;gap:6px"><span class="lab">Джерело</span><b class="strong" style="word-break:break-all">ncode.syosetu.com/n4027kx</b><span class="dim" style="font-size:13px">Перевірено сьогодні о 06:00 · в оригіналі 62 глави · нові: 61–62</span><button type="button" class="btn sm out" style="align-self:flex-start">${ic('refresh', 15)} Перевірити зараз</button></div>
<div class="row" style="gap:12px;flex-wrap:wrap;align-items:flex-end"><div class="fm"><label>Перекладати до глави</label><span class="field" style="width:110px">62</span></div><div class="fm"><label>Якість</label><div class="seg"><span>Найкраща</span><span class="on">Висока</span><span>Швидка</span></div></div></div>
<div class="col">${[['Нові глави — щотижня', 'перевірка щопонеділка, переклад одразу', 1], ['Одразу опублікувати', 'інакше — чернетки', 1], ['Повідомляти про нові глави оригіналу', 'на сайті й у Telegram', 1]].map(([t, s, on]) => `<div class="set-r"><span>${t}<small>${s}</small></span><span class="tog ${on ? 'on' : ''}"></span></div>`).join('')}</div>
<div class="sum"><span class="dim">Глави 46–62 · 17 глав · резерв із запасом</span><b>[ціна] ш</b></div><div class="row"><a href="LiveK.dc.html" class="btn pri">Перекласти 46–62</a><span class="faint" style="font-size:12.5px">Закриті (платні) глави на сайті оригіналу не беремо.</span></div>`, true)}
${iff('is.locked', `<div class="err" style="padding-top:30px;max-width:none">${ic('lock', 40, 1.4)}<b>Автопереклад із сайту — з дозволу власника сайту</b><p>Глави братимуться самі за посиланням на сайт оригіналу, нові — щотижня. Попросіть доступ: відповідь прийде у «Сповіщення».</p><button type="button" class="btn pri">Попросити доступ</button><a href="OriginalK.dc.html" class="btn">Вставити оригінал вручну</a></div>`)}
</div></div></div>`;
export const AutoT = { file: 'AutoT.dc.html', title: 'Автопереклад із сайту — телефон', w: 390, h: 844, dev: 'ph', stany: ['налаштування', 'без дозволу'], ids: ['on', 'locked'], extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'TrT.dc.html', title: 'Автопереклад із сайту', main: autoBody(false) })}</div>` };
export const AutoK = { file: 'AutoK.dc.html', title: 'Автопереклад із сайту — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['налаштування', 'без дозволу'], ids: ['on', 'locked'], extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'Доглядач маяка › <b>автопереклад із сайту</b>', main: autoBody(true) })}</div>` };
