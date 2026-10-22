// Решта екранів читача: головна, каталог, бібліотека, «Що перекласти», профіль і команда, вхід,
// помилки сторінок, правила й «Для розробників». Телефон (…T) і комп'ютер (…K).
import { ic, iff } from './lib.mjs';
import { NOVELS, TAGS } from './content.mjs';
import { CHROME_CSS, phoneFrame, deskFrame } from './chrome.mjs';
import { READER_CSS } from './reader.mjs';

export const SITE_CSS = `
.page{display:flex;flex-direction:column;gap:26px;padding:20px 16px 32px;min-width:0}
.dk .page{padding:30px 44px 60px;max-width:1340px}
.sec{display:flex;flex-direction:column;gap:12px;min-width:0}
.sec-h{display:flex;align-items:baseline;justify-content:space-between;gap:12px}
.sec-h .more{font-size:13.5px;color:var(--acc);white-space:nowrap}
.k-pult .sec-h{border-bottom:1px solid var(--line);padding-bottom:8px}
.k-pult .sec-h .h2{font:500 12px/1 var(--f-num);letter-spacing:.08em;text-transform:uppercase;color:var(--acc2)}
.k-pult .sec-h .more{font:500 12px/1 var(--f-num)}
.k-pal .sec-h{border-bottom:1px solid var(--line);padding-bottom:6px}
.k-pal .sec-h .more{font-variant:all-small-caps;letter-spacing:.08em;font-size:15px}
.k-obl .sec-h .h2{font-size:19px}
.nl{display:grid;grid-template-columns:44px minmax(0,1fr) auto;gap:12px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line)}
.nl .cover{width:44px;height:64px;font-size:0;padding:0}
.nl b{font-size:15px;color:var(--strong);font-weight:600;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.nl .sub{font-size:13px;color:var(--dim);display:block}
.nl .r{font-size:12.5px;color:var(--faint);text-align:right;white-space:nowrap}
.k-pult .nl{grid-template-columns:32px minmax(0,1fr) auto;padding:7px 0}
.k-pult .nl .cover{width:32px;height:46px}
.k-pult .nl b{font-size:14px;font-weight:500}
.k-pult .nl .r,.k-pult .nl .sub{font-family:var(--f-num);font-size:11.5px}
.k-pal .nl{border-bottom:1px dotted var(--line)}
.k-pal .nl b{font:600 18px/1.2 var(--f-h)}
.k-obl .nl{border:0;background:var(--s1);border-radius:20px;padding:10px 14px 10px 10px;margin-bottom:6px}
.k-obl .nl .cover{border-radius:10px}
.shelf{display:grid;grid-auto-flow:column;grid-auto-columns:118px;gap:12px;overflow-x:auto;scrollbar-width:none;margin:0 -16px;padding:0 16px}
.dk .shelf{grid-auto-flow:row;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));grid-auto-columns:auto;overflow:visible;margin:0;padding:0}
.cc{display:flex;flex-direction:column;gap:6px;min-width:0}
.cc .cover{width:100%;height:auto;aspect-ratio:2/3;font-size:12px}
.cc b{font-size:13.5px;color:var(--strong);font-weight:600;line-height:1.3}
.cc span{font-size:12px;color:var(--faint)}
.k-pal .cc b{font:600 16px/1.2 var(--f-h)}
.k-pult .cc b{font-weight:500}
.cont-c{display:grid;grid-template-columns:64px minmax(0,1fr) auto;gap:4px 14px;padding:14px;align-items:center}
.cont-c .cover{width:64px;height:92px;grid-row:1/4;font-size:0;padding:0}
.cont-c b{font:var(--wh) 18px/1.2 var(--f-h);color:var(--strong)}
.cont-c .btn{grid-column:3;grid-row:1/4}
.ph .cont-c{grid-template-columns:64px minmax(0,1fr)}
.ph .cont-c .btn{grid-column:2;grid-row:auto;justify-self:start;margin-top:6px}
.k-pal .cont-c{padding:14px 0}
.k-obl .cont-c{background:var(--acc);color:var(--onacc);border-radius:26px}
.k-obl .cont-c b,.k-obl .cont-c .dim{color:var(--onacc)}
.k-obl .cont-c .prog{background:color-mix(in srgb,var(--onacc) 25%,transparent)}
.k-obl .cont-c .prog>i{background:var(--onacc)}
.k-obl .cont-c .btn{background:var(--onacc);color:var(--acc)}
.nl .cover b,.cont-c .cover b,.li .cover b,.res .cover b,.thumb b{display:none}
.thumb{width:40px;height:58px;padding:0}
.k-obl .cont-c .lab{color:var(--onacc);opacity:.75}
.tagc{display:flex;flex-wrap:wrap;gap:8px}
.tagc .chip span{color:var(--faint);font-size:11.5px}
.home-g{display:flex;flex-direction:column;gap:26px;min-width:0}
.dk .home-g{display:grid;grid-template-columns:minmax(0,1.35fr) minmax(0,1fr);gap:48px;align-items:start}
.hello{display:flex;flex-direction:column;gap:8px;padding:16px}
.k-pal .hello{text-align:center;align-items:center;padding:16px 0}
/* каталог */
.cat-bar{display:flex;gap:8px;align-items:center}
.cat-bar .field{flex:1}
.cat-g{display:flex;flex-direction:column;gap:16px;min-width:0}
.dk .cat-g{display:grid;grid-template-columns:270px minmax(0,1fr);gap:36px;align-items:start}
.res{display:grid;grid-template-columns:72px minmax(0,1fr);gap:14px;padding:14px 0;border-bottom:1px solid var(--line)}
.res .cover{width:72px;height:104px;font-size:0;padding:0}
.res b{font:var(--wh) 17px/1.25 var(--f-h);color:var(--strong)}
.res .sub{font-size:13px;color:var(--dim)}
.res .meta{font-size:12.5px;color:var(--faint);display:flex;flex-wrap:wrap;gap:4px 12px}
.res .meta .acc{font-weight:600}
.res .chip{height:24px;font-size:12px;padding:0 9px}
.k-pult .res{grid-template-columns:52px minmax(0,1fr);padding:10px 0}
.k-pult .res .cover{width:52px;height:75px}
.k-pult .res b{font:600 15px/1.25 var(--f-ui)}
.k-pult .res .meta{font-family:var(--f-num);font-size:11.5px}
.k-pal .res{border-bottom:1px dotted var(--line)}
.k-obl .res{border:0;background:var(--s1);border-radius:22px;padding:12px;margin-bottom:8px}
.k-obl .res .cover{border-radius:12px}
.k-obl.dk .res-l{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.k-obl.dk .res{margin:0}
.dk .res-l .res{grid-template-columns:84px minmax(0,1fr)}
.dk .res-l .res .cover{width:84px;height:122px}
.k-pult.dk .res-l .res{grid-template-columns:52px minmax(0,1fr)}
.k-pult.dk .res-l .res .cover{width:52px;height:75px}
.flt{display:flex;flex-direction:column;gap:18px}
.flt-g{display:flex;flex-direction:column;gap:8px}
.ck{display:flex;align-items:center;gap:10px;font-size:14px}
.ck i{width:18px;height:18px;border-radius:4px;box-shadow:inset 0 0 0 1.5px var(--dim);flex:none}
.ck.on i{background:var(--acc);box-shadow:none}
.ck span{margin-left:auto;font-size:12px;color:var(--faint)}
.k-pult .ck i{border-radius:0}
.k-obl .ck i{border-radius:6px}
.rng{display:flex;gap:8px;align-items:center}
.rng .field{height:36px;width:80px}
.sugg{position:absolute;left:16px;right:16px;top:58px;z-index:10;padding:8px;display:flex;flex-direction:column;gap:2px}
.dk .sugg{left:auto;right:auto;width:560px;top:70px}
.sugg a{display:flex;align-items:center;gap:10px;padding:8px;border-radius:var(--rb);font-size:14.5px}
.sugg a.on{background:var(--s2)}
.sugg mark{background:none;color:var(--acc);font-weight:700}
.sugg .lab{padding:8px 8px 2px}
.coll{display:flex;gap:8px;overflow-x:auto;scrollbar-width:none}
.dk .coll{flex-wrap:wrap}
/* бібліотека */
.lib-g{display:flex;flex-direction:column;gap:16px}
.dk .lib-g{display:grid;grid-template-columns:240px minmax(0,1fr);gap:36px;align-items:start}
.lists{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none}
.dk .lists{flex-direction:column;gap:2px}
.dk .lists .chip{justify-content:space-between;height:40px;border-radius:var(--rb);background:transparent;box-shadow:none;font-size:14.5px;color:var(--tx)}
.dk .lists .chip.on{background:var(--s2);color:var(--strong);font-weight:600}
.k-pal.dk .lists .chip{border:0;font-variant:all-small-caps;letter-spacing:.06em;font-size:17px}
.k-pal.dk .lists .chip.on{color:var(--acc)}
.li{display:grid;grid-template-columns:56px minmax(0,1fr);gap:14px;padding:12px 0;border-bottom:1px solid var(--line);align-items:center}
.li .cover{width:56px;height:80px;font-size:0;padding:0}
.li b{font:var(--wh) 16px/1.25 var(--f-h);color:var(--strong)}
.li .prog{margin:8px 0 6px}
.li .row .btn{margin-left:auto}
.k-pal .li{border-bottom:1px dotted var(--line)}
.k-obl .li{border:0;background:var(--s1);border-radius:22px;padding:12px;margin-bottom:8px}
.k-pult .li .prog{height:3px}
.dk .lib-items{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:28px}
.k-obl.dk .lib-items{column-gap:10px}
/* «Що перекласти» */
.pr{display:grid;grid-template-columns:56px minmax(0,1fr);gap:14px;padding:16px 0;border-bottom:1px solid var(--line)}
.vote{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;width:56px;height:62px;border-radius:var(--rb);box-shadow:inset 0 0 0 1px var(--line);font-weight:700;color:var(--strong)}
.vote.on{background:var(--acc);color:var(--onacc);box-shadow:none}
.k-obl .vote{border-radius:18px}
.pr b{font:var(--wh) 17px/1.25 var(--f-h);color:var(--strong)}
.pr p{font-size:14px;line-height:1.5;color:var(--dim)}
.pr .meta{font-size:12.5px;color:var(--faint)}
.k-pal .pr{border-bottom:1px dotted var(--line)}
.k-obl .pr{border:0;background:var(--s1);border-radius:22px;padding:14px;margin-bottom:8px}
.dk .pr-l{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:32px}
.k-obl.dk .pr-l{column-gap:10px}
.fm{display:flex;flex-direction:column;gap:6px}
.fm label{font-size:13.5px;font-weight:600;color:var(--tx)}
.fm small{font-size:12.5px;color:var(--faint)}
.fm .field{color:var(--tx)}
.fm .field.ph{color:var(--faint)}
.fm .ta{min-height:110px;line-height:1.5;color:var(--tx)}
.k-pult .fm label{font:500 11.5px/1 var(--f-num);text-transform:uppercase;letter-spacing:.06em;color:var(--dim)}
.k-pal .fm label{font-variant:all-small-caps;letter-spacing:.08em;font-size:16px;font-weight:500;color:var(--acc)}
.ok-line{display:flex;gap:8px;align-items:center;font-size:13px;color:var(--acc2)}
.form{display:flex;flex-direction:column;gap:16px;max-width:620px}
/* профіль і команда */
.pf-h{display:grid;grid-template-columns:auto minmax(0,1fr);gap:16px;align-items:center}
.pf-h .av{width:80px;height:80px;font-size:32px}
.dk .pf-h .av{width:112px;height:112px;font-size:44px}
.pf-h h1{font:var(--wh) 26px/1.15 var(--f-h);color:var(--strong)}
.lvl{display:inline-flex;align-items:center;gap:6px;height:24px;padding:0 9px;border-radius:var(--rc);background:var(--s2);font-size:12.5px;color:var(--acc);font-weight:600}
.stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px}
.stats div{padding:12px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);display:flex;flex-direction:column;gap:2px}
.stats b{font:var(--wh) 22px/1 var(--f-num);color:var(--strong)}
.stats span{font-size:12px;color:var(--faint)}
.k-pal .stats div{background:transparent;box-shadow:none;border-top:1px solid var(--line);border-radius:0;align-items:center;text-align:center}
.k-obl .stats div{box-shadow:none}
.k-obl .stats b{color:var(--acc)}
.badges{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}
.dk .badges{grid-template-columns:repeat(6,minmax(0,1fr))}
.bdg{display:flex;flex-direction:column;align-items:center;gap:6px;text-align:center;font-size:11.5px;color:var(--dim)}
.bdg i{width:52px;height:52px;border-radius:50%;display:grid;place-items:center;font-style:normal;color:#1a1408;box-shadow:inset 0 0 0 3px rgba(0,0,0,.18)}
.bdg i.g{background:#E8C05A} .bdg i.s{background:#C9CED6} .bdg i.b{background:#C98B5A} .bdg i.n{background:var(--s2);color:var(--faint);box-shadow:inset 0 0 0 1px var(--line)}
.k-pult .bdg i{border-radius:0}
.pf-g{display:flex;flex-direction:column;gap:24px}
.dk .pf-g{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:44px;align-items:start}
.mem{display:grid;grid-template-columns:auto minmax(0,1fr) auto;gap:12px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line);font-size:14.5px}
.mem .r{font-size:12.5px;color:var(--faint)}
/* вхід */
.auth{display:flex;flex-direction:column;gap:16px;padding:28px 20px 40px;max-width:440px;margin:0 auto;width:100%}
.dk .auth{margin-top:40px;padding:36px;background:var(--s1);border-radius:var(--rsheet);box-shadow:inset 0 0 0 1px var(--line)}
.k-pal.dk .auth{background:transparent;box-shadow:none;border-top:1px solid var(--acc);border-bottom:1px solid var(--line);border-radius:0}
.auth h1{font:var(--wh) 26px/1.2 var(--f-h);color:var(--strong)}
.k-pal .auth h1{text-align:center;font-size:34px}
.auth .or{display:flex;align-items:center;gap:10px;font-size:12.5px;color:var(--faint)}
.auth .or::before,.auth .or::after{content:"";flex:1;height:1px;background:var(--line)}
.auth .lnk{font-size:14px;color:var(--dim);text-align:center}
.auth .lnk a{color:var(--acc);font-weight:600}
.g{width:18px;height:18px;border-radius:50%;background:conic-gradient(#EA4335 0 25%,#FBBC05 0 50%,#34A853 0 75%,#4285F4 0);flex:none}
.upl{width:96px;height:96px;border-radius:50%;box-shadow:inset 0 0 0 1.5px var(--dim);display:grid;place-items:center;color:var(--dim);align-self:center}
.k-pult .upl{border-radius:0}
/* помилки */
.err{display:flex;flex-direction:column;align-items:center;text-align:center;gap:14px;padding:90px 24px 40px;max-width:520px;margin:0 auto}
.err .code{font:var(--wh) 64px/1 var(--f-num);color:var(--acc)}
.k-pal .err .code{font-style:italic}
.err b{font:var(--wh) 24px/1.25 var(--f-h);color:var(--strong)}
.err p{font-size:15px;line-height:1.55;color:var(--dim)}
.err .i{color:var(--dim)}
/* правила й для розробників */
.doc-g{display:grid;grid-template-columns:240px minmax(0,1fr);gap:48px;align-items:start}
.doc-toc{display:flex;flex-direction:column;gap:2px;position:sticky;top:0}
.doc-toc a{padding:7px 10px;border-radius:var(--rb);font-size:14px;color:var(--dim)}
.doc-toc a.on{background:var(--s2);color:var(--strong);font-weight:600}
.k-pal .doc-toc a{font-variant:all-small-caps;letter-spacing:.06em;font-size:16px}
.k-pal .doc-toc a.on{background:none;color:var(--acc)}
.doc{display:flex;flex-direction:column;gap:14px;max-width:760px}
.doc h1{font:var(--wh) 32px/1.15 var(--f-h);color:var(--strong)}
.doc h2{font:var(--wh) 21px/1.25 var(--f-h);color:var(--strong);margin-top:14px}
.doc p,.doc li{font-size:15px;line-height:1.6;color:var(--tx)}
.doc ul{display:flex;flex-direction:column;gap:6px;padding-left:18px;list-style:disc}
.doc .quote{padding:12px 16px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);font-size:15px}
.code{font:13px/1.6 var(--f-num, ui-monospace);font-family:'IBM Plex Mono',ui-monospace,monospace;background:var(--s0);color:var(--tx);padding:14px 16px;border-radius:var(--rin);box-shadow:inset 0 0 0 1px var(--line);white-space:pre;overflow-x:auto}
.code .c{color:var(--faint)} .code .s{color:var(--acc2)} .code .k{color:var(--acc)}
.ep{display:grid;grid-template-columns:64px minmax(0,1fr) auto;gap:10px;align-items:center;padding:8px 0;border-bottom:1px solid var(--line);font-size:14px}
.ep .m{font:600 11.5px/1 'IBM Plex Mono',monospace;color:var(--acc2)}
.ep code{font:13px 'IBM Plex Mono',monospace;color:var(--strong)}
.ep span{font-size:12.5px;color:var(--faint)}
`;

