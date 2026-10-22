// Сторінка новели: телефон (NovelT), планшет (NovelP), комп'ютер (NovelK).
import { ic, iff, kk } from './lib.mjs';
import { NOVEL, COMMENTS } from './content.mjs';
import { CHROME_CSS, phoneFrame, deskFrame } from './chrome.mjs';
import { READER_CSS } from './reader.mjs';

const NOVEL_CSS = `
.nov{display:flex;flex-direction:column;gap:18px;padding:20px 16px 28px}
.nov-side{display:flex;flex-direction:column;gap:16px;min-width:0}
.nov-hero{display:grid;grid-template-columns:112px minmax(0,1fr);gap:16px;align-items:start}
.nov-cov{width:112px;height:162px;font-size:12px}
.nov-id{display:flex;flex-direction:column;gap:6px;min-width:0}
.nov-t{font:var(--wh) 24px/1.15 var(--f-h);color:var(--strong);text-wrap:balance}
.nov-o{font-size:13.5px;color:var(--dim);line-height:1.35}
.nov-m{font-size:13px;color:var(--dim);line-height:1.4}
.nov-m a{color:var(--acc)}
.nov-st{display:flex;flex-wrap:wrap;gap:4px 14px;font-size:13px;color:var(--dim)}
.nov-st b{color:var(--strong);font-weight:600}
.nov-pr{display:flex;flex-direction:column;gap:6px;font-size:12.5px;color:var(--dim)}
.nov-act{display:flex;gap:8px;align-items:center}
.nov-act .btn.pri{flex:1}
.nov-act .ib{box-shadow:inset 0 0 0 1px var(--line);border-radius:var(--rb)}
.k-pal .nov-act .ib{box-shadow:none}
.nov-tr{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);font-size:13.5px}
.k-pal .nov-tr{background:transparent;box-shadow:none;border-top:1px dotted var(--line);border-bottom:1px dotted var(--line);border-radius:0;padding:10px 0}
.tabs{display:flex;gap:2px;border-bottom:1px solid var(--line);overflow-x:auto;scrollbar-width:none}
.tab{height:44px;padding:0 12px;display:flex;align-items:center;gap:6px;font-size:14.5px;color:var(--dim);white-space:nowrap;box-shadow:inset 0 -2px 0 transparent}
.tab span{font-size:12px;color:var(--faint)}
.tab.on{color:var(--strong);font-weight:600;box-shadow:inset 0 -2px 0 var(--acc)}
.ph .tabs{overflow:visible}
.ph .tab{flex:1;justify-content:center;padding:0 2px;font-size:13.5px}
.ph .tab span{display:none}
.nov-tr .acc{white-space:nowrap;display:flex;align-items:center;gap:2px}
.k-pult .tabs{gap:0}
.k-pult .tab{font:500 11.5px/1 var(--f-num);letter-spacing:.04em;text-transform:uppercase;height:40px;box-shadow:inset -1px 0 0 var(--line)}
.k-pult .tab.on{box-shadow:inset 0 -2px 0 var(--acc),inset -1px 0 0 var(--line);background:var(--s1)}
.k-pal .tabs{justify-content:center;gap:18px;border-bottom:0;border-top:1px solid var(--line);padding-top:4px}
.k-pal .tab{padding:0;font-variant:all-small-caps;letter-spacing:.1em;font-size:16px}
.k-pal .tab.on{box-shadow:none;color:var(--acc);font-weight:500}
.k-obl .tabs{border:0;gap:6px;background:var(--s1);border-radius:24px;padding:4px}
.k-obl .tab{height:40px;border-radius:20px;font-weight:700;font-size:13.5px;padding:0 14px}
.k-obl .tab.on{background:var(--acc);color:var(--onacc);box-shadow:none}
.k-obl .tab.on span{color:var(--onacc)}
.pane{display:flex;flex-direction:column;gap:12px}
.chs-bar{display:flex;align-items:center;justify-content:space-between;gap:10px}
.skip{display:flex;flex-direction:column;gap:8px;padding:12px;font-size:13.5px}
.k-pal .skip{padding:12px 0}
.skip .row a{color:var(--acc);font-weight:600;font-size:13.5px}
.vol-h{display:flex;align-items:center;gap:8px;padding:10px 0 6px;font-weight:600;color:var(--strong)}
.vol-h span{margin-left:auto;font-weight:400;font-size:12.5px;color:var(--faint)}
.k-pal .vol-h{font:600 19px/1.2 var(--f-h)}
.k-pal .vol-h span{font:13px var(--f-ui)}
.k-obl .vol-h{font:700 14px/1.2 var(--f-h)}
.k-pult .vol-h{font:500 12px/1 var(--f-num);text-transform:uppercase;letter-spacing:.06em;color:var(--acc2)}
.chr{display:grid;grid-template-columns:34px minmax(0,1fr) auto 20px;gap:8px;align-items:center;padding:11px 4px;border-bottom:1px solid var(--line);font-size:14.5px}
.chr .n{color:var(--faint);font-variant-numeric:tabular-nums;font-size:13px}
.chr .d{font-size:12px;color:var(--faint)}
.chr.read .t{color:var(--dim)}
.chr .ok{color:var(--acc2)}
.chr.cur{color:var(--strong);font-weight:600}
.chr.cur .n{color:var(--acc)}
.chr .here{display:block;font-size:12px;font-weight:500;color:var(--acc)}
.k-pult .chr{padding:9px 6px;font-size:14px}
.k-pult .chr .n{font-family:var(--f-num);font-size:12px}
.k-pult .chr .d{font-family:var(--f-num);font-size:11.5px}
.k-pult .chr.cur{background:var(--s1);box-shadow:inset 2px 0 0 var(--acc)}
.k-pal .chr{border-bottom:1px dotted var(--line);grid-template-columns:34px auto minmax(20px,1fr) auto 20px}
.k-pal .chr .n{font:600 18px/1 var(--f-h)}
.k-pal .chr::after{content:"";order:2;align-self:end;margin-bottom:6px;border-bottom:1px dotted var(--faint);grid-column:3;grid-row:1}
.k-pal .chr .t{grid-column:2}
.k-pal .chr .d{grid-column:4}
.k-pal .chr .ok{grid-column:5}
.k-obl .chr{border:0;border-radius:18px;padding:12px 14px;background:var(--s1);margin-bottom:6px}
.k-obl .chr .n{font:700 13px/1 var(--f-num)}
.k-obl .chr.cur{background:var(--acc);color:var(--onacc)}
.k-obl .chr.cur .n,.k-obl .chr.cur .here,.k-obl .chr.cur .d{color:var(--onacc)}
.vol-c{display:flex;align-items:center;gap:8px;padding:12px 0;border-top:1px solid var(--line);color:var(--dim);font-size:14px}
.vol-c span{margin-left:auto;font-size:12.5px;color:var(--faint)}
.pager{display:flex;align-items:center;justify-content:space-between;font-size:13px;color:var(--dim);padding-top:4px}
.pager .row a{width:34px;height:34px;display:grid;place-items:center;border-radius:var(--rb);box-shadow:inset 0 0 0 1px var(--line);font-size:13px}
.pager .row a.on{background:var(--acc);color:var(--onacc);box-shadow:none}
.about p{font:16px/1.65 var(--f-rd);color:var(--read)}
.kv{margin:0;display:grid;grid-template-columns:120px minmax(0,1fr);gap:10px 12px;font-size:14px}
.kv dt{color:var(--faint)} .kv dd{margin:0;color:var(--tx)}
.k-pult .kv dt{font:500 11.5px/1.6 var(--f-num);text-transform:uppercase;letter-spacing:.05em}
.k-pal .kv dt{font-variant:all-small-caps;letter-spacing:.08em;font-size:15px}
.stars{display:flex;gap:4px;color:var(--acc)}
.stars .off{color:var(--line)}
.tags{display:flex;flex-wrap:wrap;gap:6px}
.chars{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.chc{display:flex;align-items:center;gap:10px;padding:10px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.chc .av{width:44px;height:44px;font-size:18px}
.chc b{display:block;font-size:14.5px;color:var(--strong)}
.chc span{font-size:12.5px;color:var(--faint)}
.k-pal .chc{background:transparent;box-shadow:none;border-bottom:1px dotted var(--line);border-radius:0;padding:10px 0}
.k-obl .chc{box-shadow:none}
.menu{display:flex;flex-direction:column}
.menu a,.menu button{display:flex;align-items:center;gap:12px;padding:13px 4px;font-size:15px;border-bottom:1px solid var(--line);width:100%}
.menu small{display:block;font-size:12.5px;color:var(--faint)}
.menu .dis{color:var(--faint)}
.menu .i{color:var(--dim)}
.menu .grp{font-size:12px;color:var(--faint);padding:14px 4px 4px;border:0}
.k-obl .menu a,.k-obl .menu button{border:0;border-radius:16px;padding:12px}
.k-pal .menu a,.k-pal .menu button{border-bottom:1px dotted var(--line)}
/* комп'ютер і планшет */
.dk .nov,.tb .nov{display:grid;grid-template-columns:300px minmax(0,1fr);gap:40px;padding:36px 48px 60px;align-items:start;max-width:1280px}
.tb .nov{grid-template-columns:240px minmax(0,1fr);gap:28px;padding:28px 28px 40px}
.dk .nov-hero,.tb .nov-hero{grid-template-columns:minmax(0,1fr)}
.dk .nov-cov{width:300px;height:430px;font-size:22px;padding:20px}
.tb .nov-cov{width:240px;height:344px;font-size:18px;padding:16px}
.dk .nov-t{font-size:30px}
.k-pult.dk .nov,.k-pult.tb .nov{grid-template-columns:340px minmax(0,1fr);gap:0;padding:0;max-width:none;height:100%}
.k-pult.tb .nov{grid-template-columns:280px minmax(0,1fr)}
.k-pult.dk .nov-side,.k-pult.tb .nov-side{padding:20px;border-right:1px solid var(--line);height:100%;overflow:auto;background:var(--s1)}
.k-pult.dk .nov-main,.k-pult.tb .nov-main{padding:0 24px 40px;overflow:auto;height:100%}
.k-pult.dk .nov-cov{width:120px;height:172px;font-size:12px;padding:10px}
.k-pult.tb .nov-cov{width:110px;height:158px;font-size:11px;padding:8px}
.k-pult.dk .nov-hero,.k-pult.tb .nov-hero{grid-template-columns:auto minmax(0,1fr)}
.k-pult .nov-t{font-size:22px}
.k-pal.dk .nov,.k-pal.tb .nov{grid-template-columns:minmax(0,1fr);max-width:920px;margin:0 auto;gap:24px}
.k-pal.dk .nov-hero,.k-pal.tb .nov-hero{display:flex;flex-direction:column;align-items:center;text-align:center;gap:14px}
.k-pal.dk .nov-cov,.k-pal.tb .nov-cov{width:150px;height:216px;font-size:15px;padding:14px}
.k-pal.dk .nov-id,.k-pal.tb .nov-id{align-items:center}
.k-pal .nov-t{font-size:30px;font-weight:600}
.k-pal.dk .nov-t{font-size:52px}
.k-pal.dk .nov-st,.k-pal.tb .nov-st{justify-content:center}
.k-pal.dk .nov-pr,.k-pal.tb .nov-pr{width:420px}
.k-pal.dk .nov-act,.k-pal.tb .nov-act{justify-content:center}
.k-pal.dk .nov-act .btn.pri,.k-pal.tb .nov-act .btn.pri{flex:none;padding:0 32px}
.k-pal.dk .nov-tr,.k-pal.tb .nov-tr{justify-content:center}
.k-obl.dk .nov{grid-template-columns:minmax(0,1fr);gap:20px;padding:6px 10px 40px 0}
.k-obl.dk .nov-side{display:grid;grid-template-columns:260px minmax(0,1fr);gap:28px;padding:26px;border-radius:28px;background:var(--s1)}
.k-obl.dk .nov-hero{display:contents}
.k-obl.dk .nov-cov{width:260px;height:372px;grid-row:1/4;border-radius:22px}
.k-obl .nov-t{font-size:26px;letter-spacing:-.02em}
.k-obl.dk .nov-t{font-size:44px}
.k-obl.dk .nov-act{grid-column:2}
.k-obl.dk .nov-act .btn.pri{flex:none;padding:0 30px}
.k-obl.dk .nov-tr{grid-column:2;background:var(--s2);box-shadow:none}
.k-obl .nov-act .ib{box-shadow:none;background:var(--s2)}
.k-obl.tb .nov{grid-template-columns:minmax(0,1fr);padding:4px 4px 40px 0}
.k-obl.tb .nov-side{padding:20px;border-radius:26px;background:var(--s1)}
.k-obl.tb .nov-hero{grid-template-columns:180px minmax(0,1fr)}
.k-obl.tb .nov-cov{width:180px;height:258px}
.k-zv.tb .nov,.k-pal.tb .nov{padding:24px}
.dk .chars{grid-template-columns:repeat(3,minmax(0,1fr))}
.dk .chr{grid-template-columns:44px minmax(0,1fr) 120px 110px 20px}
.dk .chr .s{display:block}
.chr .s{display:none;font-size:12.5px;color:var(--faint)}
.k-pal.dk .chr{grid-template-columns:44px auto minmax(20px,1fr) 110px 20px}
.k-pal.dk .chr .s{display:none}
.mpop{top:300px;right:140px;width:340px;padding:8px 14px}
.dk .about-g{display:grid;grid-template-columns:minmax(0,1fr) 300px;gap:32px}
`;

