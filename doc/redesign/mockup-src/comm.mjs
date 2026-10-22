// Для всіх, хто увійшов: сповіщення, повідомлення й загальний чат, «Я», налаштування,
// мої правки, мої картинки, шаги. Телефон (…T) і комп'ютер (…K).
import { ic, iff, kk } from './lib.mjs';
import { NOVELS } from './content.mjs';
import { CHROME_CSS, phoneFrame, deskFrame } from './chrome.mjs';
import { READER_CSS } from './reader.mjs';
import { SITE_CSS } from './site.mjs';

export const COMM_CSS = `
.flt-row{display:flex;gap:6px;overflow-x:auto;scrollbar-width:none}
.dk .flt-row{flex-wrap:wrap}
.day{font-size:12.5px;color:var(--faint);padding:14px 0 4px}
.k-pult .day{font:500 11px/1 var(--f-num);text-transform:uppercase;letter-spacing:.08em}
.k-pal .day{font-variant:all-small-caps;letter-spacing:.1em;font-size:15px;color:var(--acc)}
.nt{display:grid;grid-template-columns:40px minmax(0,1fr) auto;gap:12px;padding:12px 0;border-bottom:1px solid var(--line);align-items:start;position:relative}
.nt .ic-b{width:40px;height:40px;border-radius:50%;display:grid;place-items:center;background:var(--s2);color:var(--dim)}
.nt .cover{width:40px;height:56px;padding:0;font-size:0}
.nt .cover b{display:none}
.nt p{font-size:14.5px;line-height:1.45}
.nt p b{color:var(--strong);font-weight:600}
.nt .t{font-size:12px;color:var(--faint);white-space:nowrap}
.nt.new::before{content:"";position:absolute;left:-10px;top:28px;width:6px;height:6px;border-radius:50%;background:var(--acc)}
.nt .q{font-size:13.5px;color:var(--dim);margin-top:4px}
.k-pult .nt .ic-b{border-radius:0;box-shadow:inset 0 0 0 1px var(--line);background:none}
.k-pult .nt .t{font-family:var(--f-num);font-size:11px}
.k-pal .nt{border-bottom:1px dotted var(--line)}
.k-pal .nt .ic-b{background:none;box-shadow:inset 0 0 0 1px var(--acc);color:var(--acc)}
.k-obl .nt{border:0;background:var(--s1);border-radius:20px;padding:12px;margin-bottom:6px}
.k-obl .nt.new::before{left:4px;top:8px}
.k-obl .nt .ic-b{background:var(--acc);color:var(--onacc)}
.dk .nt-l{max-width:820px}
.bar-act{display:flex;align-items:center;gap:8px;justify-content:space-between}
/* повідомлення */
.cv{display:grid;grid-template-columns:44px minmax(0,1fr) auto;gap:12px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line)}
.cv .av{width:44px;height:44px;font-size:16px}
.cv b{display:block;font-size:15px;color:var(--strong);font-weight:600}
.cv .p{font-size:13.5px;color:var(--dim);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;display:block}
.cv .r{display:flex;flex-direction:column;align-items:flex-end;gap:4px;font-size:12px;color:var(--faint)}
.cv .n{min-width:20px;height:20px;padding:0 6px;border-radius:10px;background:var(--acc);color:var(--onacc);font:700 11px/20px var(--f-ui);text-align:center}
.cv.on{background:var(--s1)}
.k-pult .cv .n{border-radius:0;font-family:var(--f-num)}
.k-pal .cv{border-bottom:1px dotted var(--line)}
.k-obl .cv{border:0;border-radius:20px;padding:10px;margin-bottom:4px}
.k-obl .cv.on{background:var(--acc);color:var(--onacc)}
.k-obl .cv.on b,.k-obl .cv.on .p,.k-obl .cv.on .r{color:var(--onacc)}
.msg-g{display:flex;flex-direction:column;height:100%}
.dk .msg-g{display:grid;grid-template-columns:340px minmax(0,1fr);height:100%}
.dk .msg-g.chat3{grid-template-columns:260px minmax(0,1fr) 260px}
.msg-l{padding:16px;overflow:auto;min-height:0;display:flex;flex-direction:column;gap:6px}
.dk .msg-l{border-right:1px solid var(--line)}
.k-obl.dk .msg-l{border:0}
.th{display:flex;flex-direction:column;min-height:0;height:100%}
.th-h{display:flex;align-items:center;gap:10px;padding:10px 12px;border-bottom:1px solid var(--line);flex:none}
.th-h .av{width:36px;height:36px}
.th-h b{font-size:15px;color:var(--strong)}
.th-h span{font-size:12.5px;color:var(--faint)}
.th-b{flex:1;overflow:auto;padding:16px;display:flex;flex-direction:column;gap:10px;min-height:0}
.bub{max-width:78%;padding:9px 12px;border-radius:16px 16px 16px 4px;background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);font-size:14.5px;line-height:1.45;align-self:flex-start;display:flex;flex-direction:column;gap:4px}
.bub.me{align-self:flex-end;background:var(--acc);color:var(--onacc);border-radius:16px 16px 4px 16px;box-shadow:none}
.bub .who{font-size:12.5px;font-weight:700;color:var(--acc)}
.bub .tm{font-size:11px;opacity:.7;align-self:flex-end}
.bub .rq{font-size:13px;padding:4px 8px;border-radius:8px;background:rgba(127,127,127,.18);opacity:.9}
.bub .img{width:220px;height:140px;border-radius:10px}
.bub .sp{background:var(--s2);color:transparent;border-radius:4px;text-shadow:0 0 8px var(--dim)}
.k-pult .bub{border-radius:0}
.k-pal .bub{border-radius:2px;background:transparent;box-shadow:inset 0 0 0 1px var(--line)}
.k-pal .bub.me{background:var(--s2);color:var(--tx);box-shadow:inset 0 0 0 1px var(--acc)}
.k-obl .bub{border-radius:20px 20px 20px 6px;box-shadow:none;background:var(--s2)}
.k-obl .bub.me{border-radius:20px 20px 6px 20px;background:var(--acc)}
.cmp{flex:none;border-top:1px solid var(--line);padding:10px 12px;display:flex;flex-direction:column;gap:8px;background:var(--bg)}
.cmp .tools{display:flex;gap:2px;color:var(--dim)}
.cmp .tools button{width:32px;height:30px;display:grid;place-items:center;border-radius:var(--rb);font-size:13px;font-weight:700}
.cmp .in{display:flex;gap:8px;align-items:center}
.cmp .in .field{flex:1}
.ch-l{display:flex;flex-direction:column;gap:2px}
.ch-l a{display:flex;align-items:center;gap:8px;padding:8px 10px;border-radius:var(--rb);font-size:14.5px;color:var(--dim)}
.ch-l a.on{background:var(--s2);color:var(--strong);font-weight:600}
.ch-l a .n{margin-left:auto;font-size:12px;color:var(--acc);font-weight:700}
.k-pult .ch-l a{font-family:var(--f-num);font-size:13px}
.on-l{display:flex;flex-direction:column;gap:8px;padding:16px;border-left:1px solid var(--line);overflow:auto}
.on-l .row .av{width:28px;height:28px;font-size:12px;position:relative}
.on-l .role{font-size:11.5px;color:var(--acc2)}
.dot-on{width:8px;height:8px;border-radius:50%;background:var(--acc2);flex:none}
.role-t{font-size:11px;font-weight:700;color:var(--acc2);text-transform:uppercase;letter-spacing:.04em}
.ment{color:var(--acc);font-weight:600}
.me .ment{color:inherit;text-decoration:underline}
.pics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:6px}
.pic{aspect-ratio:1;border-radius:calc(var(--rcard)*.6);position:relative;overflow:hidden}
.pic.c1{background:#1D3B44}.pic.c2{background:#5A2A1E}.pic.c3{background:#1E2A5A}.pic.c4{background:#3B3F22}.pic.c5{background:#2B1830}.pic.c6{background:#18363A}
.pic::before{content:"";position:absolute;left:-10%;right:30%;bottom:-20%;height:70%;border-radius:50%;background:rgba(255,255,255,.18)}
.pic::after{content:"";position:absolute;right:16%;top:16%;width:18%;height:18%;border-radius:50%;background:rgba(255,230,170,.55)}
/* «Я» */
.me-h{display:flex;align-items:center;gap:14px;padding:6px 0 4px}
.me-h .av{width:56px;height:56px;font-size:22px}
.mi{display:flex;align-items:center;gap:14px;padding:14px 4px;border-bottom:1px solid var(--line);font-size:15.5px}
.mi .i:first-child{color:var(--dim)}
.mi .r{margin-left:auto;font-size:13px;color:var(--faint);display:flex;align-items:center;gap:6px}
.k-pal .mi{border-bottom:1px dotted var(--line)}
.k-obl .mi{border:0;border-radius:18px;padding:13px 12px;background:var(--s1);margin-bottom:6px}
.k-pult .mi{padding:12px 4px;font-size:14.5px}
.bal{display:flex;align-items:baseline;gap:8px}
.bal b{font:var(--wh) 34px/1 var(--f-num);color:var(--strong)}
.k-obl .bal b{color:var(--acc)}
/* налаштування */
.set-g{display:flex;flex-direction:column;gap:18px}
.dk .set-g{display:grid;grid-template-columns:240px minmax(0,1fr);gap:44px;align-items:start}
.set-nav{display:flex;flex-direction:column;gap:2px}
.set-nav a{display:flex;align-items:center;gap:10px;padding:9px 10px;border-radius:var(--rb);font-size:14.5px;color:var(--dim)}
.set-nav a.on{background:var(--s2);color:var(--strong);font-weight:600}
.k-pal .set-nav a{font-variant:all-small-caps;letter-spacing:.06em;font-size:17px}
.k-pal .set-nav a.on{background:none;color:var(--acc)}
.k-obl .set-nav a.on{background:var(--acc);color:var(--onacc)}
.set{display:flex;flex-direction:column;gap:22px;max-width:760px}
.set-r{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:12px 0;border-bottom:1px solid var(--line);font-size:14.5px}
.set-r small{display:block;font-size:12.5px;color:var(--faint);margin-top:2px}
.k-pal .set-r{border-bottom:1px dotted var(--line)}
.kgrid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
.dk .kgrid{grid-template-columns:repeat(4,minmax(0,1fr))}
.kc{display:flex;flex-direction:column;gap:8px;padding:10px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.kc.on{box-shadow:inset 0 0 0 2px var(--acc)}
.kc b{font-size:14.5px;color:var(--strong)}
.kc span{font-size:12px;color:var(--faint);line-height:1.35}
.kc .mini{height:86px;border-radius:calc(var(--rcard)*.5);background:var(--bg);box-shadow:inset 0 0 0 1px var(--line);position:relative;overflow:hidden}
.mini i{position:absolute;display:block;background:var(--s2)}
.mini .ln{height:4px;border-radius:2px;background:var(--faint);opacity:.5}
.thg{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}
.dk .thg{grid-template-columns:repeat(4,minmax(0,1fr))}
.tc{display:flex;align-items:center;gap:10px;padding:10px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);font-size:14px}
.tc.on{box-shadow:inset 0 0 0 2px var(--acc)}
.tc .sws{display:flex;border-radius:50%;overflow:hidden;width:30px;height:30px;flex:none;box-shadow:inset 0 0 0 1px rgba(128,128,128,.4)}
.tc .sws i{flex:1}
.ntab{display:grid;grid-template-columns:minmax(0,1fr) 64px 64px 64px;gap:0;font-size:14px}
.ntab>*{padding:11px 0;border-bottom:1px solid var(--line);display:flex;align-items:center}
.ntab .h{font-size:12px;color:var(--faint);justify-content:center}
.ntab .c{justify-content:center}
.ck2{width:20px;height:20px;border-radius:5px;box-shadow:inset 0 0 0 1.5px var(--dim);display:grid;place-items:center}
.ck2.on{background:var(--acc);box-shadow:none;color:var(--onacc)}
.k-pult .ck2{border-radius:0}
.tok{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:4px 12px;padding:12px 0;border-bottom:1px solid var(--line)}
.tok b{color:var(--strong);font-size:14.5px;word-break:break-all}
.tok .m{font-size:12.5px;color:var(--faint)}
.tok .chip{height:22px;font-size:11.5px;padding:0 7px}
.once{padding:14px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--acc);display:flex;flex-direction:column;gap:8px}
.once code{font:13px 'IBM Plex Mono',monospace;color:var(--strong);word-break:break-all}
.dl{display:grid;grid-template-columns:60px minmax(0,1fr) auto;gap:10px;padding:8px 0;border-bottom:1px solid var(--line);font-size:13px;align-items:center}
.dl .c{font:600 12px 'IBM Plex Mono',monospace}
.dl .c.ok{color:var(--acc2)} .dl .c.bad{color:var(--del)}
/* мої правки */
.sg{display:flex;flex-direction:column;gap:8px;padding:14px 0;border-bottom:1px solid var(--line)}
.sg .src{font-size:12.5px;color:var(--faint)}
.sg .txt{font:15.5px/1.6 var(--f-rd);color:var(--read)}
.sg del{color:var(--del);background:var(--delbg);text-decoration-thickness:1.5px;border-radius:3px}
.sg ins{color:var(--ins);background:var(--insbg);text-decoration:none;border-radius:3px}
.k-pal .sg{border-bottom:1px dotted var(--line)}
.k-obl .sg{border:0;background:var(--s1);border-radius:22px;padding:14px;margin-bottom:8px}
.dk .sg-l{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:32px}
.k-obl.dk .sg-l{column-gap:10px}
/* мої картинки */
.drop{display:flex;flex-direction:column;align-items:center;gap:8px;padding:22px;border-radius:var(--rcard);box-shadow:inset 0 0 0 1.5px var(--line);border:0;text-align:center;font-size:14px;color:var(--dim);background-image:none}
.k-obl .drop{border-radius:26px}
.gal{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
.dk .gal{grid-template-columns:repeat(5,minmax(0,1fr))}
.gi{display:flex;flex-direction:column;gap:6px;font-size:12.5px;color:var(--dim)}
.gi b{font:500 12.5px 'IBM Plex Mono',monospace;color:var(--strong)}
.gi .pic{aspect-ratio:4/3}
.pl{display:grid;grid-template-columns:120px minmax(0,1fr) auto;gap:12px;padding:10px 0;border-bottom:1px solid var(--line);font-size:14px;align-items:center}
.pl code{font:13px 'IBM Plex Mono',monospace;color:var(--strong)}
.pl .warn{color:var(--warn)}
/* шаги */
.hist{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:4px 12px;padding:12px 0;border-bottom:1px solid var(--line);font-size:14.5px}
.hist .m{font-size:12.5px;color:var(--faint)}
.hist .plus{color:var(--acc2);font-weight:700;font-variant-numeric:tabular-nums}
.hist .minus{color:var(--tx);font-weight:700;font-variant-numeric:tabular-nums}
.k-pult .hist .plus,.k-pult .hist .minus{font-family:var(--f-num);font-weight:500}
`;
const CSS = READER_CSS + CHROME_CSS + SITE_CSS + COMM_CSS;
const N = Object.fromEntries(NOVELS.map(n => [n.id, n]));
const cov = (n) => `<span class="cover ${n.c}"><b>${n.t}</b></span>`;
const icb = (i) => `<span class="ic-b">${ic(i, 19)}</span>`;
const skel = (n = 5) => `<div class="col" style="gap:16px" aria-busy="true">${Array.from({ length: n }, () => `<div class="row" style="gap:12px"><span class="sk" style="width:40px;height:40px;border-radius:50%;flex:none"></span><span class="col grow" style="gap:8px"><span class="sk" style="height:14px;width:80%"></span><span class="sk" style="height:12px;width:40%"></span></span></div>`).join('')}</div>`;
const head = (title, dk, right = '') => `<div class="bar-act">${dk ? `<h1 class="h1">${title}</h1>` : '<span></span>'}${right}</div>`;