const CSS = READER_CSS + CHROME_CSS + SITE_CSS;
const N = Object.fromEntries(NOVELS.map(n => [n.id, n]));
const cov = (n, cls = '') => `<span class="cover ${n.c} ${cls}"><b>${n.t}</b></span>`;
const sec = (title, more, body, href = '#') => `<section class="sec"><div class="sec-h"><h2 class="h2">${title}</h2>${more ? `<a href="${href}" class="more">${more} ›</a>` : ''}</div>${body}</section>`;
const nl = (n, sub, r, href = 'NovelT.dc.html') => `<a href="${href}" class="nl">${cov(n)}<span style="min-width:0"><b>${n.t}</b><span class="sub">${sub}</span></span><span class="r">${r}</span></a>`;
const cc = (n, sub) => `<a href="NovelT.dc.html" class="cc">${cov(n)}<b>${n.t}</b><span>${sub}</span></a>`;
const skelRows = (n = 5) => `<div class="col" style="gap:14px" aria-busy="true">${Array.from({ length: n }, () => `<div class="row" style="gap:12px"><span class="sk" style="width:44px;height:64px;flex:none"></span><span class="col grow" style="gap:8px"><span class="sk" style="height:14px;width:70%"></span><span class="sk" style="height:12px;width:45%"></span></span></div>`).join('')}</div>`;