const chapRows = (dk) => [
  [9, 'Острів без годинників', '3 тижні тому', 'read'], [10, 'Сім сходинок', '3 тижні тому', 'read'], [11, 'Пором о сьомій', '2 тижні тому', 'read'],
  [12, 'Лінза, що пам’ятає світло', '12 днів тому', 'cur'], [13, 'Журнал доглядача', '10 днів тому', ''], [14, 'Сигнал із півночі', '9 днів тому', ''],
  [15, 'Люди з туману', '7 днів тому', ''], [16, 'Друга лампа', '5 днів тому', '']
].map(([n, t, d, c]) => `<a href="ReaderT.dc.html" class="chr ${c}"><span class="n">${n}</span><span class="t ell">${t}${c === 'cur' ? '<span class="here">тут ви зупинились</span>' : ''}</span>${dk ? `<span class="s">${c === 'read' ? 'прочитано' : c === 'cur' ? '34 %' : ''}</span>` : ''}<span class="d">${d}</span>${c === 'read' ? `<span class="ok">${ic('check', 16)}</span>` : '<span></span>'}</a>`).join('');

const paneChapters = (dk) => `<div class="pane">
<div class="chs-bar"><div class="seg" style="flex:none"><span class="on">По томах</span><span>Усі глави</span></div><button type="button" class="btn sm out">${ic('sort', 16)} Від першої</button></div>
<div class="card skip"><span><b class="strong">Пропущено 2 глави</b> <span class="dim">— 7 і 8</span></span><div class="row" style="gap:16px"><a href="ReaderT.dc.html">Читати з першої непрочитаної</a><a href="#">Позначити прочитаними</a></div></div>
<div class="vol-h">${ic('down', 16)} Том 1 · ${NOVEL.volumes[0].t}<span>1–24 · прочитано 11</span></div>
<div>${chapRows(dk)}</div>
<div class="pager"><span>сторінка 1 з 2 · глави 1–20</span><span class="row" style="gap:4px"><a href="#" class="on">1</a><a href="#">2</a><a href="#" aria-label="Наступна сторінка">${ic('next', 16)}</a></span></div>
<div class="vol-c">${ic('next', 16)} Том 2 · ${NOVEL.volumes[1].t}<span>25–40 · нових 16</span></div>
</div>`;

