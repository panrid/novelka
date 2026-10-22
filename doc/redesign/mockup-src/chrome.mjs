// Меню й шапки сайту для кожного каркаса: телефон, планшет, комп'ютер.
// Пункти однакові скрізь (рішення 28): Головна · Каталог · Бібліотека · Студія; Сповіщення й Повідомлення окремо.
import { ic, kk, iff } from './lib.mjs';

export const CHROME_CSS = `
.site{height:100%;min-height:0}
.s-main{min-height:0;overflow:auto;scrollbar-width:none;position:relative}
.s-main::-webkit-scrollbar{display:none}
.ph .site{display:flex;flex-direction:column}
.ph .s-main{flex:1}
.dk .site,.tb .site{display:grid;grid-template-columns:minmax(0,1fr);grid-template-rows:auto minmax(0,1fr)}
.k-pult.dk .site,.k-pult.tb .site{grid-template-columns:60px minmax(0,1fr);grid-template-rows:44px minmax(0,1fr)}
.k-pult .s-rail{grid-row:1/3}
.k-obl.dk .site{grid-template-columns:264px minmax(0,1fr);grid-template-rows:minmax(0,1fr);padding:14px 14px 0 14px;gap:14px}
.k-obl.tb .site{grid-template-columns:84px minmax(0,1fr);grid-template-rows:minmax(0,1fr);padding:12px 12px 0 12px;gap:12px}
/* Пульт */
.s-top-p{height:48px;flex:none;display:flex;align-items:center;gap:6px;padding:0 6px 0 12px;border-bottom:1px solid var(--line);background:var(--s0)}
.s-top-p .lg,.s-rail .lg{width:28px;height:28px;display:grid;place-items:center;background:var(--acc);color:var(--onacc);font:600 16px/1 var(--f-num);flex:none}
.s-top-p .t{flex:1;min-width:0;font:12.5px/1 var(--f-num);color:var(--dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.s-top-p .t b{color:var(--tx);font-weight:500}
.s-nav-p{height:60px;flex:none;display:grid;grid-template-columns:repeat(5,1fr);border-top:1px solid var(--line);background:var(--s0)}
.s-nav-p a{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;font:500 10px/1 var(--f-num);letter-spacing:.04em;color:var(--dim);box-shadow:inset -1px 0 0 var(--line);position:relative}
.s-nav-p a:last-child{box-shadow:none}
.s-nav-p a.on{color:var(--acc);box-shadow:inset 0 2px 0 var(--acc),inset -1px 0 0 var(--line)}
.s-nav-p .dot,.s-nav-z .dot,.s-dock .dot{position:absolute;top:8px;left:calc(50% + 6px);min-width:15px;height:15px;padding:0 3px;background:var(--acc);color:var(--onacc);font:600 9.5px/15px var(--f-ui);text-align:center;border-radius:8px}
.s-nav-p .dot{border-radius:0;font-family:var(--f-num)}
.s-rail{background:var(--s0);border-right:1px solid var(--line);display:flex;flex-direction:column;align-items:center;gap:4px;padding:8px 0 10px}
.s-rail .lg{margin:0 0 12px}
.s-rail a,.s-rail button{width:44px;height:44px;display:grid;place-items:center;color:var(--dim);position:relative}
.s-rail a.on{color:var(--strong);background:var(--s2);box-shadow:inset 2px 0 0 var(--acc)}
.s-rail .dot{position:absolute;right:5px;top:6px;min-width:15px;height:15px;padding:0 3px;background:var(--acc);color:var(--onacc);font:500 10px/15px var(--f-num);text-align:center}
.s-rail .sp{flex:1}
.s-rail .av{width:32px;height:32px;font-size:12px}
.s-path{display:flex;align-items:center;gap:10px;padding:0 12px 0 16px;border-bottom:1px solid var(--line);background:var(--s0);font:12.5px/1 var(--f-num);color:var(--dim)}
.s-path b{color:var(--tx);font-weight:500}
.s-path .k{margin-left:auto;height:28px;width:280px;display:flex;align-items:center;gap:8px;padding:0 8px;box-shadow:inset 0 0 0 1px var(--line);color:var(--faint);font-family:var(--f-ui);font-size:13px}
.s-path kbd{margin-left:auto;font:11px/1 var(--f-num);color:var(--dim);box-shadow:inset 0 0 0 1px var(--line);padding:3px 4px}
/* Звичний */
.s-top-z{height:56px;flex:none;display:flex;align-items:center;gap:2px;padding:0 6px 0 16px;border-bottom:1px solid var(--line)}
.lg-z{font:700 22px/1 var(--f-h);color:var(--strong);letter-spacing:-.01em}
.lg-z i{color:var(--acc);font-style:normal}
.s-top-z .t{flex:1;min-width:0;font-weight:600;font-size:15px;color:var(--strong);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;padding-left:4px}
.s-nav-z{height:62px;flex:none;display:grid;grid-template-columns:repeat(5,1fr);border-top:1px solid var(--line);background:var(--bg)}
.s-nav-z a{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;font-size:11px;color:var(--dim);position:relative}
.s-nav-z a.on{color:var(--strong)}
.s-nav-z a.on .i{color:var(--acc)}
.s-head-z{height:64px;display:flex;align-items:center;gap:6px;padding:0 20px;border-bottom:1px solid var(--line)}
.s-head-z nav{display:flex;gap:4px;margin-left:24px}
.s-head-z nav a{height:38px;padding:0 12px;border-radius:10px;display:flex;align-items:center;font-size:14.5px;color:var(--dim)}
.s-head-z nav a.on{background:var(--s2);color:var(--strong);font-weight:600}
.s-head-z .field{width:300px;margin-left:auto;margin-right:8px;height:38px}
.tb .s-head-z .field{width:200px}
.s-head-z .av{width:34px;height:34px;margin-left:6px}
/* Палітурка */
.s-top-a{height:56px;flex:none;display:grid;grid-template-columns:auto 1fr auto;align-items:center;padding:0 6px;border-bottom:1px solid var(--line)}
.lg-a{font:italic 600 26px/1 var(--f-h);color:var(--strong);text-align:center}
.s-top-a .t{text-align:center;font-variant:all-small-caps;letter-spacing:.1em;color:var(--acc);font-size:15px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.s-mast{display:flex;flex-direction:column;align-items:center;padding:14px 28px 0;border-bottom:1px solid var(--line)}
.s-mast .r1{width:100%;display:grid;grid-template-columns:1fr auto 1fr;align-items:center}
.s-mast .r1 .l{display:flex;gap:16px;color:var(--dim);font-size:15px;align-items:center}
.s-mast .r1 .r{display:flex;gap:2px;justify-content:flex-end;align-items:center}
.s-mast .lg-a{font-size:38px}
.s-mast nav{display:flex;gap:26px;padding:10px 0 12px}
.s-mast nav a{font-variant:all-small-caps;letter-spacing:.12em;font-size:17px;color:var(--dim)}
.s-mast nav a.on{color:var(--acc);border-bottom:1px solid var(--acc)}
/* Обкладинка */
.s-top-o{height:64px;flex:none;display:flex;align-items:center;gap:6px;padding:0 12px 0 20px}
.lg-o{font:800 20px/1 var(--f-h);color:var(--strong);letter-spacing:-.02em}
.lg-o i{color:var(--acc);font-style:normal}
.s-top-o .t{flex:1;min-width:0;font:700 14px/1.2 var(--f-h);color:var(--strong);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.s-dock{position:absolute;left:16px;right:16px;bottom:16px;z-index:6;height:66px;border-radius:33px;background:var(--s2);display:grid;grid-template-columns:repeat(5,1fr);align-items:center;padding:0 7px;box-shadow:0 10px 30px rgba(0,0,0,.35)}
.s-dock a{height:52px;border-radius:26px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;color:var(--dim);font-size:10.5px;font-weight:700;position:relative}
.s-dock a.on{background:var(--acc);color:var(--onacc)}
.k-obl.ph .s-main{padding-bottom:96px}
.s-side{background:var(--s1);border-radius:28px 28px 0 0;padding:22px 14px;display:flex;flex-direction:column;gap:6px;min-height:0;overflow:hidden}
.s-side .lg-o{padding:0 8px 16px}
.s-side a.nv-i{height:44px;border-radius:22px;padding:0 14px;display:flex;align-items:center;gap:12px;font-weight:700;font-size:14.5px;color:var(--dim);position:relative}
.s-side a.nv-i.on{background:var(--acc);color:var(--onacc)}
.s-side .n{margin-left:auto;min-width:22px;height:22px;border-radius:11px;background:var(--acc);color:var(--onacc);font:700 11px/22px var(--f-ui);text-align:center}
.s-side .cont{margin-top:14px;padding:12px;border-radius:20px;background:var(--s2);display:grid;grid-template-columns:48px 1fr;gap:4px 12px;align-items:center}
.s-side .cont .cover{width:48px;height:68px;grid-row:1/3;padding:4px;font-size:0;border-radius:10px}
.s-side .cont b{font-size:13.5px;color:var(--strong)}
.s-side .cont span{font-size:12px;color:var(--dim)}
.s-side .sp{flex:1}
.s-side .me{display:flex;align-items:center;gap:6px;padding-top:8px}
.tb .s-side{padding:18px 10px;align-items:center}
.tb .s-side a.nv-i{width:56px;height:56px;padding:0;justify-content:center;border-radius:28px}
.tb .s-side a.nv-i span,.tb .s-side .cont,.tb .s-side .me .grow{display:none}
.tb .s-side .lg-o{font-size:0;padding:0 0 18px}
.tb .s-side .lg-o i{font-size:30px}
.tb .s-side .me{flex-direction:column}
`;