// ── Головна ──
const contCard = `<div class="card cont-c">${cov(N.n1)}<span class="lab">Продовжити</span><b>Доглядач маяка</b><span class="col" style="gap:6px"><span class="dim" style="font-size:13.5px">Глава 12 · Лінза, що пам’ятає світло · 34 %</span><span class="prog"><i style="width:34%"></i></span></span><a href="ReaderT.dc.html" class="btn pri">Читати далі</a></div>`;
const newCh = sec('Нові глави ваших новел', 'усі', [
  nl(N.n2, 'Глави 71–72 · ШІ з правками', '2 год тому'), nl(N.n1, 'Глава 41 · Друга лампа', 'вчора'), nl(N.n4, 'Глави 22–23', '2 дні тому'), nl(N.n3, 'Побічна історія: «Суп для митаря»', '4 дні тому')].join(''), 'LibraryT.dc.html');
const weekly = sec('Читають цього тижня', 'каталог', `<div class="shelf">${[N.n2, N.n3, N.n1, N.n6, N.n4, N.n5].map(n => cc(n, `★ ${n.r} · ${n.ch} глав`)).join('')}</div>`, 'CatalogT.dc.html');
const newTr = sec('Нові переклади', '', [nl(N.n6, 'Хан Соджін · з корейської', '54 глави'), nl(N.n5, 'Свій твір · @oksana_hai', '15 глав')].join(''));
const tagsSec = sec('Популярні теги', '', `<div class="tagc">${TAGS.slice(0, 8).map(([t, n]) => `<a href="CatalogT.dc.html" class="chip">${t} <span>${n}</span></a>`).join('')}</div>`);
const propSec = sec('Що перекласти далі', '', `<div class="card" style="padding:14px;display:flex;flex-direction:column;gap:10px"><p class="dim" style="font-size:14px;line-height:1.5">Пропонуйте новели й голосуйте — перекладачі беруть те, чого чекають найбільше.</p><a href="ProposalsT.dc.html" class="btn sm" style="align-self:flex-start">Що перекласти · 34</a></div>`);
const guest = `<div class="card hello"><b class="h2">Новели й ранобе українською</b><p class="dim" style="font-size:14px;line-height:1.5">Читайте без реєстрації. Увійдіть, щоб вести бібліотеку, пропонувати правки й коментувати.</p><div class="row"><a href="AuthT.dc.html" class="btn pri">Увійти</a><a href="AuthT.dc.html" class="btn">Зареєструватися</a></div></div>`;
const homeBody = (dk) => `<div class="page">
${iff('is.reader', `<div class="home-g"><div class="col" style="gap:26px">${contCard}${newCh}${dk ? newTr : ''}</div><div class="col" style="gap:26px">${weekly}${tagsSec}${dk ? propSec : newTr + propSec}</div></div>`, true)}
${iff('is.guest', `<div class="home-g"><div class="col" style="gap:26px">${guest}${sec('Нові глави', 'усі', [nl(N.n2, 'Глави 71–72', '2 год тому'), nl(N.n1, 'Глава 41', 'вчора'), nl(N.n4, 'Глави 22–23', '2 дні тому')].join(''))}</div><div class="col" style="gap:26px">${weekly}${tagsSec}</div></div>`)}
${iff('is.load', `<div class="home-g"><div class="col" style="gap:20px"><span class="sk" style="height:120px;border-radius:var(--rcard)"></span>${skelRows(4)}</div><div class="col" style="gap:20px"><span class="sk" style="height:20px;width:40%"></span><div class="row" style="gap:12px">${[1, 2, 3].map(() => '<span class="sk" style="width:118px;height:172px;flex:none"></span>').join('')}</div></div></div>`)}
</div>`;
const HS = ['читач', 'гість', 'заготовка'], HI = ['reader', 'guest', 'load'];
export const HomeT = { file: 'HomeT.dc.html', title: 'Головна — телефон', w: 390, h: 844, dev: 'ph', stany: HS, ids: HI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 0, main: homeBody(false) })}</div>` };
export const HomeK = { file: 'HomeK.dc.html', title: 'Головна — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: HS, ids: HI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: 0, path: '<b>головна</b>', main: homeBody(true) })}</div>` };

