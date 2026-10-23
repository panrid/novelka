// Листи зі сповіщеннями й службові (рішення 37). Каркаси й теми на листи не діють: один вигляд у
// кольорах «Сланцю». Кожен стан — той самий лист двічі: світла пошта на комп'ютері (лист 600 px)
// і темна пошта на телефоні (390 px), а збоку — як лист виглядає у вхідних і нотатки для реалізації.
import { ic, iff } from './lib.mjs';

const SANS = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";
const SERIF = "Georgia,'Times New Roman',serif";
const MONO = "ui-monospace,Menlo,Consolas,monospace";

const MAIL_CSS = `
.mf{position:relative;overflow:hidden;box-sizing:border-box;background:#15171B;color:#C9CDD5;font:14px/1.45 ${SANS};-webkit-font-smoothing:antialiased}
.mf *,.mf *::before,.mf *::after{box-sizing:border-box}
.mf :where(p,h1,h2,h3,ul,ol,dl,dd){margin:0;padding:0}
.mf :where(ul,ol){list-style:none}
.mf :where(a){color:inherit}
.mf :where(button){font:inherit;color:inherit;background:none;border:0;padding:0;cursor:pointer;text-align:inherit}
.mf-g{display:grid;grid-template-columns:680px 390px minmax(0,1fr);gap:32px;padding:24px 32px;height:100%}
.mf-c{display:flex;flex-direction:column;gap:10px;min-width:0;min-height:0}
.mf-cap{display:flex;align-items:center;gap:8px;height:18px;font:600 11.5px/1 ${SANS};letter-spacing:.08em;text-transform:uppercase;color:#8A909C}
/* світла пошта на комп'ютері — нейтральний клієнт */
.cl{flex:1;min-height:0;display:flex;flex-direction:column;background:#FFFFFF;border-radius:10px;overflow:hidden;color:#1F2329}
.cl-bar{height:44px;flex:none;display:flex;align-items:center;gap:14px;padding:0 16px;border-bottom:1px solid #E6E8EC;background:#F7F8FA;color:#5F6670}
.cl-subj{padding:18px 24px 4px;font:600 20px/1.3 ${SANS};color:#111418}
.cl-from{display:flex;align-items:center;gap:10px;padding:8px 24px 14px;font-size:13px;color:#5F6670;flex:none}
.cl-from b{color:#1F2329;font-weight:600}
.cl-body{flex:1;min-height:0;overflow:hidden}
/* темна пошта на телефоні */
.phn{width:390px;height:844px;flex:none;display:flex;flex-direction:column;background:#0D0E10;border-radius:34px;box-shadow:0 0 0 1px #2B2E35;overflow:hidden;color:#E4E6EA}
.phn-st{height:44px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 26px;font:600 14px/1 ${SANS}}
.phn-nav{height:40px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 10px;color:#9AA3B2}
.phn-nav span{display:flex;align-items:center;gap:2px;font-size:15px}
.phn-subj{padding:4px 16px 2px;font:600 18px/1.3 ${SANS};color:#F2F3F5}
.phn-from{display:flex;align-items:center;gap:10px;padding:8px 16px 12px;font-size:12.5px;color:#8D95A4;flex:none}
.phn-from b{color:#E4E6EA;font-weight:600}
.phn-body{flex:1;min-height:0;overflow:hidden;position:relative}
.mav{width:32px;height:32px;flex:none;border-radius:8px;background:#F2B35B;color:#1D1404;display:grid;place-items:center;font:700 16px/1 ${SERIF}}
.grow{flex:1;min-width:0}
/* сам лист: кольори «Сланцю» — світлий і темний */
.ml-lt{--m-bg:#F3F4F6;--m-card:#FFFFFF;--m-s2:#EEF1F5;--m-line:#D5D9E0;--m-tx:#1B1F27;--m-strong:#0B0E14;--m-read:#1E222A;--m-dim:#596070;--m-acc:#A86200;--m-onacc:#FFFFFF;--m-acc2:#0B786B}
.ml-dk{--m-bg:#1C1F25;--m-card:#22262D;--m-s2:#2A303A;--m-line:#333842;--m-tx:#D6DAE1;--m-strong:#EEF0F4;--m-read:#D9DCE2;--m-dim:#8D95A4;--m-acc:#F2B35B;--m-onacc:#1D1404;--m-acc2:#5CCFBF}
.ml{background:var(--m-bg);color:var(--m-tx);padding:28px 40px 32px;font:15px/1.55 ${SANS};min-height:100%}
.ml-dk{padding:18px 12px 40px}
.ml-w{max-width:600px;margin:0 auto}
.ml-hd{display:flex;align-items:center;gap:10px;padding:0 4px 16px;font:700 18px/1 ${SANS};color:var(--m-strong);letter-spacing:-.01em}
.ml-mk{width:28px;height:28px;border-radius:7px;background:var(--m-acc);color:var(--m-onacc);display:grid;place-items:center;font:700 16px/1 ${SERIF}}
.ml-card{background:var(--m-card);border:1px solid var(--m-line);border-radius:10px;padding:28px 32px;display:flex;flex-direction:column;gap:16px}
.ml-dk .ml-card{padding:22px 18px}
.ml-lab{font:700 11.5px/1.3 ${SANS};letter-spacing:.09em;text-transform:uppercase;color:var(--m-acc)}
.ml-h{font:700 22px/1.25 ${SANS};color:var(--m-strong)}
.ml-dk .ml-h{font-size:20px}
.ml-h3{font:600 16px/1.3 ${SANS};color:var(--m-strong)}
.ml b{color:var(--m-strong);font-weight:600}
.ml-btn{display:inline-block;align-self:flex-start;height:46px;line-height:46px;padding:0 24px;border-radius:8px;background:var(--m-acc);color:var(--m-onacc);font-weight:600;font-size:15px;text-decoration:none;white-space:nowrap}
.ml-dk .ml-btn{align-self:stretch;text-align:center}
.ml-a{color:var(--m-acc);text-decoration:underline;text-underline-offset:2px}
.ml-sm{font-size:13.5px;line-height:1.5;color:var(--m-dim)}
.ml-hr{height:1px;background:var(--m-line)}
.ml-nov{display:flex;gap:14px;align-items:flex-start}
.ml-cov{position:relative;overflow:hidden;width:56px;height:80px;flex:none;border-radius:4px;display:flex;align-items:flex-end;padding:5px;font:700 9px/1.1 ${SERIF}}
.ml-cov.sm{width:40px;height:57px;font-size:7px}
.ml-cov::before{content:"";position:absolute;left:-20%;right:-20%;top:18%;height:46%;border-radius:50%;background:var(--cc);opacity:.4}
.ml-cov b{position:relative;color:inherit;font-weight:700}
.ml-meta{font-size:13px;line-height:1.45;color:var(--m-dim)}
.ml-ex{font:16px/1.65 ${SERIF};color:var(--m-read)}
.ml-q{border-left:3px solid var(--m-acc);background:var(--m-s2);border-radius:0 6px 6px 0;padding:10px 14px;font:15px/1.6 ${SERIF};color:var(--m-read)}
.ml-grp{display:flex;gap:12px;padding-top:14px;border-top:1px solid var(--m-line)}
.ml-grp:first-of-type{border-top:0;padding-top:0}
.ml-chs{display:flex;flex-direction:column;gap:3px;font-size:14px;margin:4px 0 6px}
.ml-chs span{color:var(--m-dim);font-variant-numeric:tabular-nums}
.ml-tag{display:inline-block;font-size:11.5px;line-height:18px;padding:0 7px;border-radius:9px;background:var(--m-s2);color:var(--m-acc2);font-weight:600;margin-left:6px;vertical-align:1px}
.ml-link{font:12px/1.45 ${MONO};color:var(--m-dim);word-break:break-all;background:var(--m-s2);border-radius:6px;padding:10px 12px}
.ml-code{font:600 30px/1 ${MONO};letter-spacing:.3em;text-indent:.3em;text-align:center;background:var(--m-s2);border-radius:8px;padding:16px 0;color:var(--m-strong)}
.ml-kv{display:grid;grid-template-columns:128px minmax(0,1fr);gap:6px 12px;font-size:14px}
.ml-kv span{color:var(--m-dim)}
.ml-kv i{font-style:normal;color:var(--m-tx)}
.ml-lt{--m-del:#B23A17;--m-delbg:#FBE4DC;--m-ins:#7A4600;--m-insbg:#FCEBCF}
.ml-dk{--m-del:#F39479;--m-delbg:rgba(243,148,121,.13);--m-ins:#F5C27A;--m-insbg:rgba(242,179,91,.16)}
.ml-del{color:var(--m-del);background:var(--m-delbg);text-decoration:line-through;border-radius:3px;padding:0 2px}
.ml-ins{color:var(--m-ins);background:var(--m-insbg);border-radius:3px;padding:0 2px}
.ml-who{font-size:13px;color:var(--m-dim);margin-bottom:4px}
.ml-mine{border-left:3px solid var(--m-line);padding:2px 0 2px 12px;font-size:14px;color:var(--m-dim)}
.ml-at{color:var(--m-acc2);font-weight:600}
.ml-msgs{display:flex;flex-direction:column;gap:10px}
.ml-msg{background:var(--m-s2);border-radius:10px;padding:10px 14px;font-size:14.5px}
.ml-msg .t{display:block;font-size:12px;color:var(--m-dim);margin-top:2px}
.ml-medal{width:72px;height:72px;flex:none;border-radius:50%;display:grid;place-items:center;background:#C9CED6;color:#2A2F38;box-shadow:inset 0 0 0 5px #A9B0BB;font:700 26px/1 ${SERIF}}
.ml-bar{display:block;height:6px;border-radius:3px;background:var(--m-s2);overflow:hidden}
.ml-bar i{display:block;height:100%;background:var(--m-acc);border-radius:3px}
.ml-big{font:700 34px/1 ${SANS};color:var(--m-strong);font-variant-numeric:tabular-nums}
.ml-big small{font-size:15px;font-weight:500;color:var(--m-dim);margin-left:6px}
.ml-warn{border-radius:8px;padding:12px 14px;background:var(--m-delbg);font-size:14px}
.ml-st{display:flex;flex-direction:column;gap:6px}
.ml-ft{padding:18px 6px 0;display:flex;flex-direction:column;gap:8px;font-size:12.5px;line-height:1.55;color:var(--m-dim)}
.ml-ft a{color:var(--m-dim);text-decoration:underline;text-underline-offset:2px}
.ml-ft .lnk{display:flex;flex-wrap:wrap;gap:4px 14px}
/* збоку: у вхідних, нотатки, усі листи */
.sd{display:flex;flex-direction:column;gap:20px;min-width:0}
.inb{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:2px 8px;padding:10px 12px;border-radius:8px;font-size:13px;line-height:1.35}
.inb.lt{background:#FFFFFF;color:#1F2329}
.inb.dk{background:#1E2025;color:#D6DAE1;box-shadow:inset 0 0 0 1px #2B2E35}
.inb .t{font-size:12px;color:#8A909C}
.inb .s{grid-column:1/-1;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.inb .p{grid-column:1/-1;color:#8A909C;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}
.nts{display:flex;flex-direction:column;gap:12px}
.nts dt{font:600 11px/1.3 ${SANS};letter-spacing:.08em;text-transform:uppercase;color:#8A909C}
.nts dd{margin-top:3px;font-size:13px;line-height:1.5;color:#C9CDD5}
.lst{display:flex;flex-direction:column;gap:2px}
.lst button{display:block;width:100%;padding:4px 10px;border-radius:6px;font-size:13px;color:#A9AFBA}
.lst button.on{background:#2A2D33;color:#F2B35B;font-weight:600}
.lst button:hover{background:#22252A}
`;