const NAV = [['home', 'Головна', 'HomeT'], ['compass', 'Каталог', 'CatalogT'], ['lib', 'Бібліотека', 'LibraryT'], ['chat', 'Повідомлення', 'MessagesT'], ['user', 'Я', 'MeT']];
const themeBtn = `<button type="button" class="ib" onClick="{{ toggleLight }}" aria-label="Світла чи темна тема">${iff('dark', ic('sun', 19), true)}${iff('light', ic('moon', 19))}</button>`;
const bell = (n = 3) => `<a href="NotificationsT.dc.html" class="ib" aria-label="Сповіщення, нових ${n}">${ic('bell', 20)}<span class="dot">${n}</span></a>`;
const msgs = (n = 2) => `<a href="MessagesK.dc.html" class="ib" aria-label="Повідомлення, нових ${n}">${ic('chat', 20)}<span class="dot">${n}</span></a>`;

// Телефон: { active: 0..4 | -1, title, back: 'File.dc.html', actions: '<…>' }
export function phoneFrame({ active = 0, title = '', back = '', actions = '', main }) {
  const left = (cls, size = 20) => back ? `<a href="${back}" class="ib" aria-label="Назад">${ic('back', size)}</a>` : '';
  const navItems = (cls) => NAV.map(([i, l, f], n) => `<a href="${f}.dc.html" class="${n === active ? 'on' : ''}"${n === active ? ' aria-current="page"' : ''}>${ic(i, 21)}${cls === 'o' && n !== active ? '' : `<span>${l}</span>`}${n === 3 ? '<span class="dot">2</span>' : ''}</a>`).join('');
  return `<div class="site">
${kk({
  pult: `<header class="s-top-p">${back ? left() : '<span class="lg">н</span>'}<span class="t">${title || '<b>новелка</b>'}</span>${actions}<a href="CatalogT.dc.html" class="ib" aria-label="Пошук">${ic('search', 20)}</a>${bell()}</header>`,
  zv: `<header class="s-top-z">${back ? left('z', 21) : '<span class="lg-z">новелка<i>.</i></span>'}<span class="t">${back ? title : ''}</span>${actions}<a href="CatalogT.dc.html" class="ib" aria-label="Пошук">${ic('search', 21)}</a>${bell()}</header>`,
  pal: `<header class="s-top-a">${back ? left() : `<button type="button" class="ib" aria-label="Меню">${ic('list', 21)}</button>`}${back ? `<span class="t">${title}</span>` : '<span class="lg-a">Новелка</span>'}<span class="row" style="gap:0">${actions}${bell()}</span></header>`,
  obl: `<header class="s-top-o">${back ? left() : '<span class="lg-o">новелка<i>.</i></span>'}<span class="t">${back ? title : ''}</span>${actions}${bell()}</header>`
})}
<main class="s-main">${main}</main>
${kk({
  pult: `<nav class="s-nav-p" aria-label="Меню">${navItems('p')}</nav>`,
  zv: `<nav class="s-nav-z" aria-label="Меню">${navItems('z')}</nav>`,
  pal: '',
  obl: `<nav class="s-dock" aria-label="Меню">${navItems('o')}</nav>`
})}
</div>`;
}