// ── Каталог ──
const res = (n) => `<a href="NovelT.dc.html" class="res">${cov(n)}<span class="col" style="gap:5px;min-width:0"><b>${n.t}</b><span class="sub">${n.a} · ${n.l === 'свій твір' ? 'свій твір' : 'з ' + ({ 'англ.': 'англійської', 'япон.': 'японської', 'кит.': 'китайської', 'кор.': 'корейської' }[n.l])}</span><span class="meta"><span class="acc">★ ${n.r}</span><span>${n.ch === n.of ? n.ch + ' глав' : n.ch + ' з ' + n.of + ' глав'}</span><span>${n.st}</span>${n.ai ? '<span>ШІ</span>' : '<span>людський</span>'}</span><span class="row" style="gap:6px;flex-wrap:wrap">${n.tags.map(t => `<span class="chip">${t}</span>`).join('')}</span>${n.pct && n.pct < 100 ? `<span class="prog" style="max-width:220px"><i style="width:${n.pct}%"></i></span>` : ''}</span></a>`;
const filters = `<div class="flt">
<div class="flt-g"><span class="lab">Жанр</span>${[['Фентезі', 412, 1], ['Романтика', 154], ['Пригоди', 131], ['Містика', 77]].map(([t, n, on]) => `<span class="ck ${on ? 'on' : ''}"><i></i>${t}<span>${n}</span></span>`).join('')}<a href="#" class="acc" style="font-size:13px">ще 9</a></div>
<div class="flt-g"><span class="lab">Світ і сюжет</span>${[['Ісекай', 188], ['Академія', 64], ['Повільне життя', 96, 1]].map(([t, n, on]) => `<span class="ck ${on ? 'on' : ''}"><i></i>${t}<span>${n}</span></span>`).join('')}</div>
<div class="flt-g"><span class="lab">Переклад</span><div class="seg"><span class="on">Будь-який</span><span>ШІ</span><span>Людський</span></div><div class="seg"><span class="on">Переклади</span><span>Свої твори</span></div></div>
<div class="flt-g"><span class="lab">Стан</span>${[['Перекладається', 301], ['Завершено', 88], ['Оригінал завершено', 120]].map(([t, n]) => `<span class="ck"><i></i>${t}<span>${n}</span></span>`).join('')}</div>
<div class="flt-g"><span class="lab">Глав</span><div class="row" style="gap:6px;flex-wrap:wrap"><span class="chip">до 50</span><span class="chip on">50–200</span><span class="chip">200–500</span><span class="chip">500+</span></div></div>
<div class="flt-g"><span class="lab">Ще</span><span class="ck"><i></i>Оцінка 4+</span><span class="ck"><i></i>Оновлено цього тижня</span><span class="ck"><i></i>Показувати 18+</span></div>
</div>`;
const catTop = `<div class="cat-bar"><span class="field">${ic('search', 17)} Назва, автор, тег</span><button type="button" class="btn out" onClick="{{ go.filt }}">${ic('filter', 17)} 3</button></div>`;
const chosen = `<div class="row" style="flex-wrap:wrap;gap:6px"><span class="chip on">Фентезі ${ic('x', 13)}</span><span class="chip on">Повільне життя ${ic('x', 13)}</span><span class="chip on">50–200 глав ${ic('x', 13)}</span><a href="#" class="acc" style="font-size:13px;margin-left:4px">Скинути</a></div>`;
const colls = `<div class="coll">${['Читати без очікування', 'На вечір', 'Довге читання', 'Свіже цього тижня', 'Найкраще оцінені', 'Випадкова новела'].map((t, i) => `<span class="chip">${t}</span>`).join('')}</div>`;
const sortRow = `<div class="row" style="justify-content:space-between"><span class="dim" style="font-size:13.5px"><b class="strong">46</b> новел</span><button type="button" class="btn sm out">${ic('sort', 15)} Популярні</button></div>`;
const results = `<div class="res-l">${[N.n1, N.n2, N.n3, N.n4, N.n6].map(res).join('')}</div>`;
const catBody = (dk) => `<div class="page" style="position:relative">
<div class="col" style="gap:12px">${dk ? '<h1 class="h1">Каталог</h1>' : ''}${catTop}${iff('not.empty', chosen, true)}${colls}</div>
<div class="cat-g">${dk ? `<aside>${filters}</aside>` : ''}<div class="col" style="gap:8px;min-width:0">
${iff('ok', sortRow + results, true)}
${iff('is.empty', `<div class="err" style="padding-top:40px">${ic('search', 40, 1.5)}<b>Нічого не знайдено за «маякк»</b><p>Перевірте назву або приберіть частину фільтрів. Новели шукаються за всіма назвами: українською, оригінальною й англійською.</p><button type="button" class="btn pri">Скинути фільтри</button></div>`)}
${iff('is.load', skelRows(6))}
</div></div>
${iff('is.search', `<div class="pop sugg" role="listbox"><span class="lab">Новели</span><a href="NovelT.dc.html" class="on">${ic('book', 18)}<span>Доглядач <mark>маяк</mark>а</span></a><a href="#">${ic('book', 18)}<span>Світло <mark>маяк</mark>а на краю моря</span></a><span class="lab">Теги</span><a href="#">${ic('compass', 18)}<span>Острів · <mark>маяк</mark>и</span></a><span class="faint" style="font-size:12px;padding:6px 8px">↑ ↓ — вибрати · Enter — відкрити</span></div>`)}
</div>`;
const CS = ['каталог', 'фільтри', 'пошук', 'нічого не знайдено', 'заготовка'], CI = ['list', 'filt', 'search', 'empty', 'load'];
const CA = { ok: ['list', 'filt', 'search'] };
export const CatalogT = { file: 'CatalogT.dc.html', title: 'Каталог — телефон', w: 390, h: 844, dev: 'ph', stany: CS, ids: CI, alias: CA, extra: CSS,
  root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 1, main: catBody(false) })}${iff('is.filt', `<div class="scrim"></div><div class="sheet" role="dialog" aria-label="Фільтри"><div class="grab"></div><div class="sh-h"><b class="h2 grow">Фільтри</b><button type="button" class="ib" onClick="{{ go.list }}" aria-label="Закрити">${ic('x', 20)}</button></div><div class="sh-b">${filters}</div><div style="padding:12px 20px 20px;display:flex;gap:8px;border-top:1px solid var(--line)"><button type="button" class="btn">Скинути</button><button type="button" class="btn pri grow">Показати 46 новел</button></div></div>`)}</div>` };
export const CatalogK = { file: 'CatalogK.dc.html', title: 'Каталог — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: CS.filter(s => s !== 'фільтри'), ids: CI.filter(s => s !== 'filt'), alias: CA, extra: CSS,
  root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: 1, path: '<b>каталог</b> · фентезі · повільне життя', main: catBody(true) })}</div>` };