const paneAbout = `<div class="pane about-g"><div class="col" style="gap:14px"><div class="about col" style="gap:10px">${NOVEL.about.map(p => `<p>${p}</p>`).join('')}</div>
<div class="tags">${NOVEL.tags.map(t => `<a href="CatalogT.dc.html" class="chip">${t}</a>`).join('')}</div></div>
<dl class="kv">
<dt>Ваша оцінка</dt><dd><span class="stars">${[1, 2, 3, 4].map(() => ic('star', 18)).join('')}<span class="off">${ic('star', 18)}</span></span></dd>
<dt>Інші назви</dt><dd>${NOVEL.orig}</dd>
<dt>Автор</dt><dd>${NOVEL.author} (${NOVEL.authorOrig})</dd>
<dt>Оригінал</dt><dd>англійська · виходить · ${NOVEL.origChapters} глави · <a href="#" class="acc">відкрити ↗</a></dd>
<dt>Переклад</dt><dd>ШІ з правками людей · в роботі</dd>
<dt>Команда</dt><dd><a href="TeamK.dc.html" class="acc">«${NOVEL.team}»</a> · 3 людини</dd>
<dt>Книжкою</dt><dd><a href="#" class="row acc" style="display:inline-flex">${ic('dl', 16)} EPUB — уся новела чи том</a></dd>
</dl></div>`;