const DNAV = [['home', 'Головна', 'HomeK'], ['compass', 'Каталог', 'CatalogK'], ['lib', 'Бібліотека', 'LibraryK'], ['layers', 'Студія', 'StudioK']];
// Комп'ютер і планшет: { active: 0..3 | -1, path: 'html', main }
export function deskFrame({ active = -1, path = '', main }) {
  const rail = `<nav class="s-rail" aria-label="Меню"><span class="lg">н</span>${DNAV.map(([i, l, f], n) => `<a href="${f}.dc.html" class="${n === active ? 'on' : ''}" aria-label="${l}">${ic(i, 21)}</a>`).join('')}<span class="sp"></span><a href="NotificationsK.dc.html" aria-label="Сповіщення">${ic('bell', 20)}<span class="dot">3</span></a><a href="MessagesK.dc.html" aria-label="Повідомлення">${ic('chat', 20)}<span class="dot">2</span></a>${themeBtn}<span class="av">М</span></nav>`;
  return `<div class="site">
${kk({
  pult: `${rail}<div class="s-path">${path || '<b>новелка</b>'}<span class="k">${ic('search', 15)} Новела, автор, тег<kbd>⌘K</kbd></span></div>`,
  zv: `<header class="s-head-z"><span class="lg-z">новелка<i>.</i></span><nav>${DNAV.map(([i, l, f], n) => `<a href="${f}.dc.html" class="${n === active ? 'on' : ''}">${l}</a>`).join('')}</nav><span class="field">${ic('search', 16)} Новела, автор, тег</span>${bell()}${msgs()}${themeBtn}<span class="av">М</span></header>`,
  pal: `<header class="s-mast"><div class="r1"><span class="l">${ic('search', 18)} Пошук</span><span class="lg-a">Новелка</span><span class="r">${bell()}${msgs()}${themeBtn}<span class="av" style="width:32px;height:32px;font-size:15px;margin-left:6px">М</span></span></div><nav>${DNAV.map(([i, l, f], n) => `<a href="${f}.dc.html" class="${n === active ? 'on' : ''}">${l}</a>`).join('<span class="faint">·</span>')}</nav></header>`,
  obl: `<aside class="s-side"><span class="lg-o">новелка<i>.</i></span>${DNAV.map(([i, l, f], n) => `<a href="${f}.dc.html" class="nv-i ${n === active ? 'on' : ''}">${ic(i, 20)}<span>${l}</span></a>`).join('')}<div class="cont"><span class="cover"><b>Доглядач маяка</b></span><b>Продовжити</b><span>Доглядач маяка · гл. 12</span></div><span class="sp"></span><div class="me"><span class="av">М</span><span class="grow" style="font-weight:700;font-size:14px">marta_k</span>${bell()}${msgs()}${themeBtn}</div></aside>`
})}
<main class="s-main">${main}</main>
</div>`;
}