// Обкладинка в листі — картинка; на макеті намальована, як на сайті
const COV = { c1: ['#1D3B44', '#F4EBD8', '#E9B65C'], c2: ['#5A2A1E', '#FBE7D3', '#F08A4B'], c3: ['#1E2A5A', '#E5ECFF', '#5FB6E8'], c4: ['#3B3F22', '#F1EED7', '#C9C26A'] };
const cov = (c, t, sm = false) => `<span class="ml-cov${sm ? ' sm' : ''}" style="background:${COV[c][0]};color:${COV[c][1]};--cc:${COV[c][2]}"><b>${t}</b></span>`;

const SITE = 'Новелка — переклади новел українською · <a href="#">novelka.in.ua</a>';

// Лист: шапка, картка, низ. L.body — вміст картки; L.foot — рядки низу листа
const letter = (L, mode) => `<div class="ml ml-${mode}"><div class="ml-w">
<div class="ml-hd"><span class="ml-mk">Н</span>Новелка</div>
<div class="ml-card">${L.body(mode)}</div>
<div class="ml-ft">${L.foot}<p>${SITE}</p></div>
</div></div>`;

const desk = (L) => `<div class="cl">
<div class="cl-bar">${ic('back', 18)}${ic('dl', 18)}${ic('trash', 18)}<span class="grow"></span>${ic('reply', 18)}${ic('more', 18)}</div>
<div class="cl-subj">${L.subj}</div>
<div class="cl-from"><span class="mav">Н</span><span class="grow"><b>Новелка</b> &lt;sp@novelka.in.ua&gt;<br>кому: marta.k@ukr.net</span><span>${L.when}</span></div>
<div class="cl-body">${letter(L, 'lt')}</div></div>`;

