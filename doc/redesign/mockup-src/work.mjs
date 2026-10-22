// Студія, частина 2: робоче місце глави (комп'ютер, планшет, телефон), «Як у книжці», історія глави,
// словник, правки на перевірку, решта сторінок перекладу (назви, томи, з файлу, естафета, дані, кошик, команди).
import { ic, iff, kk, fmt } from './lib.mjs';
import { ROWS, CH12, NOVELS } from './content.mjs';
import { CHROME_CSS } from './chrome.mjs';
import { READER_CSS } from './reader.mjs';
import { SITE_CSS } from './site.mjs';
import { COMM_CSS } from './comm.mjs';
import { STUDIO_CSS, studioFrame, studioPhone, trMenu } from './studio.mjs';
import { WS_CSS, WS_PAL, WS_PULT, WS_OBL, WS_ZV } from './ws-dirs.mjs';

const WORK_CSS = `
.ws-root{position:relative}
.ws-root>div:not(.pop):not(.pubconf){height:100%}
/* сховані колонки */
.ws-wide .b-dk{grid-template-columns:54px minmax(0,1fr)}
.ws-wide .b-exp,.ws-wide .b-ins{display:none}
.ws-wide .b-tabs,.ws-wide .b-ed{grid-column:2/-1}
.ws-wide .g-body{grid-template-columns:minmax(0,1fr)}
.ws-wide .g-body>aside{display:none}
.ws-wide .a-body{grid-template-columns:minmax(0,1fr)}
.ws-wide .a-toc,.ws-wide .a-margin{display:none}
.ws-wide .c-dk{grid-template-columns:minmax(0,1fr)}
.ws-wide .c-side,.ws-wide .c-drawer{display:none}
.ws-wide .a-spread{padding:26px 120px 40px}
.ws-wide .g-grid{padding:0 60px 12px}
/* планшет: без бічних колонок, частина кнопок ховається */
.ws-tb .b-k,.ws-tb .b-tools .b-tg:nth-of-type(n+4){display:none}
.ws-tb .a-crumb,.ws-tb .a-topr .st,.ws-tb .g-crumb,.ws-tb .g-st,.ws-tb .g-top2 .g-b:not(.pri):not(.ic){display:none}
.ws-tb .a-spread{padding:20px}
.ws-tb .a-bar{gap:12px;font-size:14px}
.ws-tb .c-mh .big{font-size:48px}
.ws-tb .c-mh h1{font-size:22px}
.ws-tb .c-tg .c-pill{display:none}
.ws-tb .b-pub{margin-right:4px}
.ws-tb .g-grid{padding:0 8px 12px}
/* конфлікт публікації, картинка */
.pubconf{position:absolute;left:50%;top:76px;transform:translateX(-50%);width:640px;z-index:30;padding:16px 18px;border-radius:var(--rcard);background:var(--s1);box-shadow:0 18px 50px rgba(0,0,0,.45),inset 0 0 0 1px var(--warn);display:flex;gap:12px;font:14px/1.5 var(--f-ui)}
.pubconf .i{color:var(--warn);margin-top:2px}
.imgm{right:200px;top:70px;width:380px;padding:8px;font:14.5px/1.4 var(--f-ui);z-index:30}
.imgm a{display:flex;gap:12px;align-items:flex-start;padding:10px;border-radius:var(--rb)}
.imgm a small{display:block;font-size:12.5px;color:var(--faint)}
/* «Як у книжці» */
.wb{display:flex;flex-direction:column;height:100%}
.wb-h{display:flex;align-items:center;gap:10px;padding:10px 16px;border-bottom:1px solid var(--line);flex:none}
.wb-b{flex:1;overflow:auto;display:flex;justify-content:center}
.wb-c{width:100%;max-width:720px;padding:36px 0 60px;display:flex;flex-direction:column;gap:6px}
.wb-p{display:grid;grid-template-columns:110px minmax(0,1fr);gap:16px;padding:8px 0}
.wb-p .mk{font:600 11.5px/1.3 var(--f-ui);padding-top:6px;text-align:right}
.wb-p .mk.m{color:var(--faint)} .wb-p .mk.v{color:var(--acc2)} .wb-p .mk.s{color:var(--acc)}
.wb-p p{font:18px/1.7 var(--f-rd);color:var(--read)}
.wb-p.cur{background:var(--s1);border-radius:var(--rcard);padding:12px;margin:0 -12px;box-shadow:inset 0 0 0 1px var(--line)}
.wb-o{grid-column:2;font:15px/1.6 var(--f-rd);color:var(--dim);padding:10px 12px;border-radius:var(--rin);background:var(--bg);box-shadow:inset 0 0 0 1px var(--line)}
.k-pult .wb-p .mk{font-family:var(--f-num);font-weight:500}
.k-pal .wb-p .mk{font-variant:all-small-caps;letter-spacing:.06em;font-size:14px}
/* телефон */
.wt{display:flex;flex-direction:column;height:100%}
.wt-tools{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none;padding:10px 16px;border-bottom:1px solid var(--line);flex:none}
.wt-b{flex:1;overflow:auto;padding:16px;display:flex;flex-direction:column;gap:14px}
.wt-b p{font:17px/1.68 var(--f-rd);color:var(--read)}
.wt-cur{display:flex;flex-direction:column;gap:10px;padding:12px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1.5px var(--acc)}
.wt-cur .o{font:14.5px/1.55 var(--f-rd);color:var(--dim);padding:8px 10px;border-radius:var(--rin);background:var(--bg)}
.wt-sug{display:flex;flex-direction:column;gap:8px;padding:12px;border-radius:var(--rcard);box-shadow:inset 0 0 0 1px var(--line)}
.wt-sug del{color:var(--del);background:var(--delbg)} .wt-sug ins{color:var(--ins);background:var(--insbg);text-decoration:none}
.wt-fmt{flex:none;display:flex;gap:2px;align-items:center;padding:6px 10px;border-top:1px solid var(--line);background:var(--s0)}
.wt-fmt button{width:40px;height:40px;display:grid;place-items:center;border-radius:var(--rb);font-weight:700;color:var(--dim)}
.k-pult .wt-cur{border-radius:0;box-shadow:inset 0 0 0 1px var(--acc2)}
.k-pal .wt-cur{border-radius:0;background:var(--s2);box-shadow:none;border-top:1px solid var(--acc)}
.k-obl .wt-cur{border-radius:22px;box-shadow:0 0 0 2px var(--acc)}
/* історія */
.hi-g{display:grid;grid-template-columns:360px minmax(0,1fr);gap:28px;align-items:start}
.hv{display:grid;grid-template-columns:36px minmax(0,1fr);gap:4px 12px;padding:12px;border-radius:var(--rin);font-size:14px}
.hv.on{background:var(--s1);box-shadow:inset 0 0 0 1.5px var(--acc)}
.hv .av{width:36px;height:36px;font-size:13px;grid-row:1/3}
.hv .m{font-size:12.5px;color:var(--faint)}
.hv .pub{font-size:11.5px;color:var(--acc2);font-weight:700}
.hd{display:flex;flex-direction:column;gap:12px}
.hd .p{font:16px/1.6 var(--f-rd);padding:10px 0;border-bottom:1px solid var(--line)}
.hd .p .n{font:12px var(--f-ui);color:var(--faint);display:block}
.hd del{color:var(--del);background:var(--delbg)} .hd ins{color:var(--ins);background:var(--insbg);text-decoration:none}
/* словник */
.gl-g{display:flex;flex-direction:column;gap:16px}
.dk .gl-g{display:grid;grid-template-columns:minmax(0,1fr) 400px;gap:24px;align-items:start}
.k-obl.dk .gl-g{grid-template-columns:minmax(0,1fr)}
.ws-tb .b-status span:nth-child(n+3){display:none}
.gr{display:grid;grid-template-columns:22px minmax(0,1fr) minmax(0,1fr) 90px 70px auto;gap:10px;align-items:center;padding:10px 4px;border-bottom:1px solid var(--line);font-size:14px}
.gr.h{font-size:12px;color:var(--faint)}
.gr b{color:var(--strong);font-weight:600}
.gr .o{color:var(--dim);font-family:'IBM Plex Mono',monospace;font-size:12.5px}
.gr.on{background:var(--s1)}
.ph .gr{grid-template-columns:minmax(0,1fr) auto;gap:4px 10px}
.ph .gr .k,.ph .gr .f,.ph .gr>.ck2{display:none}
.k-pal .gr{border-bottom:1px dotted var(--line)}
.k-obl .gr{border:0;border-radius:14px;margin-bottom:4px;padding:10px 12px}
.gc{display:flex;flex-direction:column;gap:14px;padding:18px}
.gc .av{width:64px;height:64px;font-size:26px}
.gc .forms{display:grid;grid-template-columns:90px minmax(0,1fr);gap:6px 10px;font-size:13.5px}
.gc .forms span:nth-child(odd){color:var(--faint)}
.gc .fct{display:grid;grid-template-columns:52px minmax(0,1fr) auto;gap:8px;font-size:14px;padding:6px 0;border-bottom:1px solid var(--line);align-items:start}
.gc .fct span:first-child{font-size:12.5px;color:var(--faint);padding-top:2px}
.k-pal .gc{padding:18px 0}
.occ{display:grid;grid-template-columns:60px minmax(0,1fr) auto;gap:10px;padding:8px 0;border-bottom:1px solid var(--line);font-size:14px;align-items:center}
.occ .n{color:var(--faint);font-size:12.5px}
.occ mark{background:var(--selw);color:inherit;border-radius:3px}
/* правки на перевірку */
.rq{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:6px 14px;padding:14px 0;border-bottom:1px solid var(--line);align-items:center}
.rq .src{font-size:12.5px;color:var(--faint)}
.rq p{font:15.5px/1.6 var(--f-rd);color:var(--read);grid-column:1}
.rq del,.rv del{color:var(--del);background:var(--delbg)} .rq ins,.rv ins{color:var(--ins);background:var(--insbg);text-decoration:none}
.k-obl .rq{border:0;background:var(--s1);border-radius:20px;padding:14px;margin-bottom:6px}
.rv-g{display:flex;flex-direction:column;height:100%}
.rv-bar{display:flex;align-items:center;gap:10px;padding:10px 16px;border-bottom:1px solid var(--line);flex-wrap:wrap;flex:none}
.rv-b{flex:1;overflow:auto;display:flex;justify-content:center}
.rv-c{width:100%;max-width:720px;padding:28px 16px 60px;display:flex;flex-direction:column;gap:16px}
.rv-c p{font:18px/1.7 var(--f-rd);color:var(--read)}
.rv{display:flex;flex-direction:column;gap:10px;padding:12px 14px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.rv.on{box-shadow:inset 0 0 0 2px var(--acc)}
.rv p{font-size:17.5px}
.rv .who{font-size:12.5px;color:var(--faint)}
.k-pal .rv{border-radius:0;background:var(--s2);box-shadow:none;border-left:0;border-top:1px solid var(--acc)}
/* решта сторінок перекладу */
.tl{display:grid;grid-template-columns:44px minmax(0,1fr) minmax(0,1fr) auto;gap:12px;align-items:center;padding:9px 4px;border-bottom:1px solid var(--line);font-size:14px}
.tl .n{color:var(--faint)}
.tl .o{color:var(--dim);font-size:13px}
.tl .field{height:34px;color:var(--tx);font-size:14px}
.vrow{display:grid;grid-template-columns:150px 90px 90px minmax(0,1fr) 80px;gap:10px;align-items:center;padding:10px 4px;border-bottom:1px solid var(--line);font-size:14px}
.vrow .old{color:var(--faint);text-decoration:line-through}
.vrow .new{color:var(--acc);font-weight:600}
.imp{display:grid;grid-template-columns:44px minmax(0,1fr) 120px auto;gap:12px;align-items:center;padding:9px 4px;border-bottom:1px solid var(--line);font-size:14px}
.imp .w{color:var(--warn);font-size:12.5px}
`;
const CSS = READER_CSS + CHROME_CSS + SITE_CSS + COMM_CSS + STUDIO_CSS + WS_CSS + WORK_CSS;
const N = Object.fromEntries(NOVELS.map(n => [n.id, n]));