// ── Сповіщення ──
const NT = {
  ch1: `<div class="nt new">${cov(N.n2)}<span><p><b>Маг води</b> — нові глави 71–72</p><p class="q">«Острів трьох течій», «Відплив» · ШІ з правками</p></span><span class="t">2 год</span></div>`,
  ch2: `<div class="nt new">${cov(N.n1)}<span><p><b>Доглядач маяка</b> — глава 41 «Друга лампа»</p></span><span class="t">вчора</span></div>`,
  rep: `<div class="nt new">${icb('reply')}<span><p><b>oleh.reads</b> відповів на ваш коментар у «Доглядачі маяка», гл. 12</p><p class="q">«Так, дзвін той самий — у главі 3 він теж бив один раз».</p></span><span class="t">3 год</span></div>`,
  sug: `<div class="nt">${icb('check')}<span><p>Команда «Солона вода» прийняла <b>2 ваші правки</b> в главі 12</p></span><span class="t">вчора</span></div>`,
  st: `<div class="nt">${icb('layers')}<span><p>Переклад глав 41–45 <b>завершено</b> · «Доглядач маяка» · витрачено 14 шагів</p></span><span class="t">вчора</span></div>`,
  ach: `<div class="nt">${icb('star')}<span><p>Нове досягнення — <b>«Вичитувачка»</b>, срібна медаль</p></span><span class="t">2 дні</span></div>`,
  sh: `<div class="nt">${icb('plus')}<span><p>Власник сайту нарахував <b>50 шагів</b> · «за вичитку тому 1»</p></span><span class="t">3 дні</span></div>`,
  ment: `<div class="nt">${icb('chat')}<span><p><b>orysia</b> згадала <span class="ment">$solona-voda</span> у загальному чаті</p></span><span class="t">3 дні</span></div>`
};
const ntFilters = (on) => `<div class="flt-row">${['Усі', 'Нові глави', 'Правки', 'Відповіді', 'Студія', 'Шаги й сайт'].map((t, i) => `<span class="chip ${i === on ? 'on' : ''}">${t}</span>`).join('')}</div>`;
const ntBody = (dk) => `<div class="page"><div class="col nt-l" style="gap:12px">
${head('Сповіщення', dk, `<span class="row">${iff('not.arch', '<button type="button" class="btn sm out">' + ic('check', 16) + ' Усе прочитано</button>', true)}<button type="button" class="btn sm ${''}" onClick="{{ go.arch }}">${ic('dl', 16)} Архів</button></span>`)}
${iff('is.all', ntFilters(0) + `<div class="day">Сьогодні</div>${NT.ch1}${NT.rep}<div class="day">Учора</div>${NT.ch2}${NT.sug}${NT.st}<div class="day">Раніше</div>${NT.ach}${NT.sh}${NT.ment}<div class="pager"><span>сторінка 1 з 3</span><span class="row" style="gap:4px"><a href="#" class="on">1</a><a href="#">2</a><a href="#">3</a></span></div>`, true)}
${iff('is.chap', ntFilters(1) + `<div class="day">Сьогодні</div>${NT.ch1}<div class="day">Учора</div>${NT.ch2}<div class="nt">${cov(N.n4)}<span><p><b>Останній картограф</b> — глави 22–23</p></span><span class="t">2 дні</span></div><div class="nt">${cov(N.n1)}<span><p><b>Доглядач маяка</b> — в оригіналі вийшли глави 61–62</p><p class="q">для команди: можна перекладати далі</p></span><span class="t">4 дні</span></div>`)}
${iff('is.arch', `<div class="row" style="justify-content:space-between"><span class="row" style="gap:10px;font-size:14px"><span class="ck2 on">${ic('check', 14)}</span>Вибрано 2</span><span class="row"><button type="button" class="btn sm out">${ic('undo', 16)} Повернути</button><button type="button" class="btn sm out">${ic('trash', 16)} Видалити назавжди</button></span></div>${NT.sug.replace('class="nt"', 'class="nt" style="opacity:.85"')}${NT.st}${NT.ach}<p class="faint" style="font-size:12.5px">В архіві сповіщення лежать, доки ви їх не видалите. Видалене назавжди не повернути.</p>`)}
${iff('is.empty', `<div class="err" style="padding-top:60px">${ic('bell', 40, 1.5)}<b>Нових сповіщень немає</b><p>Тут з’являться нові глави ваших новел, відповіді, прийняті правки й новини Студії. Що саме надсилати й куди — у налаштуваннях.</p><a href="SettingsK.dc.html" class="btn">Налаштувати сповіщення</a></div>`)}
${iff('is.load', skel(6))}
</div></div>`;
const NS = ['усі', 'нові глави', 'архів', 'порожньо', 'заготовка'], NI = ['all', 'chap', 'arch', 'empty', 'load'];
export const NotificationsT = { file: 'NotificationsT.dc.html', title: 'Сповіщення — телефон', w: 390, h: 844, dev: 'ph', stany: NS, ids: NI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: -1, back: 'HomeT.dc.html', title: 'Сповіщення', main: ntBody(false) })}</div>` };
export const NotificationsK = { file: 'NotificationsK.dc.html', title: 'Сповіщення — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: NS, ids: NI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: '<b>сповіщення</b>', main: ntBody(true) })}</div>` };