const phone = (L) => `<div class="phn">
<div class="phn-st"><span>9:41</span><span>●●● 5G</span></div>
<div class="phn-nav"><span>${ic('back', 20)} Вхідні</span><span style="gap:18px">${ic('trash', 20)}${ic('reply', 20)}</span></div>
<div class="phn-subj">${L.subj}</div>
<div class="phn-from"><span class="mav">Н</span><span class="grow"><b>Новелка</b><br>кому: мені</span><span>${L.when}</span></div>
<div class="phn-body">${letter(L, 'dk')}</div></div>`;

const side = (L, all) => `<div class="sd">
<div class="mf-c"><span class="mf-cap">У вхідних</span>
<div class="inb lt"><b>Новелка</b><span class="t">${L.when}</span><span class="s">${L.subj}</span><span class="p">${L.pre}</span></div>
<div class="inb dk"><b>Новелка</b><span class="t">${L.when}</span><span class="s">${L.subj}</span><span class="p">${L.pre}</span></div></div>
<dl class="nts">${L.notes.map(([a, b]) => `<div><dt>${a}</dt><dd>${b}</dd></div>`).join('')}</dl>
<div class="mf-c"><span class="mf-cap">Листи</span><div class="lst">${all.map(x => `<button type="button" class="${x.id === L.id ? 'on' : ''}" onClick="{{ go.${x.id} }}">${x.name}</button>`).join('')}</div></div>
</div>`;

const state = (L, all) => `<div class="mf-g">
<div class="mf-c"><span class="mf-cap">${ic('sun', 14)} Пошта на комп’ютері · світла · лист 600 px</span>${desk(L)}</div>
<div class="mf-c"><span class="mf-cap">${ic('moon', 14)} Пошта на телефоні · темна</span>${phone(L)}</div>
${side(L, all)}</div>`;

const screen = (file, title, all) => ({
  file, title, w: 1440, h: 1100, dev: 'dk', fixed: true, extra: MAIL_CSS,
  stany: all.map(x => x.name), ids: all.map(x => x.id),
  root: `<div class="mf" style="width:1440px;height:1100px">${all.map((L, i) => iff('is.' + L.id, state(L, all), i === 0)).join('\n')}</div>`
});

// Низ листа зі сповіщенням: чому прийшов, налаштувати, відписатися від цього виду листів
const footN = (why, unsub) => `<p>${why}</p><p class="lnk"><a href="#">Налаштувати сповіщення</a><a href="#">${unsub}</a></p>`;
// Низ службового листа: відписки немає
const footS = (extra = '') => `<p>Це службовий лист: такі надсилаємо завжди, навіть коли листи зі сповіщеннями вимкнено.${extra}</p>`;