// ── Робоче місце: комп'ютер і планшет ──
const wsPerKarkas = kk({ pult: WS_PULT(), zv: WS_ZV(), pal: WS_PAL(), obl: WS_OBL() });
const BOOK = [
  [CH12[0], 'v', 'перевірено'], [CH12[1], 's', 'є пропозиція'], ['— Пором запізнився, — відповіла вона. — На цьому острові все запізнюється.', 'v', 'перевірено'],
  [CH12[3], 'm', 'машинний'], [CH12[4], 'm', 'машинний'], [CH12[5], 'v', 'перевірено']
];
const book = `<div class="wb"><div class="wb-h"><a href="TrK.dc.html" class="ib" aria-label="До перекладу">${ic('back', 20)}</a><b class="strong grow">Глава 12 · Лінза, що пам’ятає світло · як у книжці</b><span class="faint" style="font-size:13px">Перевірено 3 з 6</span><div class="seg" style="flex:none;min-width:220px"><a href="#" onClick="{{ go.side }}">Поруч</a><span class="on">Як у книжці</span></div><button type="button" class="btn sm pri">Опублікувати</button></div>
<div class="wb-b"><div class="wb-c">${BOOK.map(([t, k, l], i) => `<div class="wb-p ${i === 1 ? 'cur' : ''}"><span class="mk ${k}">${l}</span><p>${fmt(t)}</p>${i === 1 ? `<div class="wb-o"><span class="lab" style="display:block;margin-bottom:4px">Оригінал</span>“You’re late,” said Master Orest without turning around. He was polishing the great lens with a rag that had once been a shirt.</div><div class="row" style="grid-column:2;gap:6px;flex-wrap:wrap"><button type="button" class="btn sm">${ic('check', 15)} Позначити перевіреним</button><button type="button" class="btn sm out">${ic('spark', 15)} Інший варіант</button><button type="button" class="btn sm out">Пропозиція: «мовив» замість «сказав»</button></div>` : ''}</div>`).join('')}</div></div></div>`;
const conf = `<div class="pubconf" role="alert">${ic('alert', 20)}<span class="col" style="gap:8px"><b class="strong">Не опубліковано: orysia опублікувала главу 12 о 12:06 — після того, як ви почали правити</b><span class="dim">Ваші правки збережено в чернетці. Подивіться різницю й опублікуйте ще раз — нічого не загубиться.</span><span class="row"><button type="button" class="btn sm pri">Показати різницю</button><a href="HistoryK.dc.html" class="btn sm out">Історія глави</a></span></span></div>`;
const imgMenu = `<div class="pop imgm" role="menu"><a href="#">${ic('img', 20)}<span>З файлу<small>JPEG, PNG, WebP до 5 МБ</small></span></a><a href="#">${ic('share', 20)}<span>За посиланням<small>сервер збереже копію</small></span></a><a href="PicturesK.dc.html">${ic('layers', 20)}<span>З моїх картинок<small>після абзацу з курсором</small></span></a><a href="#">${ic('spark', 20)}<span>Намалювати сцену<small>ШІ опише виділений фрагмент · ціна наперед [ціна] ш</small></span></a></div>`;
const WKS = ['поруч', 'як у книжці', 'сховані колонки', 'конфлікт публікації', 'вставити картинку'], WKI = ['side', 'book', 'wide', 'conf', 'img'];
export const WorkK = { file: 'WorkK.dc.html', title: 'Робоче місце глави — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: WKS, ids: WKI, alias: { wsv: ['side', 'wide', 'conf', 'img'] }, scls: { wide: 'ws-wide' }, extra: CSS,
  root: `<div class="{{ cls }} {{ scls }} ws-root" style="width:1440px;height:900px">${iff('wsv', wsPerKarkas, true)}${iff('is.book', book)}${iff('is.conf', conf)}${iff('is.img', imgMenu)}</div>` };