// ── Повідомлення й загальний чат ──
const CV = [['О', 'orysia', 'Глянеш главу 13? Там Орест говорить на «ви»', '12:04', 2, 'on'], ['$', 'Чат «Солона вода»', 'taras.p: запуск 41–45 пішов', '11:30', 0], ['К', 'Книжковий клуб · група', 'nika: голосуємо за наступну', 'вчора', 5], ['О', 'oleh.reads', 'Дякую за пораду!', 'пн', 0]];
const cvList = (on) => `${CV.map(([a, n, p, t, k, c], i) => `<a href="#" class="cv ${on && i === 0 ? 'on' : ''}"><span class="av">${a}</span><span style="min-width:0"><b class="ell">${n}</b><span class="p">${p}</span></span><span class="r">${t}${k ? `<span class="n">${k}</span>` : ''}</span></a>`).join('')}`;
const listPane = (on) => `<div class="msg-l"><div class="row"><span class="field grow">${ic('search', 16)} Пошук розмов</span><button type="button" class="ib" aria-label="Нова група" onClick="{{ go.newg }}">${ic('plus', 20)}</button></div><div class="seg"><span class="on">Розмови</span><a href="#" onClick="{{ go.chat }}">Загальний чат</a></div>${cvList(on)}</div>`;
const fmtTools = `<div class="tools"><button type="button" aria-label="Жирний">Ж</button><button type="button" aria-label="Курсив" style="font-style:italic">К</button><button type="button" aria-label="Підкреслений" style="text-decoration:underline">П</button><button type="button" aria-label="Закреслений" style="text-decoration:line-through">З</button><button type="button" aria-label="Спойлер">${ic('eye', 16)}</button><button type="button" aria-label="Цитата">${ic('quote', 16)}</button><button type="button" aria-label="Картинки, до 10">${ic('img', 16)}</button></div>`;
const composer = (ph) => `<div class="cmp">${fmtTools}<div class="in"><span class="field">${ph}</span><button type="button" class="btn pri" aria-label="Надіслати">${ic('next', 18)}</button></div></div>`;
const thread = (back) => `<div class="th"><div class="th-h">${back ? `<button type="button" class="ib" onClick="{{ go.list }}" aria-label="До розмов">${ic('back', 20)}</button>` : ''}<span class="av">О</span><span class="col grow"><b>orysia</b><span>власниця «Солоної води» · була 5 хв тому</span></span><button type="button" class="ib" onClick="{{ go.about }}" aria-label="Про розмову">${ic('more', 20)}</button></div>
<div class="th-b"><div class="day" style="align-self:center">Сьогодні</div>
<div class="bub">Привіт! Глянеш главу 13? Там Орест говорить з Ліною на «ви», а в 12 був на «ти».<span class="tm">11:58</span></div>
<div class="bub me"><span class="rq">Там Орест говорить з Ліною на «ви»</span>Так, бачу. Виправлю пакетом — там ще абзаци 4 і 9.<span class="tm">12:01 · прочитано</span></div>
<div class="bub"><span class="img pic c1"></span>Ось як це виглядало в оригіналі, сторінка 3.<span class="tm">12:03</span></div>
<div class="bub"><span>І ще: <span class="sp">у главі 20 вона дізнається, хто він</span> — тож «ви» там доречне.</span><span class="tm">12:04</span></div>
</div>${composer('Повідомлення…')}</div>`;
const about = (back) => `<div class="th"><div class="th-h">${back ? `<button type="button" class="ib" onClick="{{ go.conv }}" aria-label="Назад">${ic('back', 20)}</button>` : ''}<b class="grow">Про розмову</b></div><div class="th-b" style="gap:16px">
<div class="col" style="align-items:center;gap:8px"><span class="av" style="width:72px;height:72px;font-size:28px">К</span><b class="h2">Книжковий клуб</b><span class="faint" style="font-size:13px">група · 14 учасників · ви адміністраторка</span></div>
<div class="set-r" style="padding-top:0"><span>Вимкнути сповіщення групи</span><span class="tog" role="switch" aria-checked="false"></span></div>
<div class="col" style="gap:8px"><span class="lab">Учасники</span>${[['М', 'marta_k', 'адміністраторка'], ['Н', 'nika', ''], ['О', 'oleh.reads', ''], ['Т', 'tanya', '']].map(([a, n, r]) => `<div class="mem"><span class="av">${a}</span><span>${n}</span><span class="r">${r}</span></div>`).join('')}<a href="#" class="acc" style="font-size:13.5px">+ Додати людей · ще 10</a></div>
<div class="col" style="gap:8px"><span class="lab">Картинки з розмови</span><div class="pics">${['c1', 'c2', 'c3', 'c4', 'c5', 'c6', 'c2', 'c4'].map(c => `<span class="pic ${c}"></span>`).join('')}</div></div>
<button type="button" class="btn out">${ic('back', 17)} Вийти з групи</button></div></div>`;
const chat = (dk) => `${dk ? `<div class="msg-l"><span class="lab">Канали</span><div class="ch-l">${[['загальний', 3, 1], ['переклад-і-словники', 12], ['що-почитати', 0], ['сайт-і-пропозиції', 1]].map(([n, k, on]) => `<a href="#" class="${on ? 'on' : ''}"># ${n}${k ? `<span class="n">${k}</span>` : ''}</a>`).join('')}</div><p class="faint" style="font-size:12.5px;line-height:1.45">Правила: без спойлерів поза ||спойлером||, без реклами. Модератори — з позначкою.</p></div>` : ''}
<div class="th"><div class="th-h">${dk ? '' : `<button type="button" class="ib" onClick="{{ go.list }}" aria-label="До розмов">${ic('back', 20)}</button>`}<span class="col grow"><b># загальний</b><span>${dk ? 'гості читають · писати — після входу' : '<a href="#" class="acc">4 канали</a> · зараз на сайті 37'}</span></span></div>
<div class="th-b"><div class="bub"><span class="who">tanya</span>Хтось читав «Кухаря з нижнього міста»? Варто?<span class="tm">12:10</span></div>
<div class="bub"><span class="who">orysia <span class="role-t">модераторка</span></span>Варто, особливо якщо любите повільне життя. Переклад завершено.<span class="tm">12:12</span></div>
<div class="bub me"><span><span class="ment">@tanya</span> почни з побічної історії про суп, вона окремо читається</span><span class="tm">12:13</span></div>
<div class="bub"><span class="who">taras.p</span><span><span class="ment">$solona-voda</span> взяли «Пекаря і дракона» з «Що перекласти»</span><span class="tm">12:20</span></div></div>
${composer('Написати в #загальний… @нік, $команда')}</div>
${dk ? `<aside class="on-l"><span class="lab">Зараз на сайті · 37</span>${[['М', 'marta_k', ''], ['О', 'orysia', 'модераторка'], ['Т', 'tanya', ''], ['Н', 'nika', ''], ['Т', 'taras.p', 'перекладач']].map(([a, n, r]) => `<div class="row"><span class="dot-on"></span><span class="av">${a}</span><span class="col"><span style="font-size:14px">${n}</span>${r ? `<span class="role">${r}</span>` : ''}</span></div>`).join('')}<span class="faint" style="font-size:12px">і ще 32 · хто приховав себе, тут не видно</span></aside>` : ''}`;
const newg = `<div class="th"><div class="th-h"><button type="button" class="ib" onClick="{{ go.list }}" aria-label="Назад">${ic('back', 20)}</button><b class="grow">Нова група</b></div><div class="th-b" style="gap:16px"><span class="upl">${ic('img', 26)}</span><div class="fm"><label>Назва</label><span class="field">Книжковий клуб</span></div><div class="fm"><label>Учасники</label><span class="field">${ic('search', 16)} Нік</span><div class="row" style="gap:6px;flex-wrap:wrap"><span class="chip on">nika ${ic('x', 13)}</span><span class="chip on">tanya ${ic('x', 13)}</span></div><small>До 100 людей. Хто заборонив додавати себе в групи, отримає запрошення.</small></div><button type="button" class="btn pri">Створити групу</button></div></div>`;
const MS = ['розмови', 'розмова', 'про розмову', 'загальний чат', 'нова група'], MI = ['list', 'conv', 'about', 'chat', 'newg'];
export const MessagesT = { file: 'MessagesT.dc.html', title: 'Повідомлення й чат — телефон', w: 390, h: 844, dev: 'ph', stany: MS, ids: MI, extra: CSS,
  root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 3, title: '<b>повідомлення</b>', main: `<div class="msg-g">${iff('is.list', listPane(false), true)}${iff('is.conv', thread(true))}${iff('is.about', about(true))}${iff('is.chat', chat(false))}${iff('is.newg', newg)}</div>` })}</div>` };
export const MessagesK = { file: 'MessagesK.dc.html', title: 'Повідомлення й чат — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: MS.filter(s => s !== 'розмови'), ids: MI.filter(s => s !== 'list'), extra: CSS,
  root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: '<b>повідомлення</b>', main: `${iff('is.conv', `<div class="msg-g">${listPane(true)}${thread(false)}</div>`, true)}${iff('is.about', `<div class="msg-g">${listPane(true)}${about(false)}</div>`)}${iff('is.chat', `<div class="msg-g chat3">${chat(true)}</div>`)}${iff('is.newg', `<div class="msg-g">${listPane(false)}${newg}</div>`)}` })}</div>` };