// ── Бібліотека ──
const li = (n, ch, pct, newc) => `<div class="li">${cov(n)}<span class="col" style="min-width:0"><b>${n.t}</b><span class="dim" style="font-size:13px">${ch}${newc ? ` · <span class="acc">нових ${newc}</span>` : ''}</span><span class="prog"><i style="width:${pct}%"></i></span><span class="row"><span class="faint" style="font-size:12px">${pct} %</span>${ic('bell', 15)}<a href="ReaderT.dc.html" class="btn sm ${newc ? 'pri' : ''}">Продовжити</a></span></span></div>`;
const lists = `<div class="lists">${[['Читаю', 5, 1], ['В планах', 3], ['Прочитано', 12], ['Відкладено', 1], ['Кинуто', 2]].map(([t, n, on]) => `<span class="chip ${on ? 'on' : ''}">${t} <span>${n}</span></span>`).join('')}</div>`;
const libItems = `<div class="lib-items">${li(N.n1, 'гл. 12 з 40', 30, 0)}${li(N.n2, 'гл. 50 з 72', 69, 22)}${li(N.n4, 'гл. 3 з 23', 13, 2)}${li(N.n6, 'гл. 8 з 54', 15, 0)}</div>`;
const offl = `<div class="col" style="gap:14px"><div class="card" style="padding:14px;display:flex;flex-direction:column;gap:10px"><div class="aa-r" style="font-size:14.5px"><span>Зберігати глави наперед<small>3 наступні глави кожної новели з «Читаю»</small></span><span class="tog on" role="switch" aria-checked="true"></span></div><p class="faint" style="font-size:12.5px;line-height:1.45">Без інтернету відкриваються лише збережені глави. Місце читання надішлеться, щойно з’явиться зв’язок.</p></div>
${[[N.n1, 'глави 12–14', '0,4 МБ'], [N.n2, 'глави 50–52', '0,6 МБ'], [N.n4, 'глави 3–5', '0,3 МБ']].map(([n, c, s]) => `<div class="nl">${cov(n)}<span style="min-width:0"><b>${n.t}</b><span class="sub">${c} · ${s}</span></span><button type="button" class="btn sm out">Видалити</button></div>`).join('')}
<span class="faint" style="font-size:12.5px">Разом 1,3 МБ на цьому пристрої</span></div>`;
const libBody = (dk) => `<div class="page">${dk ? '<h1 class="h1">Бібліотека</h1>' : ''}<div class="lib-g">${dk ? `<aside class="col" style="gap:18px">${lists}<div class="hr"></div><span class="chip ${''}" style="justify-content:flex-start;gap:8px;background:transparent">${ic('dl', 16)} Збережено офлайн</span></aside>` : `<div class="row" style="justify-content:space-between"><span></span></div>${lists}`}<div class="col" style="gap:12px;min-width:0">
${iff('is.lists', libItems, true)}
${iff('is.off', sec('Збережено на цьому пристрої', '', offl))}
${iff('is.empty', `<div class="err" style="padding-top:40px">${ic('lib', 40, 1.5)}<b>У «Читаю» поки порожньо</b><p>Додайте новелу кнопкою з закладкою на її сторінці — тут з’явиться прогрес і нові глави.</p><a href="CatalogT.dc.html" class="btn pri">Відкрити каталог</a></div>`)}
${iff('is.load', skelRows(4))}
</div></div></div>`;
const LS = ['списки', 'збережено офлайн', 'порожньо', 'заготовка'], LI = ['lists', 'off', 'empty', 'load'];
export const LibraryT = { file: 'LibraryT.dc.html', title: 'Бібліотека — телефон', w: 390, h: 844, dev: 'ph', stany: LS, ids: LI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 2, main: libBody(false) })}</div>` };
export const LibraryK = { file: 'LibraryK.dc.html', title: 'Бібліотека — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: LS, ids: LI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: 2, path: '<b>бібліотека</b> · читаю', main: libBody(true) })}</div>` };