// ── Листи зі сповіщеннями ──
const NOTIF = [
  {
    id: 'ch1', name: 'Нова глава', when: '18:04',
    subj: '«Доглядач маяка» — глава 41 «Друга лампа»',
    pre: 'Ліна шукала в коморі мотузку, а натрапила на щось холодне й важке, загорнуте в мішковину.',
    body: () => `<span class="ml-lab">Нова глава</span>
<div class="ml-nov">${cov('c1', 'Доглядач маяка')}<div><p class="ml-h">Глава 41 · Друга лампа</p><p class="ml-meta" style="margin-top:6px">Доглядач маяка · переклад «Солона вода» · ~12 хв читання</p></div></div>
<p class="ml-ex">Ліна знайшла другу лампу випадково: шукала в коморі під сходами мотузку, а натрапила на щось холодне й важке, загорнуте в мішковину. Скло в лампи було таке саме, як у великій лінзи, — зеленкувате, з тонкими кільцями.</p>
<a href="#" class="ml-btn">Читати главу 41</a>
<p class="ml-sm">Ви зупинилися на главі 38. <a href="#" class="ml-a">Продовжити з глави 38</a></p>`,
    foot: footN('Ви отримали цей лист, бо підписані на новелу «Доглядач маяка».', 'Не надсилати листів про нові глави цієї новели'),
    notes: [
      ['Коли', 'Щойно главу опубліковано. Якщо за годину вийдуть ще глави з підписок — усі прийдуть одним листом (наступний стан).'],
      ['Кнопка', 'Читалка, глава 41. «Продовжити» — лише коли читач відстав більше ніж на главу; веде туди, де зупинився.'],
      ['Відписка', 'Одним натиском, без входу: лише нові глави цієї новели. Заголовок List-Unsubscribe — те саме в меню пошти.'],
      ['Зміст', 'Перші 2–3 речення глави. Обкладинка — картинкою; якщо картинки вимкнено, лишається підпис.']
    ]
  },
  {
    id: 'chN', name: 'Нові глави — кілька', when: '19:12',
    subj: '5 нових глав у трьох новелах',
    pre: 'Маг води 71–72 · Доглядач маяка 41 · Останній картограф 22–23',
    body: () => `<span class="ml-lab">Нові глави</span>
<p class="ml-h">5 нових глав у ваших новелах</p>
<div class="ml-grp">${cov('c3', 'Маг води', true)}<div class="grow"><p class="ml-h3">Маг води <span class="ml-tag">ШІ з правками</span></p><p class="ml-chs"><span>71 · <a href="#" class="ml-a">Острів трьох течій</a></span><span>72 · <a href="#" class="ml-a">Відплив</a></span></p><p class="ml-meta">переклад «Тиха гавань»</p></div></div>
<div class="ml-grp">${cov('c1', 'Доглядач маяка', true)}<div class="grow"><p class="ml-h3">Доглядач маяка</p><p class="ml-chs"><span>41 · <a href="#" class="ml-a">Друга лампа</a></span></p><p class="ml-meta">переклад «Солона вода»</p></div></div>
<div class="ml-grp">${cov('c4', 'Останній картограф', true)}<div class="grow"><p class="ml-h3">Останній картограф</p><p class="ml-chs"><span>22 · <a href="#" class="ml-a">Мапа без півночі</a></span><span>23 · <a href="#" class="ml-a">Перевал</a></span></p><p class="ml-meta">переклад «Компас»</p></div></div>
<a href="#" class="ml-btn">Відкрити бібліотеку</a>`,
    foot: footN('Ви отримали цей лист, бо підписані на ці новели. Глави, що виходять протягом години, збираємо в один лист.', 'Не надсилати листів про нові глави'),
    notes: [
      ['Коли', 'Через годину після першої нової глави з підписок — усе, що вийшло за цю годину. Одна глава — попередній стан.'],
      ['Порядок', 'Як у бібліотеці: що читали останнім — вище. Понад 5 новел — «і ще N новел» посиланням на бібліотеку.'],
      ['Посилання', 'Назва глави — читалка на цій главі. Кнопка — бібліотека, вкладка «Читаю».'],
      ['Відписка', 'Від листів про нові глави взагалі (на сайті й у Telegram сповіщення лишаються).']
    ]
  },
  {
    id: 'rep', name: 'Відповідь на коментар', when: '15:47',
    subj: 'oleh.reads відповів на ваш коментар у «Доглядачі маяка»',
    pre: '«Так, дзвін той самий — у главі 3 він теж бив один раз».',
    body: () => `<span class="ml-lab">Відповідь</span>
<p class="ml-h">oleh.reads відповів вам</p>
<p class="ml-meta">Доглядач маяка · глава 12 · обговорення абзацу</p>
<p class="ml-mine">Ви: Дзвін у гавані — це той самий, що на початку книжки?</p>
<div><p class="ml-who"><b>oleh.reads</b> · 15:46</p><p class="ml-q">Так, дзвін той самий — у главі 3 він теж бив один раз. Схоже, він б’є, коли хтось приїжджає на острів.</p></div>
<a href="#" class="ml-btn">Відповісти</a>`,
    foot: footN('Ви отримали цей лист, бо на ваш коментар відповіли.', 'Не надсилати листів про відповіді й згадки'),
    notes: [
      ['Коли', 'Одразу. Кілька відповідей в одному обговоренні за годину — один лист «3 нові відповіді».'],
      ['Кнопка', 'Читалка на цьому абзаці з відкритим обговоренням і полем відповіді.'],
      ['Зміст', 'Ваш коментар — одним рядком, відповідь — до 600 знаків, далі «…».'],
      ['Відписка', 'Від відповідей і згадок разом — як рядок у налаштуваннях сповіщень.']
    ]
  },
  {
    id: 'ment', name: 'Згадка', when: '11:20',
    subj: 'orysia згадала вас у коментарі',
    pre: '@marta_k, глянь, як ви переклали «велику лінзу» в 12-й главі — у 30-й інакше.',
    body: () => `<span class="ml-lab">Згадка</span>
<p class="ml-h">orysia згадала вас</p>
<p class="ml-meta">Доглядач маяка · глава 30 · обговорення абзацу</p>
<div><p class="ml-who"><b>orysia</b> · 11:19</p><p class="ml-q"><span class="ml-at">@marta_k</span>, глянь, як ви переклали «велику лінзу» в 12-й главі — у 30-й вона стала «великим склом». Може, внести в словник?</p></div>
<a href="#" class="ml-btn">Відкрити обговорення</a>`,
    foot: footN('Ви отримали цей лист, бо вас згадали. Згадка команди ($solona-voda) приходить кожному в команді.', 'Не надсилати листів про відповіді й згадки'),
    notes: [
      ['Коли', 'Одразу після згадки @нік чи $команда — у коментарі, обговоренні абзацу або загальному чаті.'],
      ['Кнопка', 'Туди, де згадали: абзац з обговоренням або повідомлення в чаті.'],
      ['Не надсилаємо', 'Якщо людина вже прочитала згадку на сайті, поки лист чекав у черзі (до 2 хвилин). Те саме для відповідей.']
    ]
  },
  {
    id: 'sug', name: 'Правки прийнято', when: 'вчора',
    subj: 'Команда «Солона вода» прийняла 2 ваші правки',
    pre: 'Доглядач маяка, глава 12 — дякуємо за вичитку!',
    body: () => `<span class="ml-lab">Правки прийнято</span>
<p class="ml-h">2 ваші правки тепер у тексті</p>
<p class="ml-meta">Доглядач маяка · глава 12 · команда «Солона вода»</p>
<p class="ml-ex">— Ти запізнилася, — не обертаючись, <span class="ml-del">сказав</span> <span class="ml-ins">мовив</span> майстер Орест. Він натирав велику лінзу ганчіркою, <span class="ml-del">що</span> <span class="ml-ins">яка</span> колись була сорочкою.</p>
<p class="ml-sm">«Дякуємо, так звучить краще» — <b>solona_voda</b></p>
<a href="#" class="ml-btn">Подивитися в главі</a>
<p class="ml-sm">Усього прийнято 37 ваших правок. До золотої «Вичитувачки» — ще 63.</p>`,
    foot: footN('Ви отримали цей лист, бо запропонували правки до цієї глави.', 'Не надсилати листів про прийняті правки'),
    notes: [
      ['Коли', 'Раз на добу добіркою: усі правки, прийняті за день. Відхилені — лише на сайті.'],
      ['Зміст', 'Перший абзац із правками — видалене й додане виділено; решта — «і ще N у главах 12, 14». Слово команди — якщо залишили.'],
      ['Кнопка', 'Читалка на першому абзаці з правкою.']
    ]
  },
  {
    id: 'msg', name: 'Нове повідомлення', when: '21:03',
    subj: '3 нові повідомлення від oleh.reads',
    pre: 'Привіт! Бачив, ви берете «Останнього картографа».',
    body: () => `<span class="ml-lab">Повідомлення</span>
<p class="ml-h">oleh.reads пише вам</p>
<div class="ml-msgs"><p class="ml-msg">Привіт! Бачив, ви берете «Останнього картографа».<span class="t">20:58</span></p><p class="ml-msg">Можу допомогти з вичиткою — маю вільні вечори.<span class="t">20:59</span></p><p class="ml-msg">Якщо треба, додайте мене в команду.<span class="t">21:01</span></p></div>
<a href="#" class="ml-btn">Відповісти</a>`,
    foot: footN('Ви отримали цей лист, бо маєте непрочитані повідомлення. Наступний прийде, лише коли прочитаєте ці.', 'Не надсилати листів про повідомлення'),
    notes: [
      ['Коли', 'Раз на непрочитану серію, як у Telegram: перше повідомлення + 10 хвилин тиші. Поки серія не прочитана — нових листів немає.'],
      ['Зміст', 'Останні 3 повідомлення серії. Групова розмова — її назва й хто писав. Картинка — «[картинка]».'],
      ['Кнопка', 'Розмова на сайті з полем відповіді. Відповідь листом не приймаємо.']
    ]
  },
  {
    id: 'run', name: 'Студія: запуск завершено', when: '02:14',
    subj: 'Переклад глав 41–45 завершено',
    pre: '«Доглядач маяка» · 5 глав у чернетках · витрачено 14 шагів',
    body: () => `<span class="ml-lab">Студія · хід перекладу</span>
<p class="ml-h">Глави 41–45 перекладено</p>
<div class="ml-kv"><span>Переклад</span><b>Доглядач маяка</b><span>Глави</span><i>41–45 · у чернетках</i><span>Як</span><i>Перекласти з ШІ · ваш словник, 64 слова</i><span>Тривало</span><i>18 хв</i><span>Витрачено</span><i>14 шагів · лишилось 72</i></div>
<p>Глави чекають у чернетках: перегляньте й опублікуйте, коли будете готові.</p>
<a href="#" class="ml-btn">Відкрити в Студії</a>`,
    foot: footN('Ви отримали цей лист, бо запустили переклад у Студії.', 'Не надсилати листів про Студію'),
    notes: [
      ['Коли', 'Коли запуск скінчився, а людини немає на сторінці «Хід перекладу» (інакше вона й так бачить).'],
      ['Кнопка', 'Переклад у Студії, глави 41–45. Якщо запуск публікує сам — «опубліковано» й посилання в читалку.'],
      ['Відписка', 'Від усіх листів Студії: запуски, правки на перевірку.']
    ]
  },
  {
    id: 'runErr', name: 'Студія: запуск зупинився', when: '02:31',
    subj: 'Автопереклад зупинився на главі 64',
    pre: 'Сайт оригіналу не відповідає. Глави 61–63 готові, за 64–65 шаги не списано.',
    body: () => `<span class="ml-lab">Студія · хід перекладу</span>
<p class="ml-h">Автопереклад зупинився на главі 64</p>
<p class="ml-warn"><b>Сайт оригіналу не відповідає.</b> Ми пробували тричі за 20 хвилин. Так буває, коли сайт перевантажений або змінився.</p>
<div class="ml-kv"><span>Переклад</span><b>Маг води</b><span>Готово</span><i>61–63 · у чернетках</i><span>Не зроблено</span><i>64–65 · шаги не списано</i></div>
<a href="#" class="ml-btn">Продовжити з глави 64</a>
<p class="ml-sm">Або <a href="#" class="ml-a">вставте оригінал глави 64 вручну</a> — далі автопереклад піде сам.</p>`,
    foot: footN('Ви отримали цей лист, бо запустили автопереклад у Студії.', 'Не надсилати листів про Студію'),
    notes: [
      ['Коли', 'Одразу, коли запуск зупинився з помилкою, — навіть якщо людина на сайті (помилку легко проґавити).'],
      ['Причина', 'Людськими словами, як на сайті: сайт не відповідає, бракує шагів, ШІ відмовився перекладати главу.'],
      ['Кнопка', 'Хід перекладу цього запуску з кнопкою «Продовжити».']
    ]
  },
  {
    id: 'rev', name: 'Студія: правки на перевірку', when: '09:00',
    subj: '7 нових правок на перевірку в «Доглядачі маяка»',
    pre: 'Глава 12 — 3, глава 14 — 2, глава 20 — 2.',
    body: () => `<span class="ml-lab">Студія · правки читачів</span>
<p class="ml-h">7 нових правок на перевірку</p>
<p class="ml-meta">Доглядач маяка · команда «Солона вода» · за добу</p>
<div class="ml-kv"><span>Глава 12</span><i>3 правки · oleh.reads</i><span>Глава 14</span><i>2 правки · orysia</i><span>Глава 20</span><i>2 правки · lisova_m</i></div>
<p class="ml-ex" style="font-size:15px">Унизу під тонкою ковдрою туману <span class="ml-del">спала</span> <span class="ml-ins">дрімала</span> гавань, і десь у ній один раз ударив дзвін.</p>
<a href="#" class="ml-btn">Перевірити правки</a>`,
    foot: footN('Ви отримали цей лист, бо ви в команді «Солона вода».', 'Не надсилати листів про Студію'),
    notes: [
      ['Коли', 'Раз на добу о 9:00 за Києвом, якщо є нові правки. Кожному в команді, хто може їх приймати.'],
      ['Зміст', 'Скільки й де, одна правка для прикладу. Понад 5 глав — «і ще в N главах».'],
      ['Кнопка', 'Студія → «Правки на перевірку» цього перекладу.']
    ]
  },
  {
    id: 'relay', name: 'Естафета', when: '16:22',
    subj: 'Хочуть продовжити ваш переклад «Академія під дощем»',
    pre: 'Команда «Компас»: «Дуже любимо цю історію, перекладемо до кінця».',
    body: () => `<span class="ml-lab">Естафета</span>
<p class="ml-h">Команда «Компас» хоче продовжити ваш переклад</p>
<div class="ml-nov">${cov('c2', 'Академія під дощем')}<div><p class="ml-h3">Академія під дощем</p><p class="ml-meta" style="margin-top:4px">54 з 120 глав · остання — 5 місяців тому</p></div></div>
<div><p class="ml-who"><b>Компас</b> пишуть:</p><p class="ml-q">Дуже любимо цю історію, перекладемо до кінця. Ваш словник і назви залишимо, а вас — в авторах перших 54 глав.</p></div>
<a href="#" class="ml-btn">Відповісти</a>
<p class="ml-sm">Якщо не відповісти за 14 днів, а нових глав і далі не буде, переклад стане вільним для продовження.</p>`,
    foot: footN('Ви отримали цей лист, бо ви власник перекладу «Академія під дощем».', 'Не надсилати листів про естафету'),
    notes: [
      ['Коли', 'Одразу після запиту. Через 10 днів без відповіді — нагадування «лишилось 4 дні».'],
      ['Кнопка', 'Студія → переклад → «Естафета»: передати, працювати разом або відмовити.'],
      ['Той, хто просив', 'Отримує лист із відповіддю — той самий вигляд, кнопка «Відкрити переклад».']
    ]
  },
  {
    id: 'ach', name: 'Досягнення', when: '14:10',
    subj: 'Нове досягнення — «Вичитувачка», срібна медаль',
    pre: '25 прийнятих правок. До золотої — 100.',
    body: () => `<span class="ml-lab">Досягнення</span>
<div class="ml-nov" style="align-items:center"><span class="ml-medal">25</span><div><p class="ml-h">Вичитувачка</p><p class="ml-meta" style="margin-top:4px">срібна медаль · 25 прийнятих правок</p></div></div>
<p>Команди прийняли 25 ваших правок — тексти на Новелці стали чистішими. Дякуємо!</p>
<div class="ml-st"><p class="ml-sm">До золотої — 100 правок · у вас 25</p><span class="ml-bar"><i style="width:25%"></i></span></div>
<a href="#" class="ml-btn">Подивитися в профілі</a>`,
    foot: footN('Ви отримали цей лист, бо отримали нове досягнення.', 'Не надсилати листів про досягнення'),
    notes: [
      ['Коли', 'Одразу. Кілька досягнень за день — одним листом.'],
      ['Медаль', 'Картинкою, як на сайті; якщо картинки вимкнено — підпис «срібна медаль».'],
      ['Типово', 'Пошта для досягнень вимкнена (налаштування сповіщень); лист — для тих, хто ввімкнув.']
    ]
  },
  {
    id: 'shahs', name: 'Шаги нараховано', when: '10:05',
    subj: 'Вам нараховано 50 шагів',
    pre: 'Від власника сайту — «за вичитку тому 1».',
    body: () => `<span class="ml-lab">Шаги</span>
<p class="ml-big">+50<small>шагів</small></p>
<p>Власник сайту нарахував вам 50 шагів <b>«за вичитку тому 1»</b>.</p>
<div class="ml-kv"><span>На рахунку</span><b>136 шагів</b><span>У резерві</span><i>12 — за запуск, що ще йде</i></div>
<a href="#" class="ml-btn">Історія шагів</a>`,
    foot: footN('Ви отримали цей лист, бо змінився ваш рахунок шагів.', 'Не надсилати листів про шаги'),
    notes: [
      ['Коли', 'Одразу після нарахування. Списання за запуски — лише в листі про запуск, окремих листів немає.'],
      ['Кнопка', 'Налаштування → Шаги, історія.']
    ]
  },
  {
    id: 'shahsLow', name: 'Шаги закінчуються', when: '23:40',
    subj: 'Лишилося 18 шагів',
    pre: 'Цього вистачить приблизно на 6 глав «Перекласти з ШІ».',
    body: () => `<span class="ml-lab">Шаги</span>
<p class="ml-big">18<small>шагів лишилось</small></p>
<p>Цього вистачить приблизно на 6 глав «Перекласти з ШІ». Ви просили попередити, коли лишиться менше 20.</p>
<a href="#" class="ml-btn">Як отримати шаги</a>
<p class="ml-sm">Межу можна змінити в <a href="#" class="ml-a">налаштуваннях шагів</a>.</p>`,
    foot: footN('Ви отримали цей лист, бо рахунок шагів опустився нижче вашої межі.', 'Не надсилати листів про шаги'),
    notes: [
      ['Коли', 'Раз, коли рахунок перейшов межу з налаштувань (типово 20). Знову — лише після поповнення вище межі.'],
      ['Кнопка', 'Сторінка «Шаги» з поясненням, як їх отримати (поповнення — після запуску оплати).']
    ]
  }
];