export const WorkP = { file: 'WorkP.dc.html', title: 'Робоче місце глави — планшет', w: 834, h: 1194, dev: 'tb', stany: ['поруч', 'як у книжці'], ids: ['side', 'book'], scls: { side: 'ws-wide ws-tb', book: '' }, extra: CSS,
  root: `<div class="{{ cls }} {{ scls }} ws-root" style="width:834px;height:1194px">${iff('is.side', wsPerKarkas, true)}${iff('is.book', book)}</div>` };

// ── Робоче місце: телефон («Оригінал» абзацу під курсором) ──
const workPhone = `<div class="wt"><div class="wt-tools"><span class="chip on">${ic('eye', 14)} Оригінал абзацу</span><span class="chip">${ic('spark', 14)} Вичитати главу</span><a href="HistoryK.dc.html" class="chip">${ic('clock', 14)} Історія · 5</a><span class="chip">Як у книжці</span></div>
<div class="wt-b"><p>${fmt(CH12[0])}</p>
${iff('not.img', `<div class="wt-cur"><span class="lab">Абзац 2 · редагуєте</span><p style="font:17px/1.65 var(--f-rd);color:var(--strong)">— Ти запізнилася, — не обертаючись, сказав майстер Орест. Він натирав <span class="selw">велику лінзу</span> ганчіркою, що колись була сорочкою.<span class="caret"></span></p><div class="o"><b class="lab" style="display:block;margin-bottom:2px">Оригінал</b>“You’re late,” said Master Orest without turning around. He was polishing <span class="selw">the great lens</span> with a rag that had once been a shirt.</div><div class="row" style="gap:6px;flex-wrap:wrap"><button type="button" class="btn sm">${ic('spark', 15)} Інший варіант</button><button type="button" class="btn sm">Вичитати</button><button type="button" class="btn sm out">${ic('undo', 15)} Машинний</button></div></div>`, true)}
${iff('is.img', `<div class="wt-cur"><span class="lab">Вставити картинку після абзацу 2</span><div class="col" style="gap:4px">${[['img', 'З файлу'], ['layers', 'З моїх картинок'], ['spark', 'Намалювати сцену · [ціна] ш']].map(([i, t]) => `<button type="button" class="btn out" style="justify-content:flex-start">${ic(i, 18)} ${t}</button>`).join('')}</div></div>`)}
<div class="wt-sug"><span class="lab">Вичитка ШІ пропонує · абзац 3</span><p style="font:16px/1.6 var(--f-rd)">— Пором запізнився, — відповіла вона. — На цьому острові <del>ніщо не приходить вчасно</del> <ins>все запізнюється</ins>.</p><div class="row"><button type="button" class="btn sm pri">Прийняти</button><button type="button" class="btn sm out">Лишити як є</button></div></div>
<p>${fmt(CH12[3])}</p></div>
<div class="wt-fmt"><button type="button" aria-label="Жирний">Ж</button><button type="button" aria-label="Курсив" style="font-style:italic">К</button><button type="button" aria-label="Підкреслений" style="text-decoration:underline">П</button><button type="button" aria-label="Закреслений" style="text-decoration:line-through">З</button><button type="button" aria-label="Картинка" onClick="{{ go.img }}">${ic('img', 19)}</button><span class="grow"></span><button type="button" class="btn sm" style="width:auto;padding:0 10px">До словника</button></div></div>`;
export const WorkT = { file: 'WorkT.dc.html', title: 'Робоче місце глави — телефон', w: 390, h: 844, dev: 'ph', stany: ['редагування', 'вставити картинку'], ids: ['ed', 'img'], extra: CSS,
  root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'TrT.dc.html', title: 'Гл. 12 · чернетка', actions: '<button type="button" class="btn sm pri" style="margin-left:4px">Опублікувати</button>', main: workPhone })}</div>` };

// ── Історія глави (рішення 23) ──
const HV = [
  ['М', 'marta_k', 'правка в читалці · 2 абзаци', 'щойно', '', 1], ['ШІ', 'Вичитка ШІ', 'Claude Sonnet · запуск 41–45 · 4 абзаци', 'учора', ''], ['О', 'orysia', 'прийняла правки читачів: nika, oleh.reads · 3 абзаци', '2 дні тому', 'у читачів'], ['ШІ', 'Переклад ШІ', 'Claude Sonnet · запуск 11–15 · уся глава', '12 днів тому', '']
];
const history = `<div class="page"><div class="bar-act"><div class="col" style="gap:4px"><span class="lab">Доглядач маяка · глава 12</span><h1 class="h1">Історія глави</h1></div><a href="WorkK.dc.html" class="btn sm out">До робочого місця</a></div>
<div class="flt-row"><span class="chip on">Усі · 9</span><span class="chip">Команда · 4</span><span class="chip">ШІ · 3</span><span class="chip">Правки читачів · 2</span></div>
<div class="hi-g"><div class="col" style="gap:4px">${HV.map(([a, w, t, d, p, on], i) => `<div class="hv ${i === 2 ? 'on' : ''}"><span class="av">${a}</span><span class="row" style="gap:8px"><b class="strong">${w}</b>${p ? `<span class="pub">${p}</span>` : ''}</span><span class="m">${t} · ${d}</span></div>`).join('')}</div>
<div class="hd"><div class="row" style="justify-content:space-between"><span class="col" style="gap:2px"><b class="strong">orysia · 2 дні тому · у читачів</b><span class="faint" style="font-size:13px">Прийнято правки: nika (абз. 2, 7) — «кличний відмінок», oleh.reads (абз. 5) — «тут дзвін, а не дзвоник»</span></span><button type="button" class="btn sm">${ic('undo', 15)} Повернути цю версію</button></div>
<div class="p"><span class="n">абзац 2 · nika</span>— Ти запізнилася, — не обертаючись, сказав <del>майстер Орест</del> <ins>майстер Орест</ins>. Він натирав велику лінзу <del>ганчіркою, яка</del> <ins>ганчіркою, що</ins> колись була сорочкою.</div>
<div class="p"><span class="n">абзац 5 · oleh.reads</span>Унизу під тонкою ковдрою туману спала гавань, і десь у ній один раз ударив <del>дзвоник</del> <ins>дзвін</ins>.</div>
<div class="p"><span class="n">абзац 7 · nika</span>— Вона завжди так світиться? — спитала <del>Ліна</del> <ins>вона</ins>.</div></div></div></div>`;
export const HistoryK = { file: 'HistoryK.dc.html', title: 'Історія глави — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['версії'], ids: ['v'], extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'Доглядач маяка › глава 12 › <b>історія</b>', main: history })}</div>` };