// ── «Я» на телефоні ──
const mi = (i, t, r = '', href = '#') => `<a href="${href}" class="mi">${ic(i, 20)}<span>${t}</span><span class="r">${r}${ic('next', 16)}</span></a>`;
const meBody = `<div class="page" style="gap:16px"><div class="me-h"><span class="av">М</span><span class="col"><b class="h2">marta_k</b><a href="ProfileT.dc.html" class="acc" style="font-size:13.5px">Мій профіль</a></span></div>
<div class="card" style="padding:14px;display:flex;align-items:center;gap:12px"><span class="col grow"><span class="lab">Шаги</span><span class="bal"><b>86</b><span class="dim">у резерві 12</span></span></span><a href="ShahsT.dc.html" class="btn sm">Історія</a></div>
<div>${mi('layers', 'Студія', '2 переклади', 'StudioT.dc.html')}${mi('pen', 'Мої правки', '2 чернетки', 'MySuggestionsT.dc.html')}${mi('img', 'Мої картинки', '', 'PicturesT.dc.html')}${mi('users', 'Мої команди', '1')}${mi('gear', 'Налаштування', '', 'SettingsT.dc.html')}${iff('is.owner', mi('lock', 'Адміністрування', '3 скарги', 'AdminK.dc.html'))}${mi('book', 'Правила сайту', '', 'InfoK.dc.html')}</div>
<button type="button" class="btn out">Вийти</button></div>`;
export const MeT = { file: 'MeT.dc.html', title: '«Я» — телефон', w: 390, h: 844, dev: 'ph', stany: ['читач', 'власник сайту'], ids: ['user', 'owner'], extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 4, main: meBody })}</div>` };

// ── Налаштування ──
const SETN = [['prof', 'user', 'Профіль'], ['look', 'eye', 'Вигляд сайту'], ['read', 'book', 'Читання'], ['notif', 'bell', 'Сповіщення'], ['sec', 'lock', 'Вхід і безпека'], ['shahs', 'plus', 'Шаги'], ['api', 'cols', 'API і вебхуки']];
const setNav = (on) => `<nav class="set-nav">${SETN.map(([id, i, t]) => `<a href="#" class="${id === on ? 'on' : ''}" onClick="{{ go.${id} }}">${ic(i, 18)}${t}</a>`).join('')}</nav>`;
const sr = (t, right, small = '') => `<div class="set-r"><span>${t}${small ? `<small>${small}</small>` : ''}</span>${right}</div>`;
const tog = (on) => `<span class="tog ${on ? 'on' : ''}" role="switch" aria-checked="${on ? 'true' : 'false'}"></span>`;
const sProf = `<div class="set"><div class="row" style="gap:16px"><span class="av" style="width:72px;height:72px;font-size:28px">М</span><span class="col" style="gap:6px"><button type="button" class="btn sm">Змінити аватарку</button><span class="faint" style="font-size:12.5px">обрізається до кола</span></span></div>
<div class="fm"><label>Нік</label><span class="field">marta_k</span><small>Раніше: marta · історію бачите лише ви й модератори</small></div>
<div class="fm"><label>Про себе</label><div class="ta">Читаю все про острови й маяки. Вичитую «Доглядача маяка».</div></div>
<div class="col"><span class="lab">Приватність</span>${sr('Хто може писати мені', '<div class="seg" style="min-width:150px"><span class="on">Усі</span><span>Ніхто</span></div>')}${sr('Хто може додавати в групи', '<div class="seg" style="min-width:150px"><span class="on">Усі</span><span>Ніхто</span></div>')}${sr('Показувати «Читає зараз»', tog(true))}${sr('Показувати «Зараз на сайті»', tog(true))}${sr('Заблоковані', '<span class="acc" style="font-size:13.5px">2 людини ›</span>')}</div>
<button type="button" class="btn pri" style="align-self:flex-start">Зберегти</button></div>`;
const KS = [['Пульт', 'щільно, панелі впритул, моно для чисел · за замовчуванням', 'pult'], ['Звичний', 'як сайт зараз: картки, шапка, Golos і Literata', 'zv'], ['Палітурка', 'як книжка: засічки, поля й лінійки', 'pal'], ['Обкладинка', 'яскраво: великі номери, пухкі кнопки', 'obl']];
const mini = (k) => ({
  pult: '<i style="left:0;top:0;bottom:0;width:14px;background:var(--s0)"></i><i style="left:14px;right:0;top:0;height:10px;background:var(--s0)"></i><i class="ln" style="left:22px;top:20px;width:60%"></i><i class="ln" style="left:22px;top:30px;width:50%"></i><i class="ln" style="left:22px;top:40px;width:55%"></i><i style="left:22px;top:54px;width:30px;height:10px;background:var(--acc)"></i>',
  zv: '<i style="left:0;right:0;top:0;height:14px;background:var(--s1)"></i><i style="left:8px;top:22px;width:40%;height:40px;border-radius:5px;background:var(--s1)"></i><i style="right:8px;top:22px;width:40%;height:40px;border-radius:5px;background:var(--s1)"></i><i style="left:8px;top:68px;width:40px;height:10px;border-radius:4px;background:var(--acc)"></i>',
  pal: '<i class="ln" style="left:35%;top:8px;width:30%;background:var(--acc)"></i><i style="left:8px;right:8px;top:18px;height:1px;background:var(--line)"></i><i class="ln" style="left:20%;top:30px;width:60%"></i><i class="ln" style="left:20%;top:40px;width:60%"></i><i class="ln" style="left:20%;top:50px;width:55%"></i><i class="ln" style="left:20%;top:60px;width:60%"></i>',
  obl: '<i style="left:6px;top:6px;bottom:6px;width:22px;border-radius:8px;background:var(--s1)"></i><i style="left:34px;top:8px;width:26px;height:20px;border-radius:4px;background:var(--acc);opacity:.85"></i><i style="left:34px;top:36px;right:8px;height:16px;border-radius:8px;background:var(--s1)"></i><i style="left:34px;top:58px;width:44px;height:14px;border-radius:7px;background:var(--acc)"></i>'
})[k];
const THS = [['Сланець', ['#1C1F25', '#5CCFBF', '#F2B35B'], 1], ['Графіт · зараз', ['#0F1115', '#F2C46B', '#8EA6FF']], ['Сукно', ['#0E1613', '#9CC7A4', '#D2A84E']], ['Слива', ['#191120', '#F4E27C', '#FF7EA4']], ['Пергамент', ['#2A251D', '#E6D9BF', '#C9A86A']], ['Океан', ['#0E1B26', '#8FD3E8', '#3FA7D6']], ['Неон', ['#0B0B12', '#39FF88', '#FF3FD2']], ['Свої кольори', ['#333', '#777', '#bbb']]];
const sLook = (dk) => `<div class="set">
<div class="col" style="gap:10px"><span class="lab">Каркас</span><p class="dim" style="font-size:13.5px">Будова сайту: меню, шрифти, форма кнопок і карток, щільність. Вибраний — у кожного свій; гостю його пам’ятає браузер.</p><div class="kgrid">${KS.map(([t, d, k], i) => `<button type="button" class="kc ${i === 0 ? 'on' : ''}"><span class="mini">${mini(k)}</span><b>${t}</b><span>${d}</span></button>`).join('')}</div></div>
<div class="col" style="gap:10px"><span class="lab">Тема</span><p class="dim" style="font-size:13.5px">Лише кольори; будь-яка тема лягає на будь-який каркас. Кольори нинішніх стилів сайту — теж тут.</p><div class="thg">${THS.map(([t, c, on]) => `<button type="button" class="tc ${on ? 'on' : ''}"><span class="sws">${c.map(x => `<i style="background:${x}"></i>`).join('')}</span>${t}</button>`).join('')}</div><a href="#" class="acc" style="font-size:13.5px">Усі теми · 16 ›</a></div>
<div class="col">${sr('Світла чи темна', '<div class="seg" style="min-width:250px"><span class="on">Темна</span><span>Світла</span><span>Як у системі</span></div>', dk ? 'на комп’ютері — ще й кнопкою в шапці' : 'на телефоні сайт темний, якщо не вибрати інше')}${sr('Розмір сторінок', '<div class="seg" style="min-width:200px"><span>90%</span><span class="on">100%</span><span>110%</span><span>120%</span></div>')}${sr('Каталог', '<div class="seg" style="min-width:220px"><span class="on">Рядками</span><span>Сіткою</span><span>Полицею</span></div>')}${sr('Портрети персонажів', '<div class="seg" style="min-width:150px"><span class="on">Кола</span><span>Квадрати</span></div>')}${sr('Анімації', '<div class="seg" style="min-width:250px"><span class="on">Як у системі</span><span>Легкі</span><span>Вимкнені</span></div>')}</div></div>`;
const sRead = `<div class="set"><div class="col" style="gap:10px"><span class="lab">Стиль читалки</span><div class="thg">${[['Як на сайті', 'var(--bg)', 'var(--read)', 1], ['Папір', '#F4F2EC', '#1C1B18'], ['Сепія', '#2A251D', '#E6D9BF'], ['Сірий', '#1B1E24', '#C9CDD5'], ['Ніч', '#0F1115', '#B7BCC6'], ['Чорний', '#000', '#BFC3CC'], ['Книжка', '#F6EFDF', '#2A2318'], ['Для дислексії', '#FAF6E9', '#1C1C1C']].map(([t, b, f, on]) => `<button type="button" class="tc ${on ? 'on' : ''}"><span class="sws" style="background:${b};color:${f};display:grid;place-items:center;font:600 13px var(--f-rd)">Аа</span>${t}</button>`).join('')}</div></div>
<div class="col">${sr('Шрифт', '<div class="seg" style="min-width:250px"><span class="on">Каркаса</span><span>Literata</span><span>Без засічок</span></div>')}${sr('Розмір · міжрядок', '<span class="row"><b class="strong">18</b><span class="faint">·</span><b class="strong">1,7</b></span>')}${sr('Вирівнювання', '<div class="seg" style="min-width:200px"><span class="on">По ширині з переносами</span><span>Ліворуч</span></div>')}${sr('Ширина колонки', '<div class="seg" style="min-width:220px"><span>Вузька</span><span class="on">Звичайна</span><span>Уся ширина</span></div>')}${sr('Читати', '<div class="seg" style="min-width:200px"><span class="on">Прокруткою</span><span>Сторінками</span></div>', 'сторінки: зони торкання й анімація гортання')}${sr('Безшовно', tog(true), 'наступна глава починається одразу')}${sr('Ховати панелі під час читання', tog(true))}${sr('Відсоток і годинник', tog(false))}${sr('Не гасити екран', tog(false))}${sr('Мені є 18', tog(true), 'показувати переклади 18+')}</div></div>`;
const sNotif = `<div class="set"><div class="ntab"><span class="h" style="justify-content:flex-start">Що</span><span class="h">На сайті</span><span class="h">Telegram</span><span class="h">Пошта</span>${[['Нові глави з підписок', 1, 1, 0], ['Відповіді й згадки', 1, 1, 0], ['Мої правки прийнято', 1, 0, 0], ['Повідомлення', 1, 1, 0], ['Студія: запуски, правки на перевірку', 1, 1, 1], ['Естафета', 1, 1, 1], ['Досягнення', 1, 0, 0], ['Шаги', 1, 0, 1]].map(([t, a, b, c]) => `<span>${t}</span>${[a, b, c].map(v => `<span class="c"><span class="ck2 ${v ? 'on' : ''}">${v ? ic('check', 14) : ''}</span></span>`).join('')}`).join('')}</div>
<div class="card" style="padding:14px;display:flex;flex-direction:column;gap:8px"><b class="strong">Telegram прив’язано · @marta_k</b><p class="dim" style="font-size:13.5px;line-height:1.45">Зміну ніка, пошти й пароля бот надсилає завжди. /stop у боті — відв’язати.</p><button type="button" class="btn sm out" style="align-self:flex-start">Відв’язати</button></div></div>`;
const sSec = `<div class="set">${sr('Пошта', '<button type="button" class="btn sm">Змінити</button>', 'marta.k@ukr.net · нова пошта — після підтвердження листом')}${sr('Пароль', '<button type="button" class="btn sm">Змінити</button>', 'змінено 2 місяці тому')}${sr('Google', '<button type="button" class="btn sm out">Відв’язати</button>', 'прив’язано · marta.k@gmail.com')}${sr('Нік', '<button type="button" class="btn sm">Змінити</button>', 'про зміну прийде лист і повідомлення в Telegram')}</div>`;
const sShahs = `<div class="set"><div class="card" style="padding:16px;display:flex;align-items:center;gap:16px"><span class="col grow"><span class="lab">На рахунку</span><span class="bal"><b>86</b><span class="dim">шагів · у резерві 12</span></span></span><button type="button" class="btn dis" aria-disabled="true">Поповнити · після запуску</button></div>${sr('Показувати ціну в шагах перед запуском', tog(true))}${sr('Попереджати, коли лишається менше', '<span class="field" style="width:90px;height:34px">20</span>')}<a href="ShahsK.dc.html" class="acc" style="font-size:14px">Уся історія ›</a></div>`;
const sApi = `<div class="set"><div class="once"><b class="strong">Токен «Бот для Discord» створено</b><code>nvk_live_7Hq2…xR9 (показується один раз)</code><span class="row"><button type="button" class="btn sm pri">Скопіювати</button><span class="faint" style="font-size:12.5px">Збережіть його зараз — потім побачите лише назву.</span></span></div>
<div class="col"><div class="bar-act"><span class="lab">Токени</span><button type="button" class="btn sm">${ic('plus', 15)} Новий токен</button></div>${[['Бот для Discord', ['read'], 'до 22 січня · щойно'], ['Скрипт вичитки', ['studio', 'suggest'], 'без строку · учора'], ['Перекладач-помічник', ['translate'], 'до 1 листопада · тиждень тому']].map(([t, r, m]) => `<div class="tok"><span class="col" style="gap:4px"><b>${t}</b><span class="row" style="gap:4px">${r.map(x => `<span class="chip">${x}</span>`).join('')}</span><span class="m">${m}</span></span><button type="button" class="btn sm out">Відкликати</button></div>`).join('')}</div>
<div class="col"><div class="bar-act"><span class="lab">Вебхуки</span><button type="button" class="btn sm">${ic('plus', 15)} Новий вебхук</button></div><div class="tok"><span class="col" style="gap:4px"><b>https://hooks.example.org/novelka</b><span class="row" style="gap:4px"><span class="chip">нові глави</span><span class="chip">запуски</span><span class="chip">правки</span></span><span class="m">Доглядач маяка · підпис HMAC · секрет показано раз</span></span><button type="button" class="btn sm">Пробна подія</button></div>
${[['200', 'ok', 'нова глава 41 · Доглядач маяка', '12:04'], ['503', 'bad', 'запуск 41–45 завершено · повтор через 4 хв', '11:58'], ['200', 'ok', 'правки прийнято · глава 12', 'учора']].map(([c, k, t, w]) => `<div class="dl"><span class="c ${k}">${c}</span><span>${t}</span><span class="row faint">${w}${k === 'bad' ? ' <button type="button" class="btn sm out">Повторити</button>' : ''}</span></div>`).join('')}</div></div>`;
const setBody = (dk) => `<div class="page"><div class="set-g">${dk ? '<div class="col" style="gap:14px"><h1 class="h1">Налаштування</h1>' : ''}${dk ? SETN.map(([id]) => iff(`is.${id}`, setNav(id), id === 'prof')).join('') + '</div>' : ''}<div class="col" style="gap:14px;min-width:0">
${iff('is.prof', sProf, true)}${iff('is.look', sLook(dk))}${iff('is.read', sRead)}${iff('is.notif', sNotif)}${iff('is.sec', sSec)}${iff('is.shahs', sShahs)}${iff('is.api', sApi)}</div></div></div>`;
const SS = ['профіль', 'вигляд сайту', 'читання', 'сповіщення', 'вхід і безпека', 'шаги', 'API і вебхуки'], SI = SETN.map(x => x[0]);
const phTitle = SETN.map(([id, , t]) => iff(`is.${id}`, `<b>налаштування</b> › ${t.toLowerCase()}`, id === 'prof')).join('');
export const SettingsT = { file: 'SettingsT.dc.html', title: 'Налаштування — телефон', w: 390, h: 844, dev: 'ph', stany: SS, ids: SI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 4, back: 'MeT.dc.html', title: SETN.map(([id, , t]) => iff(`is.${id}`, t, id === 'prof')).join(''), main: setBody(false) })}</div>` };
export const SettingsK = { file: 'SettingsK.dc.html', title: 'Налаштування — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: SS, ids: SI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: phTitle, main: setBody(true) })}</div>` };

// ── Мої правки ──
const SG = {
  draft: [['Доглядач маяка · гл. 12, абз. 2', '— Ти запізнилася, — не обертаючись, <del>сказав</del> <ins>мовив</ins> майстер Орест.', 'не надіслано', 'Надіслати'], ['Доглядач маяка · гл. 12, абз. 9', 'Світло лінзи пройшло над <del>водою</del> <ins>морем</ins> і зникло в тумані.', 'не надіслано', 'Надіслати']],
  rev: [['Маг води · гл. 50, абз. 14', 'Рьо <del>створив</del> <ins>сплів</ins> водяний щит одним рухом.', 'на перевірці · 2 дні', 'Відкликати']],
  acc: [['Доглядач маяка · гл. 11, абз. 3', 'Пором <del>прийшов</del> <ins>причалив</ins> о сьомій, як завжди із запізненням.', 'прийнято · orysia · учора', ''], ['Останній картограф · гл. 2, абз. 7', 'Мапа <del>була</del> <ins>виявилася</ins> старшою за саме місто.', 'прийнято · тиждень тому', '']],
  stale: [['Маг води · гл. 49, абз. 2', 'Абзац змінився після вашої правки: <del>Вода слухалась</del> <ins>Вода корилася</ins> його голосу.', 'застаріла — абзац уже інший', 'Відкрити главу']]
};
const sg = ([s, t, st, b]) => `<div class="sg"><span class="src">${s}</span><p class="txt">${t}</p><span class="row"><span class="faint grow" style="font-size:12.5px">${st}</span>${b ? `<button type="button" class="btn sm ${b === 'Надіслати' ? 'pri' : 'out'}">${b}</button>` : ''}</span></div>`;
const sgBody = (dk) => `<div class="page"><div class="col" style="gap:12px">${dk ? '<h1 class="h1">Мої правки</h1>' : ''}
${['draft', 'rev', 'acc', 'stale'].map((id, i) => iff(`is.${id}`, `<div class="flt-row">${['Чернетки · 2', 'На перевірці · 1', 'Прийняті · 23', 'Застарілі · 1'].map((t, j) => `<span class="chip ${i === j ? 'on' : ''}">${t}</span>`).join('')}</div>${id === 'draft' ? `<div class="card" style="padding:12px 14px;display:flex;align-items:center;gap:12px"><span class="grow dim" style="font-size:13.5px">2 правки в «Доглядачі маяка» чекають, доки ви надішлете їх пакетом</span><button type="button" class="btn sm pri">Надіслати всі</button></div>` : ''}<div class="sg-l">${SG[id].map(sg).join('')}</div>`, i === 0)).join('')}
</div></div>`;
const GS = ['чернетки', 'на перевірці', 'прийняті', 'застарілі'], GI = ['draft', 'rev', 'acc', 'stale'];
export const MySuggestionsT = { file: 'MySuggestionsT.dc.html', title: 'Мої правки — телефон', w: 390, h: 844, dev: 'ph', stany: GS, ids: GI, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 4, back: 'MeT.dc.html', title: 'Мої правки', main: sgBody(false) })}</div>` };
export const MySuggestionsK = { file: 'MySuggestionsK.dc.html', title: 'Мої правки — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: GS, ids: GI, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: 'я › <b>мої правки</b>', main: sgBody(true) })}</div>` };