const CH = [['Л', 'Ліна', 48, ''], ['О', 'майстер Орест', 41, 'b'], ['Я', 'Сірий Ян', 12, ''], ['Т', 'Тереза Вальд', 7, 'b'], ['М', 'Мірра', 5, ''], ['К', 'капітан Дорош', 4, 'b']];
const paneChars = (n) => `<div class="pane"><div class="chs-bar"><div class="seg" style="flex:none"><span class="on">Частіше згадані</span><span>За іменем</span><span>За появою</span></div></div>
<div class="chars">${CH.slice(0, n).map(([l, nm, m]) => `<a href="ReaderPanelsT.dc.html" class="chc"><span class="av">${l}</span><span class="col"><b>${nm}</b><span>${m} згадок · до глави 12</span></span></a>`).join('')}</div>
<label class="row" style="gap:10px;font-size:14px;color:var(--dim)"><span class="tog" role="switch" aria-checked="false"></span>Показати всіх — можливі спойлери</label></div>`;

const cmt = (c) => `<div class="cm"><span class="av">${c.av}</span><div class="col" style="gap:4px"><div class="cm-h"><b>${c.who}</b><span class="faint">${c.when} · до глави 12</span></div>${c.quote ? `<div class="cm-q">«${c.quote}»</div>` : ''}<p class="cm-t">${c.spoiler ? c.text.replace(/\|\|(.+?)\|\|/, '<span class="cm-sp">$1</span>') : c.text}</p><div class="cm-f"><span>${ic('up2', 14)} ${c.votes}</span><span>${ic('reply', 14)} Відповісти${c.replies ? ' · ' + c.replies : ''}</span><span>${ic('flag', 14)}</span></div></div></div>`;
const paneDisc = `<div class="pane"><div class="field" style="height:auto;min-height:var(--hb);padding:10px 12px">Що думаєте про переклад? Можна згадати @нік чи $команду</div><div class="chs-bar"><div class="seg" style="flex:none"><span class="on">Найкращі</span><span>Нові</span></div><span class="faint" style="font-size:13px">86 коментарів</span></div>${COMMENTS.map(cmt).join('<div class="hr"></div>')}</div>`;