// ── Словник ──
const GE = [['Ліна', 'Lina · リナ', 'персонаж', 'жін.', 'погоджено', 'pub'], ['майстер Орест', 'Master Orest', 'персонаж', 'чол.', 'погоджено', 'pub'], ['велика лінза', 'the great lens', 'термін', '—', 'нове', 'dr', 1], ['Сірий Ріг', 'Grey Horn', 'місце', '—', 'погоджено', 'pub'], ['журнал доглядача', 'the keeper’s log', 'річ', '—', 'нове', 'dr'], ['Сірий Ян', 'Grey Jan', 'персонаж', 'чол.', 'нове', 'dr']];
const glList = (dk) => `<div class="col"><div class="row" style="justify-content:space-between;gap:8px;flex-wrap:wrap"><span class="field" style="max-width:${dk ? 280 : 1000}px">${ic('search', 16)} Слово чи переклад</span>${dk ? `<span class="row" style="gap:6px"><button type="button" class="btn sm">${ic('check', 15)} Погодити вибрані · 2</button><button type="button" class="btn sm out">Відхилити</button><button type="button" class="btn sm out">${ic('spark', 15)} Розібрати персонажів ШІ</button></span>` : ''}</div>
<div class="flt-row" style="margin:10px 0"><span class="chip on">Усі · 124</span><span class="chip">Нові · 5</span><span class="chip">Погоджені · 112</span><span class="chip">Відхилені · 7</span><span class="chip">Персонажі</span><span class="chip">Місця</span><span class="chip">Терміни</span></div>
${dk ? '<div class="gr h"><span></span><span>Українською</span><span>В оригіналі</span><span>Вид</span><span>Рід</span><span>Стан</span></div>' : ''}
${GE.map(([u, o, k, g, s, c, on]) => `<div class="gr ${on ? 'on' : ''}"><span class="ck2 ${s === 'нове' && dk ? 'on' : ''}">${s === 'нове' && dk ? ic('check', 13) : ''}</span><span class="col"><b>${u}</b>${dk ? '' : `<span class="o">${o}</span>`}</span>${dk ? `<span class="o">${o}</span><span class="k">${k}</span><span class="f">${g}</span>` : ''}<span class="row" style="gap:6px"><span class="state ${c}"><i></i>${s}</span>${s === 'нове' ? `<button type="button" class="ib" aria-label="Погодити">${ic('check', 16)}</button><button type="button" class="ib" aria-label="Відхилити">${ic('x', 16)}</button>` : ''}</span></div>`).join('')}</div>`;
const glCard = `<aside class="card gc"><div class="row" style="gap:14px"><span class="av">В</span><span class="col" style="gap:4px"><span class="state dr"><i></i>нове · з глави 12</span><b class="h2">велика лінза</b><span class="faint" style="font-size:13px">термін · 9 згадок у 4 главах</span></span></div>
<div class="fm"><label>Українською</label><span class="field">велика лінза</span></div>
<div class="forms"><span>англійська</span><span>the great lens · the Lens</span><span>вид · рід</span><span>термін · жін.</span><span>примітка</span><span>лінза маяка, «пам’ятає» світло</span></div>
<div class="col"><span class="lab">Що відомо · «Хто це»</span>${[['гл. 3', 'Її привезли з материка сто років тому.'], ['гл. 12', 'Світиться, коли на неї дивляться.']].map(([n, t]) => `<div class="fct"><span>${n}</span><span>${t}</span><button type="button" class="ib" aria-label="Змінити">${ic('pen', 15)}</button></div>`).join('')}<a href="#" class="acc" style="font-size:13.5px;margin-top:6px">+ Додати факт</a></div>
<div class="col" style="gap:8px">${[['Діє в історії', 'монстри й лише згадані не потрапляють у «Персонажі»', 1], ['Шукати всі форми слова', 'для імені, що відмінюється', 1]].map(([t, s, on]) => `<div class="set-r" style="padding:6px 0"><span>${t}<small>${s}</small></span><span class="tog ${on ? 'on' : ''}"></span></div>`).join('')}<span class="row" style="gap:6px;font-size:13.5px"><span class="faint">Інші назви:</span> Лінза · <a href="#" class="acc">«Це той самий, що…»</a></span></div>
<div class="row" style="gap:6px;flex-wrap:wrap"><button type="button" class="btn sm pri">Погодити</button><button type="button" class="btn sm" onClick="{{ go.repl }}">Замінити в усіх главах</button><button type="button" class="btn sm out">Відхилити</button></div>
<span class="row" style="gap:10px;align-items:center"><span class="upl" style="width:52px;height:52px;align-self:auto">${ic('img', 18)}</span><span class="faint" style="font-size:12.5px">Портрет: з «Моїх картинок» або «Намалювати» · [ціна] ш</span></span></aside>`;
const glRepl = `<aside class="card gc"><b class="h2">Замінити «велика лінза» в усіх главах</b><div class="fm"><label>Нова форма</label><span class="field">Велика Лінза</span><small>Закінчення зберігаються: «великої лінзи» → «Великої Лінзи»</small></div>
<span class="lab">Де трапляється · 9</span>${[['гл. 3', 'привезли <mark>велику лінзу</mark> з материка'], ['гл. 12', 'Він натирав <mark>велику лінзу</mark> ганчіркою'], ['гл. 14', 'світло <mark>великої лінзи</mark> лягло на воду']].map(([n, t]) => `<div class="occ"><span class="n">${n}</span><span>${t}</span><a href="#" class="acc" style="font-size:13px">у тексті</a></div>`).join('')}
<div class="col" style="gap:6px"><span class="row" style="gap:10px;font-size:14px"><span class="rad on"></span>Правками на перевірку</span><span class="row" style="gap:10px;font-size:14px"><span class="rad"></span>Одразу в главах — нова версія кожної</span><span class="row" style="gap:10px;font-size:14px"><span class="ck2"></span>Узгодити відмінки вичиткою ШІ · [ціна] ш</span></div>
<div class="row"><button type="button" class="btn pri">Замінити у 4 главах</button><button type="button" class="btn" onClick="{{ go.card }}">Скасувати</button></div></aside>`;
const glBody = (dk) => `<div class="page">${dk ? '<div class="tr-g">' + trMenu(4) : ''}<div class="col" style="gap:14px;min-width:0">${dk ? '<h1 class="h1">Словник · Доглядач маяка</h1>' : ''}
${iff('is.list', dk ? `<div class="gl-g">${glList(dk)}${glCard}</div>` : glList(dk), true)}${iff('is.card', dk ? `<div class="gl-g">${glList(dk)}${glCard}</div>` : glCard)}${iff('is.repl', dk ? `<div class="gl-g">${glList(dk)}${glRepl}</div>` : glRepl)}
</div>${dk ? '</div>' : ''}</div>`;
const GS = ['список', 'картка запису', 'заміна в главах'], GI = ['list', 'card', 'repl'];
export const GlossaryT = { file: 'GlossaryT.dc.html', title: 'Словник — телефон', w: 390, h: 844, dev: 'ph', stany: GS, ids: GI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'TrT.dc.html', title: 'Словник', main: glBody(false) })}</div>` };
export const GlossaryK = { file: 'GlossaryK.dc.html', title: 'Словник — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: GS, ids: GI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'Доглядач маяка › <b>словник</b>', main: glBody(true) })}</div>` };