// ── Що перекласти ──
const PR = [
  ['Море скляних дзвонів', 'The Sea of Glass Bells · royalroad.com', 'Дівчина-дзвонарка шукає місто, яке пішло під воду разом із її родиною.', 48, 1, '@oleh.reads · 3 дні тому', ['Фентезі', 'Море']],
  ['Сто днів у вежі Аліти', '塔の百日 · syosetu.com', 'Учень мага має сто днів, щоб пройти вежу, де кожен поверх — інша пора року.', 41, 0, '@nika · тиждень тому', ['Академія', 'Магія']],
  ['Коли вовки співають', 'без посилання', 'Містична повість про село в Карпатах і зиму, яка не закінчується.', 17, 0, '@marta_k · 2 тижні тому', ['Містика']],
  ['Пекар і дракон', 'The Baker and the Dragon · ao3', 'Взято: перекладає $svitanok з 3 жовтня.', 63, 1, '@tanya · місяць тому', ['Повільне життя'], true]
];
const prCard = ([t, o, d, v, on, m, tags, taken]) => `<div class="pr"><button type="button" class="vote ${on ? 'on' : ''}" aria-pressed="${on ? 'true' : 'false'}">${ic('up2', 18)}<span>${v}</span></button><span class="col" style="gap:6px;min-width:0"><b>${t}</b><span class="faint" style="font-size:12.5px">${o}</span><p>${d}</p><span class="row" style="gap:6px;flex-wrap:wrap">${tags.map(x => `<span class="chip">${x}</span>`).join('')}</span><span class="row"><span class="meta grow">${m}</span>${taken ? '<span class="state pub"><i></i>взято</span>' : '<a href="StudioNewK.dc.html" class="btn sm out">Беру перекладати</a>'}</span></span></div>`;
const prList = `<div class="row" style="justify-content:space-between"><div class="seg" style="flex:none"><span class="on">Найбільше голосів</span><span>Нові</span><span>Взяті</span></div></div><div class="pr-l">${PR.map(prCard).join('')}</div>`;
const prForm = `<div class="form">
<div class="fm"><label>Посилання на новелу</label><span class="field">https://www.royalroad.com/fiction/88127/the-sea-of-glass-bells</span><span class="ok-line">${ic('check', 16)} Знайдено на сайті: назву й опис заповнено й перекладено — перевірте</span></div>
<div class="fm"><label>Назва українською</label><span class="field">Море скляних дзвонів</span></div>
<div class="fm"><label>Опис</label><div class="ta">Дівчина-дзвонарка шукає місто, яке пішло під воду разом із її родиною. Кожен дзвін на дні моря звучить голосом когось, хто там лишився.</div></div>
<div class="fm"><label>Теги</label><div class="row" style="gap:6px;flex-wrap:wrap"><span class="chip on">Фентезі ${ic('x', 13)}</span><span class="chip on">Море ${ic('x', 13)}</span><span class="chip">+ тег</span></div></div>
<p class="faint" style="font-size:13px">Без посилання теж можна — тоді назва обов’язкова. Таке саме посилання вдруге — це голос за наявну пропозицію.</p>
<div class="row"><button type="button" class="btn pri">Запропонувати</button><button type="button" class="btn" onClick="{{ go.list }}">Скасувати</button></div></div>`;
const prBody = (dk) => `<div class="page"><div class="col" style="gap:8px"><h1 class="h1">Що перекласти</h1><p class="dim" style="font-size:14.5px;line-height:1.5;max-width:640px">Пропонуйте новели й голосуйте. Перекладач натискає «Беру перекладати» — у Студії з’являється переклад, а всім, хто голосував, приходить сповіщення.</p>${iff('is.list', `<a href="#" class="btn pri" style="align-self:flex-start">${ic('plus', 17)} Запропонувати новелу</a>`, true)}</div>
${iff('is.list', prList, true)}${iff('is.new', prForm)}</div>`;
const PS = ['список', 'запропонувати'], PI = ['list', 'new'];
export const ProposalsT = { file: 'ProposalsT.dc.html', title: 'Що перекласти — телефон', w: 390, h: 844, dev: 'ph', stany: PS, ids: PI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 0, back: 'HomeT.dc.html', title: 'Що перекласти', main: prBody(false) })}</div>` };
export const ProposalsK = { file: 'ProposalsK.dc.html', title: 'Що перекласти — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: PS, ids: PI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: 0, path: 'головна › <b>що перекласти</b>', main: prBody(true) })}</div>` };

// ── Профіль, інша людина, команда ──
const BADGES = [['Перша глава', 'g'], ['Книжковий черв’як', 'g'], ['Вичитувачка', 's'], ['Голос спільноти', 's'], ['Критик', 'b'], ['Цитатниця', 'b'], ['Нічна сова', 's'], ['Порадниця', 'b'], ['Перекладачка', 'n'], ['Меценат', 'n'], ['Естафета', 'n'], ['Голосувальниця', 'b']];
const badges = (n) => `<div class="badges">${BADGES.slice(0, n).map(([t, m]) => `<span class="bdg"><i class="${m}">${ic(m === 'n' ? 'lock' : 'star', 20)}</i>${t}</span>`).join('')}</div>`;
const profile = (me, dk) => `<div class="pf-g"><div class="col" style="gap:22px">
<div class="pf-h"><span class="av">${me ? 'М' : 'О'}</span><div class="col" style="gap:6px"><h1>${me ? 'marta_k' : 'oleh.reads'}</h1><span class="row" style="gap:8px;flex-wrap:wrap"><span class="lvl">${ic('star', 13)} Рівень ${me ? 7 : 4}</span><span class="faint" style="font-size:12.5px">з нами з березня</span></span>${me ? '' : '<span class="row" style="gap:8px;margin-top:4px"><a href="MessagesK.dc.html" class="btn sm pri">' + ic('chat', 16) + ' Написати</a><button type="button" class="ib" aria-label="Ще: заблокувати, поскаржитися">' + ic('more', 18) + '</button></span>'}</div></div>
<p style="font-size:15px;line-height:1.55">${me ? 'Читаю все про острови й маяки. Вичитую «Доглядача маяка» — пишіть, якщо бачите помилку.' : 'Переважно фентезі й ісекаї. Люблю довгі серії.'}</p>
<div class="card" style="padding:12px 14px;display:flex;gap:12px;align-items:center">${cov(N.n1, 'thumb')}<span class="col"><span class="lab">Читає зараз</span><b class="strong">Доглядач маяка · гл. 12</b></span></div>
<div class="stats"><div><b>412</b><span>глав прочитано</span></div><div><b>${me ? 23 : 4}</b><span>правок прийнято</span></div><div><b>${me ? 58 : 31}</b><span>коментарі</span></div></div>
${sec('Досягнення', me ? '12' : '', badges(dk ? 12 : 8))}
</div><div class="col" style="gap:22px">
${sec('Внесок у переклади', '', [nl(N.n1, '19 правок · 1 240 знаків', 'редакторка'), nl(N.n4, '4 правки', '')].join(''))}
${me ? `<a href="SettingsK.dc.html" class="btn out">${ic('gear', 17)} Редагувати профіль</a>` : ''}
</div></div>`;
const team = `<div class="pf-g"><div class="col" style="gap:22px">
<div class="pf-h"><span class="av">С</span><div class="col" style="gap:6px"><h1>Солона вода</h1><span class="faint" style="font-size:13.5px">$solona-voda · команда перекладу · 3 людини</span><span class="row" style="gap:8px;margin-top:4px"><a href="MessagesK.dc.html" class="btn sm">${ic('chat', 16)} Чат команди</a><a href="StudioK.dc.html" class="btn sm out">${ic('gear', 16)} Керувати</a></span></div></div>
<p style="font-size:15px;line-height:1.55">Перекладаємо тихі історії про море, ремесло й людей. ШІ робить чернетку, ми вичитуємо кожну главу.</p>
${sec('Переклади', '', [nl(N.n1, '40 з 62 · в роботі · ШІ з правками', '★ 4,6'), nl(N.n4, '23 з 80 · в роботі', '★ 4,2')].join(''))}
</div><div class="col" style="gap:22px">
${sec('Учасники', '', [['О', 'orysia', 'власниця'], ['Т', 'taras.p', 'перекладач'], ['М', 'marta_k', 'редакторка']].map(([a, n, r]) => `<a href="ProfileK.dc.html" class="mem"><span class="av">${a}</span><span>${n}</span><span class="r">${r}</span></a>`).join(''))}
<div class="card" style="padding:14px;display:flex;flex-direction:column;gap:8px"><b class="strong">Підтримати шагами</b><p class="dim" style="font-size:13.5px;line-height:1.5">Шаги йдуть на переклад нових глав. Буде доступно після запуску оплати.</p><button type="button" class="btn sm dis" aria-disabled="true">Підтримати</button></div>
</div></div>`;
const pfBody = (dk) => `<div class="page">${iff('is.me', profile(true, dk), true)}${iff('is.other', profile(false, dk))}${iff('is.team', team)}</div>`;
const FS = ['мій профіль', 'інша людина', 'команда'], FI = ['me', 'other', 'team'];
export const ProfileT = { file: 'ProfileT.dc.html', title: 'Профіль і команда — телефон', w: 390, h: 844, dev: 'ph', stany: FS, ids: FI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 4, back: 'MeT.dc.html', title: 'Профіль', main: pfBody(false) })}</div>` };
export const ProfileK = { file: 'ProfileK.dc.html', title: 'Профіль і команда — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: FS, ids: FI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: 'люди › <b>marta_k</b>', main: pfBody(true) })}</div>` };