// ── Мої картинки ──
const GAL = [['27-5.png', 'гл. 27 · після абз. 5', 'c1'], ['маяк-ніч.jpg', 'намальовано · гл. 12', 'c3'], ['28-1.png', 'ще не вставлена', 'c2'], ['лінза.webp', 'гл. 12 · після абз. 2', 'c6'], ['30-12.png', 'ще не вставлена', 'c4'], ['пором.jpg', 'намальовано · гл. 11', 'c5'], ['31-4.png', 'ще не вставлена', 'c1'], ['туман.png', 'гл. 15', 'c3'], ['32-7.png', 'ще не вставлена', 'c6'], ['обкладинка-2.jpg', 'обкладинка тому 2', 'c2']];
const picBody = (dk) => `<div class="page"><div class="col" style="gap:14px">${dk ? '<h1 class="h1">Мої картинки</h1>' : ''}
${iff('is.all', `<div class="drop">${ic('img', 28, 1.5)}<b class="strong">Перетягніть файли сюди або <span class="acc">виберіть</span></b><span class="faint" style="font-size:12.5px">JPEG, PNG, WebP до 5 МБ · кілька одразу · до 300 на годину</span></div>
<div class="bar-act"><div class="flt-row"><span class="chip on">Усі · 48</span><span class="chip">Намальовані · 9</span><span class="chip">Завантажені · 39</span><span class="chip">Ще не вставлені · 14</span></div>${dk ? '' : ''}</div>
<div class="bar-act"><span class="dim" style="font-size:13.5px">Назви на кшталт <b class="mono strong">27-5.png</b> — глава 27, після 5-го абзацу</span><button type="button" class="btn sm pri" onClick="{{ go.place }}">Розставити в новелі</button></div>
<div class="gal">${GAL.slice(0, dk ? 10 : 6).map(([n, w, c]) => `<div class="gi"><span class="pic ${c}"></span><b>${n}</b><span>${w}</span></div>`).join('')}</div>`, true)}
${iff('is.place', `<div class="card" style="padding:14px;display:flex;flex-direction:column;gap:6px"><b class="strong">Розставити в «Доглядачі маяка»</b><p class="dim" style="font-size:13.5px">Перед публікацією — перегляд. Кожна глава, куди стане картинка, публікується новою версією.</p></div>
${[['27-5.png', 'глава 27, після абзацу 5', ''], ['28-1.png', 'глава 28, після абзацу 1', ''], ['30-12.png', 'глава 30, після абзацу 12', ''], ['31-4.png', 'глава 31 ще не перекладена — пропущено', 'warn'], ['32-7.png', 'у главі 32 лише 5 абзаців — пропущено', 'warn']].map(([f, t, w]) => `<div class="pl"><code>${f}</code><span class="${w}">${t}</span><span class="pic c3" style="width:48px;aspect-ratio:4/3"></span></div>`).join('')}
<div class="row"><button type="button" class="btn pri">Розставити й опублікувати 3 глави</button><button type="button" class="btn" onClick="{{ go.all }}">Скасувати</button></div>`)}
${iff('is.empty', `<div class="err" style="padding-top:40px">${ic('img', 40, 1.5)}<b>Тут будуть ваші картинки</b><p>Завантажені й намальовані ШІ ілюстрації лишаються тут, доки ви їх не приберете. Картинка, прибрана звідси, у главах лишається.</p><button type="button" class="btn pri">Завантажити</button></div>`)}
</div></div>`;
const IS = ['усі', 'розставити в новелі', 'порожньо'], II = ['all', 'place', 'empty'];
export const PicturesT = { file: 'PicturesT.dc.html', title: 'Мої картинки — телефон', w: 390, h: 844, dev: 'ph', stany: IS, ids: II, extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 4, back: 'MeT.dc.html', title: 'Мої картинки', main: picBody(false) })}</div>` };
export const PicturesK = { file: 'PicturesK.dc.html', title: 'Мої картинки — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: IS, ids: II, extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: 'я › <b>мої картинки</b>', main: picBody(true) })}</div>` };