// ── Правки на перевірку (рішення 4) ──
const RQ = [['глава 12 · абз. 2 · marta_k', '— Ти запізнилася, — не обертаючись, <del>сказав</del> <ins>мовив</ins> майстер Орест.', '«мовив» краще пасує до Ореста'], ['глава 12 · абз. 9 · marta_k', 'Світло лінзи пройшло над <del>водою</del> <ins>морем</ins> і зникло в тумані.', ''], ['глава 11 · абз. 3 · nika', 'Пором <del>прийшов</del> <ins>причалив</ins> о сьомій.', 'кличний відмінок у сусідньому абзаці теж']];
const queue = `<div class="col"><div class="row" style="justify-content:space-between;flex-wrap:wrap;gap:8px"><div class="flt-row"><span class="chip on">Чекають · 3</span><span class="chip">Прийняті</span><span class="chip">Відхилені</span></div><a href="#" class="btn sm pri" onClick="{{ go.text }}">Перевіряти в тексті</a></div>${RQ.map(([s, t, w]) => `<div class="rq"><span class="src">${s}</span><span></span><p>${t}</p><span class="row" style="gap:6px"><button type="button" class="btn sm pri">Прийняти</button><button type="button" class="btn sm out">Відхилити</button></span>${w ? `<span class="faint" style="font-size:12.5px">причина: ${w}</span>` : ''}</div>`).join('')}</div>`;
const inText = `<div class="rv-g"><div class="rv-bar"><b class="strong">Глава 12 · 2 правки</b><span class="faint">‹ Правка 1 з 2 ›</span><div class="seg" style="flex:none;min-width:190px"><span class="on">Разом</span><span>Було і стало</span></div><span class="grow"></span><button type="button" class="btn sm pri">${ic('check', 15)} Прийняти всі</button></div><div class="rv-b"><div class="rv-c"><p>${fmt(CH12[0])}</p>
<div class="rv on"><span class="who">marta_k · «мовив краще пасує до Ореста» · 2 год тому</span><p>— Ти запізнилася, — не обертаючись, <del>сказав</del> <ins>мовив</ins> майстер Орест. Він натирав велику лінзу ганчіркою, що колись була сорочкою.</p><div class="row" style="gap:6px"><button type="button" class="btn sm pri">Прийняти</button><button type="button" class="btn sm out">Відхилити</button><button type="button" class="btn sm out">Змінити й прийняти</button></div></div>
<p>${fmt(CH12[2])}</p><p>${fmt(CH12[3])}</p></div></div></div>`;
const rvBody = (dk) => `${iff('is.queue', `<div class="page">${dk ? '<div class="tr-g">' + trMenu(5) : ''}<div class="col" style="gap:14px;min-width:0">${dk ? '<h1 class="h1">Правки на перевірку</h1>' : ''}${queue}</div>${dk ? '</div>' : ''}</div>`, true)}${iff('is.text', inText)}`;
const RS = ['черга', 'у тексті'], RI = ['queue', 'text'];
export const ReviewT = { file: 'ReviewT.dc.html', title: 'Правки на перевірку — телефон', w: 390, h: 844, dev: 'ph', stany: RS, ids: RI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${studioPhone({ back: 'TrT.dc.html', title: 'Правки · 3', main: rvBody(false) })}</div>` };
export const ReviewK = { file: 'ReviewK.dc.html', title: 'Правки на перевірку — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: RS, ids: RI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'Доглядач маяка › <b>правки</b>', main: rvBody(true) })}</div>` };