const menuItems = `<div class="menu">
<a href="#">${ic('layers', 20)}<span class="grow">Інші переклади<small>«Світанок» · з глави 41</small></span>${ic('next', 16)}</a>
<a href="StudioNewK.dc.html">${ic('pen', 20)}<span class="grow">Перекласти самому<small>свій переклад з глави 1 чи з 41</small></span></a>
<span class="dis row" style="padding:13px 4px;border-bottom:1px solid var(--line);gap:12px">${ic('relay', 20)}<span class="grow">Продовжити переклад<small>переклад активний — естафета ще недоступна</small></span></span>
<button type="button">${ic('undo', 20)}<span class="grow">Скинути прогрес читання</span></button>
<a href="#">${ic('dl', 20)}<span class="grow">Завантажити EPUB<small>уся новела, том 1 чи том 2</small></span></a>
<a href="ReaderPanelsT.dc.html">${ic('flag', 20)}<span class="grow">Поскаржитися</span></a>
<span class="grp">Ви в команді «Солона вода»</span>
<a href="StudioK.dc.html">${ic('gear', 20)}<span class="grow">Керувати в Студії</span></a>
</div>`;

const hero = (dk) => `<div class="nov-side"><section class="nov-hero">
<div class="cover nov-cov"><b>${NOVEL.title}</b></div>
<div class="nov-id">
<h1 class="nov-t">${NOVEL.title}</h1>
<p class="nov-o">${NOVEL.orig} · ${NOVEL.author}</p>
<p class="nov-m">переклад з англійської · <a href="TeamK.dc.html">$solona-voda</a> · ШІ з правками</p>
<div class="nov-st"><span>${ic('star', 14)}</span><span><b>${NOVEL.rating}</b> · ${NOVEL.votes} оцінок</span><span><b>${NOVEL.readers}</b> читають</span></div>
<div class="nov-pr"><div class="prog"><i style="width:64%"></i></div><span>Перекладено 40 з 62 · оригінал виходить</span></div>
</div></section>
<div class="nov-act"><a href="ReaderT.dc.html" class="btn pri">Продовжити · гл. 12</a><button type="button" class="ib" aria-label="У бібліотеці: Читаю">${ic('bookmark', 20)}</button><button type="button" class="ib on" aria-label="Повідомляти про нові глави" aria-pressed="true">${ic('bell', 20)}</button><button type="button" class="ib" onClick="{{ go.menu }}" aria-label="Ще">${ic('more', 20)}</button></div>
<div class="nov-tr">${ic('users', 18)}<span class="grow">Переклад «${NOVEL.team}»<span class="faint"> · основний</span></span><span class="acc">ще 1 ${ic('down', 14)}</span></div>
</div>`;