// ── Шаги ──
const HIST = [['Запуск: Доглядач маяка, гл. 41–45', 'фактично 14 · резерв 18, повернуто 4', '−14', 0], ['Ілюстрація «Лінза вночі»', 'гл. 12 · Найкраща якість', '−2', 0], ['Нараховано власником сайту', '«за вичитку тому 1»', '+50', 1], ['Скасовано запуск: Останній картограф, гл. 24', 'повернуто резерв', '+6', 1], ['Запуск: Останній картограф, гл. 20–23', 'фактично 9', '−9', 0]];
const shBody = (dk) => `<div class="page"><div class="col" style="gap:16px;max-width:820px">${dk ? '<h1 class="h1">Шаги</h1>' : ''}
<div class="stats"><div><b>86</b><span>на рахунку</span></div><div><b>12</b><span>у резерві запусків</span></div><div><b>−25</b><span>за жовтень</span></div></div>
<p class="dim" style="font-size:13.5px;line-height:1.5">Шаги витрачаються на переклад з ШІ й ілюстрації. Перед запуском резервується кошторис із запасом; після — списується фактичне, решта повертається.</p>
${iff('is.hist', `<div class="day">Жовтень</div>${HIST.map(([t, m, s, p]) => `<div class="hist"><span class="col"><span>${t}</span><span class="m">${m}</span></span><span class="${p ? 'plus' : 'minus'}">${s}</span></div>`).join('')}`, true)}
${iff('is.empty', `<div class="err" style="padding-top:20px">${ic('plus', 36, 1.5)}<b>Шагів поки немає</b><p>Перекладати можна й без шагів — вставити оригінал і перекладати самому. Шаги потрібні лише для перекладу з ШІ; їх нараховує власник сайту.</p><a href="InfoK.dc.html" class="btn">Як отримати шаги</a></div>`)}
</div></div>`;
export const ShahsT = { file: 'ShahsT.dc.html', title: 'Шаги — телефон', w: 390, h: 844, dev: 'ph', stany: ['історія', 'без шагів'], ids: ['hist', 'empty'], extra: CSS, root: `<div class="{{ cls }}" style="width:390px;height:844px">${phoneFrame({ active: 4, back: 'MeT.dc.html', title: 'Шаги', main: shBody(false) })}</div>` };
export const ShahsK = { file: 'ShahsK.dc.html', title: 'Шаги — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['історія', 'без шагів'], ids: ['hist', 'empty'], extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: 'я › <b>шаги</b>', main: shBody(true) })}</div>` };