// ── Вхід, реєстрація, відновлення ──
const pwd = (label, hint = '') => `<div class="fm"><label>${label}</label><span class="field" style="justify-content:space-between"><span>••••••••••</span>${ic('eye', 18)}</span>${hint ? `<small>${hint}</small>` : ''}</div>`;
const google = (t) => `<button type="button" class="btn out wide"><span class="g"></span> ${t}</button>`;
const authStates = `
${iff('is.login', `<h1>Вхід</h1><div class="fm"><label>Пошта або нік</label><span class="field">marta.k@ukr.net</span></div>${pwd('Пароль')}<a href="#" onClick="{{ go.reset }}" class="acc" style="font-size:14px;align-self:flex-end">Забули пароль?</a><button type="button" class="btn pri wide">Увійти</button><span class="or">або</span>${google('Увійти через Google')}<p class="lnk">Немає акаунта? <a href="#" onClick="{{ go.reg }}">Зареєструватися</a></p>`, true)}
${iff('is.reg', `<h1>Реєстрація</h1><div class="fm"><label>Нік</label><span class="field">marta_k</span><small>3–30 знаків, латиниця або кирилиця, не впереміш</small></div><div class="fm"><label>Пошта</label><span class="field">marta.k@ukr.net</span><small>Надішлемо лист для підтвердження</small></div>${pwd('Пароль', 'Щонайменше 10 знаків')}<span class="ck on" style="font-size:13.5px;align-items:flex-start"><i></i><span style="margin:0;font-size:13.5px;color:var(--dim)">Погоджуюся з <a href="InfoK.dc.html" class="acc">правилами сайту</a></span></span><button type="button" class="btn pri wide">Створити акаунт</button><span class="or">або</span>${google('Продовжити з Google')}<p class="lnk">Уже є акаунт? <a href="#" onClick="{{ go.login }}">Увійти</a></p>`)}
${iff('is.reset', `<h1>Новий пароль</h1><p class="dim" style="font-size:14.5px;line-height:1.5">Надішлемо посилання на пошту. Якщо прив’язано Telegram — ще й туди.</p><div class="fm"><label>Пошта</label><span class="field">marta.k@ukr.net</span></div><button type="button" class="btn pri wide">Надіслати посилання</button><p class="lnk"><a href="#" onClick="{{ go.login }}">Повернутися до входу</a></p>`)}
${iff('is.mail', `<span style="color:var(--acc)">${ic('chat', 40, 1.5)}</span><h1>Перевірте пошту</h1><p class="dim" style="font-size:14.5px;line-height:1.55">Ми надіслали лист на <b class="strong">m•••••k@ukr.net</b>. Відкрийте посилання з нього, щоб підтвердити пошту. Лист може бути в «Спамі».</p><button type="button" class="btn wide dis" aria-disabled="true">Надіслати ще раз · 0:48</button><p class="lnk">Інша пошта? <a href="#" onClick="{{ go.reg }}">Змінити</a></p>`)}
${iff('is.google', `<h1>Оберіть нік</h1><p class="dim" style="font-size:14.5px;line-height:1.5">Пошту <b class="strong">marta.k@gmail.com</b> уже підтвердив Google. Лишилося вибрати, як вас звати на сайті.</p><div class="fm"><label>Нік</label><span class="field">marta_k</span><span class="ok-line">${ic('check', 16)} Вільний</span></div><button type="button" class="btn pri wide">Готово</button>`)}
${iff('is.welcome', `<h1>Ласкаво просимо!</h1><p class="dim" style="font-size:14.5px;line-height:1.5">Додайте аватарку й кілька слів про себе — так вас упізнають у коментарях і правках.</p><span class="upl">${ic('plus', 28)}</span><div class="fm"><label>Про себе</label><div class="ta">Читаю все про острови й маяки.</div></div><button type="button" class="btn pri wide">Зберегти</button><a href="HomeT.dc.html" class="btn wide" style="background:transparent">Пропустити</a>`)}`;
const AS = ['вхід', 'реєстрація', 'новий пароль', 'перевірте пошту', 'нік для Google', 'ласкаво просимо'], AI = ['login', 'reg', 'reset', 'mail', 'google', 'welcome'];
export const AuthT = { file: 'AuthT.dc.html', title: 'Вхід і реєстрація — телефон', w: 390, h: 844, dev: 'ph', stany: AS, ids: AI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 4, back: 'HomeT.dc.html', title: 'Акаунт', main: `<div class="auth">${authStates}</div>` })}</div>` };
export const AuthK = { file: 'AuthK.dc.html', title: 'Вхід і реєстрація — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: AS, ids: AI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: '<b>вхід</b>', main: `<div class="page" style="align-items:center"><div class="auth">${authStates}</div></div>` })}</div>` };