// ── Службові листи ──
const SERVICE = [
  {
    id: 'verify', name: 'Підтвердження пошти', when: '12:30',
    subj: 'Підтвердіть пошту на Новелці',
    pre: 'Одна кнопка — і можна читати, підписуватися на новели й перекладати.',
    body: () => `<span class="ml-lab">Реєстрація</span>
<p class="ml-h">Підтвердіть пошту</p>
<p>Привіт, <b>marta_k</b>! Щоб завершити реєстрацію на Новелці, підтвердіть, що ця пошта ваша.</p>
<a href="#" class="ml-btn">Підтвердити пошту</a>
<p class="ml-sm">Посилання діє добу. Кнопка не відкривається? Скопіюйте адресу в браузер:</p>
<p class="ml-link">https://novelka.in.ua/verify?token=3f9c2e7b0d14a8e6c5b1f09a71e</p>
<div class="ml-hr"></div>
<p class="ml-sm">Не реєструвалися на Новелці? Просто проігноруйте лист — без підтвердження акаунт не запрацює.</p>`,
    foot: footS(),
    notes: [
      ['Коли', 'Одразу після реєстрації й після «Надіслати ще раз» на сторінці підтвердження.'],
      ['Кнопка', '/verify — пошту підтверджено, далі перший крок (/welcome). Посилання діє добу.'],
      ['Відписка', 'Немає: лист службовий. Немає й «Налаштувати сповіщення» — акаунт ще не працює.'],
      ['Вхід через Google', 'Цей лист не надсилаємо: пошту вже підтвердив Google.']
    ]
  },
  {
    id: 'reset', name: 'Новий пароль', when: '08:12',
    subj: 'Новий пароль для Новелки',
    pre: 'Посилання діє 30 хвилин і спрацює один раз.',
    body: () => `<span class="ml-lab">Вхід і безпека</span>
<p class="ml-h">Задайте новий пароль</p>
<p>Привіт, <b>marta_k</b>! Хтось — сподіваємось, ви — попросив новий пароль для вашого акаунта.</p>
<a href="#" class="ml-btn">Задати новий пароль</a>
<p class="ml-sm">Посилання діє 30 хвилин і спрацює один раз. Кнопка не відкривається? Скопіюйте адресу в браузер:</p>
<p class="ml-link">https://novelka.in.ua/reset?token=a81d40c3e9f25b7d06c4e1</p>
<div class="ml-hr"></div>
<p class="ml-sm">Не просили? Нічого не робіть — старий пароль лишиться чинним.</p>`,
    foot: footS(' <a href="#">Налаштувати сповіщення</a>'),
    notes: [
      ['Коли', 'Після «Забули пароль?» на сторінці входу; акаунтові без пароля (лише Google) — після «Задати пароль» у налаштуваннях.'],
      ['Кнопка', '/reset — поле нового пароля. Після зміни — лист «Пароль змінено».'],
      ['Telegram', 'Те саме посилання бот надсилає завжди, якщо Telegram прив’язано.']
    ]
  },
  {
    id: 'pwd', name: 'Пароль змінено', when: '08:15',
    subj: 'Пароль на Новелці змінено',
    pre: 'Усі інші пристрої вийшли з акаунта.',
    body: () => `<span class="ml-lab">Вхід і безпека</span>
<p class="ml-h">Пароль змінено</p>
<p>Привіт, <b>marta_k</b>! Пароль вашого акаунта щойно змінено, а всі інші пристрої вийшли з акаунта.</p>
<div class="ml-kv"><span>Коли</span><i>23 жовтня, 08:15</i><span>Пристрій</span><i>Safari, iPhone · Київ</i></div>
<div class="ml-hr"></div>
<p class="ml-h3">Це були не ви?</p>
<p class="ml-sm">Відновіть пароль — посилання прийде на цю пошту, і всі пристрої вийдуть з акаунта.</p>
<a href="#" class="ml-btn">Відновити пароль</a>`,
    foot: footS(' <a href="#">Налаштувати сповіщення</a>'),
    notes: [
      ['Коли', 'Після будь-якої зміни пароля: у налаштуваннях або за посиланням «Новий пароль».'],
      ['Кнопка', 'Лише для «це були не ви»: /reset з уже вписаною поштою.'],
      ['Пристрій', 'Браузер і система з сесії, місто — приблизно за адресою; без IP.']
    ]
  },
  {
    id: 'newmail', name: 'Зміна пошти: нова адреса', when: '17:40',
    subj: 'Підтвердіть нову пошту на Новелці',
    pre: 'Доки не підтвердите, лишається чинною стара адреса.',
    body: () => `<span class="ml-lab">Вхід і безпека</span>
<p class="ml-h">Це ваша нова пошта?</p>
<p>Привіт, <b>marta_k</b>! Ви вказали цю адресу як нову пошту свого акаунта на Новелці.</p>
<div class="ml-kv"><span>Була</span><i>marta.k@ukr.net</i><span>Стане</span><b>marta.kovalenko@gmail.com</b></div>
<a href="#" class="ml-btn">Підтвердити нову пошту</a>
<p class="ml-sm">Доки ви цього не зробите, лишається чинною стара адреса. Посилання діє добу.</p>
<p class="ml-link">https://novelka.in.ua/confirm-email?token=6be0f1d27a93c4e85b</p>`,
    foot: footS(),
    notes: [
      ['Коли', 'Після «Змінити пошту» в налаштуваннях. Лист іде на нову адресу.'],
      ['Кнопка', '/confirm-email — пошту змінено, на стару адресу йде наступний лист.'],
      ['Стара адреса', 'Показуємо повністю: людина її знає, а лист прийшов на нову.']
    ]
  },
  {
    id: 'oldmail', name: 'Зміна пошти: стара адреса', when: '17:43',
    subj: 'Пошту акаунта на Новелці змінено',
    pre: 'Тепер листи приходитимуть на ma•••@gmail.com.',
    body: () => `<span class="ml-lab">Вхід і безпека</span>
<p class="ml-h">Пошту акаунта змінено</p>
<p>Привіт, <b>marta_k</b>! Пошту вашого акаунта змінено на <b>ma•••@gmail.com</b>. Цей лист — останній на цю адресу.</p>
<div class="ml-kv"><span>Коли</span><i>23 жовтня, 17:43</i><span>Пристрій</span><i>Chrome, Windows · Львів</i></div>
<div class="ml-hr"></div>
<p class="ml-h3">Це були не ви?</p>
<p class="ml-sm">Скасуйте зміну протягом 7 днів: пошта повернеться на цю адресу, а всі пристрої вийдуть з акаунта.</p>
<a href="#" class="ml-btn">Скасувати зміну пошти</a>`,
    foot: footS(),
    notes: [
      ['Коли', 'Щойно нову пошту підтверджено. Лист іде на стару адресу.'],
      ['Нова адреса', 'Частково прихована (ma•••@gmail.com): якщо акаунт захопили, лист не видає нову адресу повністю.'],
      ['Кнопка', 'Одноразове посилання на 7 днів: повертає стару пошту, виходить з усіх пристроїв і просить новий пароль. Цього на сайті ще немає — див. README.']
    ]
  },
  {
    id: 'nick', name: 'Нік змінено', when: '13:05',
    subj: 'Ваш нік на Новелці змінено',
    pre: 'marta_k → marta_kovalenko. Наступна зміна — не раніше ніж через 30 днів.',
    body: () => `<span class="ml-lab">Профіль</span>
<p class="ml-h">Нік змінено</p>
<p class="ml-big" style="font-size:22px"><span class="ml-del" style="text-decoration:line-through">marta_k</span> → <span class="ml-ins">marta_kovalenko</span></p>
<p>Так вас тепер бачать у коментарях, командах і на профілі. Змінити нік знову можна буде <b>22 листопада</b>.</p>
<a href="#" class="ml-btn">Відкрити профіль</a>
<div class="ml-hr"></div>
<p class="ml-sm">Це були не ви? <a href="#" class="ml-a">Відновіть пароль</a> і напишіть адміністрації.</p>`,
    foot: footS(' <a href="#">Налаштувати сповіщення</a>'),
    notes: [
      ['Коли', 'Після зміни ніка в налаштуваннях (раз на 30 днів). Бот у Telegram надсилає те саме завжди.'],
      ['Кнопка', 'Профіль з новим ніком.']
    ]
  },
  {
    id: 'del', name: 'Код для видалення перекладу', when: '20:51',
    subj: 'Код для видалення перекладу на Новелці',
    pre: 'Код для «Останнього картографа». Діє 15 хвилин.',
    body: () => `<span class="ml-lab">Студія · видалення</span>
<p class="ml-h">Код для видалення перекладу</p>
<p>Ви видаляєте переклад <b>«Останній картограф»</b>. Введіть цей код у Студії:</p>
<p class="ml-code">482 917</p>
<p class="ml-sm">Код діє 15 хвилин, 5 спроб. Переклад піде в кошик, і 30 днів його можна буде відновити в Студії.</p>
<div class="ml-hr"></div>
<p class="ml-sm">Нічого не видаляли? Не вводьте код і <a href="#" class="ml-a">змініть пароль</a>.</p>`,
    foot: footS(),
    notes: [
      ['Коли', 'Після «Видалити переклад» у Студії. Той самий код — у Telegram, якщо прив’язано.'],
      ['Кнопки', 'Немає: код вводять на сайті. Цифри — великі, двома групами, щоб легко переписати з телефона.']
    ]
  }
];

export const MailNotifK = screen('MailNotifK.dc.html', 'Листи зі сповіщеннями — пошта', NOTIF);
export const MailServiceK = screen('MailServiceK.dc.html', 'Службові листи — пошта', SERVICE);