const tabs = `<nav class="tabs" role="tablist">${[['ch', 'Глави', '40'], ['about', 'Про новелу', ''], ['chars', 'Персонажі', '6'], ['disc', 'Обговорення', '86']].map(([id, l, n]) => `<button type="button" role="tab" class="tab" onClick="{{ go.${id} }}">${l}${n ? ` <span>${n}</span>` : ''}</button>`).join('')}</nav>`;
// вкладка підсвічується через стан: окремі розмітки
const tabsFor = (on) => tabs.replace(new RegExp(`(class="tab)(" onClick="\\{\\{ go\\.${on} \\}\\}")`), '$1 on" aria-selected="true$2');

const loadPane = `<div class="pane" aria-busy="true"><span class="sk" style="height:34px;width:60%"></span>${[1, 2, 3, 4, 5, 6].map(() => `<div class="row" style="gap:12px;padding:8px 0"><span class="sk" style="width:24px;height:14px"></span><span class="sk" style="height:14px;flex:1"></span><span class="sk" style="width:60px;height:12px"></span></div>`).join('')}</div>`;
const loadHero = `<div class="nov-side"><section class="nov-hero"><span class="sk nov-cov" style="border-radius:var(--rcard)"></span><div class="nov-id" style="gap:10px"><span class="sk" style="height:26px;width:85%"></span><span class="sk" style="height:14px;width:60%"></span><span class="sk" style="height:14px;width:70%"></span><span class="sk" style="height:14px;width:40%"></span></div></section><span class="sk" style="height:var(--hb)"></span></div>`;

const STANY = ['Глави', 'Про новелу', 'Персонажі', 'Обговорення', 'меню ⋯', 'заготовка'];
const IDS = ['ch', 'about', 'chars', 'disc', 'menu', 'load'];
const ALIAS = { chlike: ['ch', 'menu'], ready: ['ch', 'about', 'chars', 'disc', 'menu'] };

const content = (dk) => `<div class="nov">
${iff('ready', hero(dk), true)}${iff('is.load', loadHero)}
<div class="nov-main col" style="gap:16px">
${iff('chlike', tabsFor('ch') + paneChapters(dk), true)}
${iff('is.about', tabsFor('about') + paneAbout)}
${iff('is.chars', tabsFor('chars') + paneChars(dk ? 6 : 4))}
${iff('is.disc', tabsFor('disc') + paneDisc)}
${iff('is.load', loadPane)}
</div></div>`;

const sheetMenu = `<div class="scrim"></div><div class="sheet" role="dialog" aria-label="Ще"><div class="grab"></div><div class="sh-h"><b class="h2 grow">${NOVEL.title}</b><button type="button" class="ib" onClick="{{ go.ch }}" aria-label="Закрити">${ic('x', 20)}</button></div><div class="sh-b" style="gap:0">${menuItems}</div></div>`;
const popMenu = `<div class="pop mpop" role="menu">${menuItems}</div>`;

const CSS = READER_CSS + CHROME_CSS + NOVEL_CSS;

export const NovelT = {
  file: 'NovelT.dc.html', title: 'Новела — телефон', w: 390, h: 844, dev: 'ph', stany: STANY, ids: IDS, alias: ALIAS, extra: CSS,
  root: `<div class="{{ cls }}" style="width:390px;height:844px">
${phoneFrame({ active: 1, back: 'CatalogT.dc.html', title: 'Доглядач маяка', actions: `<button type="button" class="ib" aria-label="Поділитися">${ic('share', 20)}</button>`, main: content(false) })}
${iff('is.menu', sheetMenu)}
</div>`
};
export const NovelP = {
  file: 'NovelP.dc.html', title: 'Новела — планшет', w: 834, h: 1194, dev: 'tb', stany: STANY, ids: IDS, alias: ALIAS, extra: CSS,
  root: `<div class="{{ cls }}" style="width:834px;height:1194px">
${deskFrame({ active: 1, path: 'каталог › <b>Доглядач маяка</b>', main: content(false) })}
${iff('is.menu', popMenu)}
</div>`
};
export const NovelK = {
  file: 'NovelK.dc.html', title: 'Новела — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: STANY, ids: IDS, alias: ALIAS, extra: CSS,
  root: `<div class="{{ cls }}" style="width:1440px;height:900px">
${deskFrame({ active: 1, path: 'каталог › фентезі › <b>Доглядач маяка</b>', main: content(true) })}
${iff('is.menu', popMenu)}
</div>`
};