// ── Помилки сторінок ──
const errs = `
${iff('is.nf', `<span class="code">404</span><b>Такої сторінки немає</b><p>Можливо, новелу перейменували — стара адреса тоді переводить на нову. Спробуйте знайти її за назвою.</p><span class="field" style="max-width:360px">${ic('search', 17)} Назва, автор, тег</span><a href="HomeT.dc.html" class="btn pri">На головну</a>`, true)}
${iff('is.adult', `<span class="code">18+</span><b>Лише для дорослих</b><p>Цей переклад позначено 18+. Підтвердіть вік у налаштуваннях читання — і його буде видно в каталозі й пошуку.</p><button type="button" class="btn pri">Мені є 18</button><a href="HomeT.dc.html" class="btn">На головну</a>`)}
${iff('is.hidden', `<span style="color:var(--dim)">${ic('eye', 44, 1.4)}</span><b>Переклад приховано</b><p>Модератори приховали його на час розгляду скарги правовласника. Якщо ви в команді — подробиці у «Сповіщеннях».</p><a href="InfoK.dc.html" class="btn">Правила сайту</a>`)}
${iff('is.denied', `<span style="color:var(--dim)">${ic('lock', 44, 1.4)}</span><b>Ця сторінка — для команди перекладу</b><p>Оригінал і робоче місце бачать лише учасники команди «Солона вода». Попросіть власницю додати вас.</p><a href="ProfileK.dc.html" class="btn">Сторінка команди</a>`)}
${iff('is.down', `<span style="color:var(--warn)">${ic('alert', 44, 1.4)}</span><b>Сайт не відповідає</b><p>Ми вже знаємо й лагодимо. Спробуйте за хвилину. Збережені глави відкриваються й без зв’язку.</p><button type="button" class="btn pri">${ic('refresh', 17)} Спробувати ще раз</button><a href="LibraryT.dc.html" class="btn">Збережені глави</a>`)}`;
const ES = ['немає сторінки', 'лише 18+', 'приховано', 'немає доступу', 'сайт не відповідає'], EI = ['nf', 'adult', 'hidden', 'denied', 'down'];
export const ErrorsT = { file: 'ErrorsT.dc.html', title: 'Помилки сторінок — телефон', w: 390, h: 844, dev: 'ph', stany: ES, ids: EI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: -1, main: `<div class="err">${errs}</div>` })}</div>` };
export const ErrorsK = { file: 'ErrorsK.dc.html', title: 'Помилки сторінок — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ES, ids: EI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, main: `<div class="err">${errs}</div>` })}</div>` };

// ── Правила й для розробників ──
const rules = `<div class="doc-g"><nav class="doc-toc"><a href="#" class="on">Коротко</a><a href="#">Переклади й права</a><a href="#">Скарги правовласників</a><a href="#">18+</a><a href="#">Коментарі, чат, повідомлення</a><a href="#">Переклад з ШІ</a><a href="#">Модерація й оскарження</a></nav>
<article class="doc"><span class="faint" style="font-size:13px">Оновлено 19 жовтня 2026</span><h1>Правила сайту</h1>
<p>Новелка — місце, де читають і перекладають новели українською. Гість лише читає; бібліотека, правки, коментарі й повідомлення — після входу.</p>
<h2>Переклади й права</h2><p>Викладаючи переклад, ви підтверджуєте:</p><div class="quote">«Я маю право перекладати цей текст і викладати переклад».</div><ul><li>Оригінал глав бачить лише команда перекладу — ніколи читачі й пошукові системи.</li><li>Закриті (платні) глави на сайті оригіналу не беремо.</li></ul>
<h2>Скарги правовласників</h2><p>Правовласник надсилає скаргу з кнопки «Поскаржитися» на сторінці новели. Переклад одразу ховається від читачів, доки модератори її розглядають; команда отримує сповіщення.</p>
<h2>18+</h2><p>Такі переклади не бачать гості й ті, хто не підтвердив вік, — ні в каталозі, ні в пошуку.</p></article></div>`;
const dev = `<div class="doc-g"><nav class="doc-toc"><a href="#" class="on">Почати</a><a href="#">Токени й права</a><a href="#">Частота запитів</a><a href="#">Сторінки за ключем</a><a href="#">ETag і кеш</a><a href="#">Помилки</a><a href="#">Вебхуки</a><a href="#">Довідник</a></nav>
<article class="doc"><h1>Для розробників</h1><p>Публічний API читання, API Студії й перекладу, вебхуки. Опис OpenAPI — <code class="mono">/api/v1/openapi.json</code>.</p>
<h2>Токен</h2><p>Створіть токен у «Налаштуваннях → API», виберіть права (<b>read</b>, <b>studio</b>, <b>translate</b>, <b>suggest</b>) і строк. Токен показується один раз.</p>
<div class="code"><span class="c"># нові глави новели, 20 за раз</span>
curl -H <span class="s">"Authorization: Bearer nvk_live_…"</span> \\
  <span class="s">"https://novelka.in.ua/api/v1/novels/dohliadach-maiaka/chapters?limit=20&amp;after=12"</span></div>
<div class="code"><span class="k">const</span> res = <span class="k">await</span> fetch(<span class="s">\`\${base}/api/v1/studio/translations/\${id}/runs\`</span>, {
  method: <span class="s">'POST'</span>, headers: { Authorization: <span class="s">\`Bearer \${token}\`</span> },
  body: JSON.stringify({ from: 41, to: 45, quality: <span class="s">'high'</span> })
});  <span class="c">// відповідь: кошторис у шагах і id запуску</span></div>
<h2>Довідник</h2>${[['GET', '/api/v1/novels', 'каталог, сторінки за ключем'], ['GET', '/api/v1/novels/{slug}/chapters/{n}', 'опублікована глава'], ['POST', '/api/v1/studio/translations/{id}/runs', 'запуск перекладу з ШІ'], ['POST', '/api/v1/suggestions', 'пакет правок']].map(([m, p, d]) => `<div class="ep"><span class="m">${m}</span><code>${p}</code><span>${d}</span></div>`).join('')}
</article></div>`;
export const InfoK = { file: 'InfoK.dc.html', title: 'Правила й для розробників — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['правила', 'для розробників'], ids: ['rules', 'dev'], extra: CSS,
  root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: '<b>правила сайту</b>', main: `<div class="page">${iff('is.rules', rules, true)}${iff('is.dev', dev)}</div>` })}</div>` };