// ── Решта сторінок перекладу ──
const titles = `<p class="dim" style="font-size:14px">Назви й номери, які дав аналіз. Виправте до перекладу — читачі бачать номер з оригіналу.</p>${[[46, 'The Second Bell', 'Другий дзвін'], [47, 'Salt and Iron', 'Сіль і залізо'], [48, 'The Ferryman’s Debt', 'Борг поромника'], [49, 'Low Tide', 'Відплив']].map(([n, o, u]) => `<div class="tl"><span class="n">${n}</span><span class="o">${o}</span><span class="field">${u}</span><button type="button" class="ib" aria-label="Повернути з аналізу">${ic('undo', 16)}</button></div>`).join('')}<button type="button" class="btn pri" style="align-self:flex-start">Зберегти назви</button>`;
const struct = `<div class="row" style="gap:12px;flex-wrap:wrap"><div class="fm"><label>Нумерація</label><div class="seg"><span class="on">Наскрізна</span><span>З 1 у кожному томі</span></div></div></div>
<div class="vrow" style="font-size:12px;color:var(--faint)"><span>Том · вид</span><span>Глави</span><span>Номер</span><span>Назва</span><span></span></div>
${[['Пролог · пролог', '1', '<span class="old">1</span> <span class="new">—</span>', 'Сіль на губах'], ['Том 1 · том', '2–24', '1–23', 'Сіль і світло'], ['Том 2 · том', '25–40', '24–39', 'Журнал доглядача'], ['Побічні · побічні', '41', '<span class="old">40</span> <span class="new">437.2</span>', 'Суп для митаря']].map(([v, g, n, t]) => `<div class="vrow"><span>${v}</span><span>${g}</span><span>${n}</span><span>${t}</span><a href="#" class="acc" style="font-size:13px">змінити</a></div>`).join('')}
<p class="faint" style="font-size:12.5px">До збереження видно старий і новий номер кожної глави. Адреси глав не змінюються.</p><div class="row"><button type="button" class="btn pri">Зберегти · 41 глава змінить номер</button><button type="button" class="btn sm out">Повернути автоматичну</button></div>`;
const fromFile = `<div class="drop">${ic('dl', 26, 1.5)}<b class="strong">Перетягніть .txt чи .md або <span class="acc">виберіть</span></b><span class="faint" style="font-size:12.5px">одна глава чи кілька — розіб’ємо за заголовками</span></div><span class="lab">Розбиття · dohliadach-tom3.md</span>${[[41, 'Глава 41. Друга лампа', '3 120 знаків', ''], [42, 'Глава 42. Північний вітер', '2 840 знаків', ''], [43, 'Глава 43. Лампа без вогню', '2 990 знаків', 'спрощено: таблицю перетворено на текст']].map(([n, t, z, w]) => `<div class="imp"><span class="faint">${n}</span><span class="col"><span>${t}</span>${w ? `<span class="w">${w}</span>` : ''}</span><span class="faint" style="font-size:12.5px">${z}</span><span class="ck2 on">${ic('check', 13)}</span></div>`).join('')}<div class="row"><button type="button" class="btn pri">Опублікувати 3 глави</button><button type="button" class="btn out">Зберегти чернетками</button></div>`;
const relay = `<div class="card" style="padding:16px;display:flex;flex-direction:column;gap:10px"><span class="state pub"><i></i>Переклад активний</span><p class="dim" style="font-size:14px;line-height:1.5">Переклад стає вільним для продовження, якщо ви позначите його «покинутим», або 3 місяці без нових глав і без відповіді на запит «Хочу продовжити» 14 днів.</p><button type="button" class="btn sm out" style="align-self:flex-start">Позначити покинутим</button></div>
<span class="lab">Запити «Хочу продовжити» · 1</span><div class="rq"><span class="col" style="gap:4px"><b class="strong">Команда «Світанок» · $svitanok</b><span class="faint" style="font-size:13px">хоче продовжити з глави 41 · копія словника · 5 днів тому · лишилось 9 днів на відповідь</span></span><span class="row" style="gap:6px"><button type="button" class="btn sm pri">Дозволити</button><button type="button" class="btn sm out">Відмовити</button></span></div>`;
const data = `<div class="row" style="gap:20px;align-items:flex-start"><span class="cover c1" style="width:140px;height:200px;font-size:15px;padding:14px"><b>Доглядач маяка</b></span><div class="col grow" style="gap:12px"><div class="fm"><label>Назва</label><span class="field">Доглядач маяка</span></div><div class="fm"><label>Інші назви</label><span class="field">The Lighthouse Keeper’s Apprentice</span></div><div class="fm"><label>Автор · мова</label><span class="field">Ейвері Голт · англійська</span></div></div></div>
<div class="fm"><label>Опис</label><div class="ta">Після смерті батька шістнадцятирічна Ліна приїжджає на острів Сірий Ріг учитися в старого доглядача маяка…</div></div>
<div class="fm"><label>Теги</label><div class="row" style="gap:6px;flex-wrap:wrap">${['Фентезі', 'Повільне життя', 'Острів', 'Наставник'].map(t => `<span class="chip on">${t} ${ic('x', 13)}</span>`).join('')}<span class="chip">+ тег</span></div></div>
<div class="col">${[['Оригінал', '<span class="dim">виходить · 62 глави</span>'], ['18+', '<span class="tog"></span>']].map(([t, r]) => `<div class="set-r"><span>${t}</span>${r}</div>`).join('')}</div><button type="button" class="btn pri" style="align-self:flex-start">Зберегти</button>`;
const settings = `<div class="col">${[['Стан перекладу', '<div class="seg" style="min-width:360px"><span class="on">В роботі</span><span>Призупинено</span><span>Завершено</span><span>Закинуто</span></div>'], ['Адреса новели', '<span class="field" style="width:260px">novelka.in.ua/n/dohliadach-maiaka</span>'], ['Дозволити EPUB', '<span class="tog on"></span>'], ['Позначка ШІ', '<span class="dim">ШІ з правками людей · ставиться сама</span>']].map(([t, r]) => `<div class="set-r"><span>${t}</span>${r}</div>`).join('')}</div>
<div class="card" style="padding:16px;display:flex;flex-direction:column;gap:8px;box-shadow:inset 0 0 0 1px var(--del)"><b class="strong">Видалити переклад</b><p class="dim" style="font-size:13.5px;line-height:1.5">Спершу надішлемо код на пошту й у Telegram (6 цифр, 15 хвилин). Переклад піде в кошик: читачі його не бачать, запуски зупиняються. Відновити можна 30 днів.</p><button type="button" class="btn sm out" style="align-self:flex-start;color:var(--del)">Надіслати код</button></div>`;
const trash = `<p class="dim" style="font-size:14px">Глави й переклади в кошику лежать 30 днів, потім стираються. Номер глави лишається порожнім, інші не зсуваються.</p>${[['Глава 44 «Пусті години» · Доглядач маяка', 'видалила marta_k · 2 дні тому · лишилось 28 днів'], ['Переклад «Академія під дощем»', 'видалено 10 днів тому · лишилось 20 днів']].map(([t, m]) => `<div class="rq"><span class="col" style="gap:4px"><b class="strong">${t}</b><span class="faint" style="font-size:13px">${m}</span></span><button type="button" class="btn sm">${ic('undo', 15)} Повернути</button></div>`).join('')}`;
const teams = `${[['С', 'Солона вода', '$solona-voda · ви власниця · 3 людини · 2 переклади'], ['М', 'marta_k', 'особиста команда · 1 переклад']].map(([a, t, m]) => `<div class="rq"><span class="row" style="gap:12px"><span class="av">${a}</span><span class="col"><b class="strong">${t}</b><span class="faint" style="font-size:13px">${m}</span></span></span><a href="ProfileK.dc.html" class="btn sm out">Керувати</a></div>`).join('')}<div class="card" style="padding:16px;display:flex;flex-direction:column;gap:10px;max-width:520px"><b class="strong">Нова команда</b><div class="fm"><label>Назва</label><span class="field">Нічна зміна</span></div><div class="fm"><label>Адреса для згадок</label><span class="field">$nichna-zmina</span><small>чат команди створиться сам і буде синхронним зі складом</small></div><button type="button" class="btn pri" style="align-self:flex-start">Створити</button></div>`;
const MS = ['назви глав', 'структура й томи', 'глави з файлу', 'естафета', 'дані й обкладинка', 'налаштування перекладу', 'кошик', 'мої команди'], MI = ['titles', 'struct', 'file', 'relay', 'data', 'set', 'trash', 'teams'];
const MT = ['Назви глав', 'Структура й томи', 'Глави з файлу', 'Естафета', 'Дані й обкладинка', 'Налаштування перекладу', 'Кошик', 'Мої команди'];
const MM = [7, 8, 9, 10, 11, 11, -1, -1];
const MB = [titles, struct, fromFile, relay, data, settings, trash, teams];
const moreBody = `<div class="page">${MI.map((id, i) => iff(`is.${id}`, `${MM[i] >= 0 ? '<div class="tr-g">' + trMenu(MM[i]) : ''}<div class="col" style="gap:14px;min-width:0;max-width:900px"><h1 class="h1">${MT[i]}</h1>${MB[i]}</div>${MM[i] >= 0 ? '</div>' : ''}`, i === 0)).join('')}</div>`;
export const TrMoreK = { file: 'TrMoreK.dc.html', title: 'Переклад: назви, томи, з файлу, естафета, дані, кошик, команди — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: MS, ids: MI, extra: CSS,
  root: `<div class="{{ cls }}" style="width:1440px;height:900px">${studioFrame({ active: 0, path: 'Доглядач маяка › <b>ще</b>', main: moreBody })}</div>` };
