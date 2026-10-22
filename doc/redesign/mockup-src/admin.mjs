// Адміністрування (комп'ютер) і «Спершу»: терміни й доступ, заготовки й стани завантаження.
import { ic, iff } from './lib.mjs';
import { CHROME_CSS, deskFrame } from './chrome.mjs';
import { READER_CSS } from './reader.mjs';
import { SITE_CSS } from './site.mjs';
import { COMM_CSS } from './comm.mjs';
import { STUDIO_CSS } from './studio.mjs';

const ADMIN_CSS = `
.ad-g{display:grid;grid-template-columns:240px minmax(0,1fr);gap:36px;align-items:start}
.cmp-t{display:grid;grid-template-columns:120px minmax(0,1fr) 140px auto;gap:12px;align-items:center;padding:12px 4px;border-bottom:1px solid var(--line);font-size:14px}
.cmp-t.h{font-size:12px;color:var(--faint);padding:8px 4px}
.cmp-t .k{font-size:12px;font-weight:700;color:var(--acc)}
.cmp-t .m{font-size:12.5px;color:var(--faint)}
.k-pal .cmp-t{border-bottom:1px dotted var(--line)}
.k-obl .cmp-t{border:0;background:var(--s1);border-radius:16px;margin-bottom:4px;padding:12px}
.k-obl .cmp-t.h{background:none}
.k-pult .cmp-t .m,.k-pult .cmp-t .k{font-family:var(--f-num)}
.us{display:grid;grid-template-columns:36px minmax(0,1fr) 140px 110px 150px auto;gap:12px;align-items:center;padding:10px 4px;border-bottom:1px solid var(--line);font-size:14px}
.us .av{width:32px;height:32px;font-size:12px}
.us .m{font-size:12.5px;color:var(--faint)}
.us.h{font-size:12px;color:var(--faint)}
.chart{display:flex;align-items:flex-end;gap:6px;height:160px;padding:10px 0;border-bottom:1px solid var(--line)}
.chart i{flex:1;background:var(--acc);border-radius:3px 3px 0 0;opacity:.85;display:block}
.chart i.b{background:var(--acc2)}
.k-pult .chart i{border-radius:0}
.mt{display:grid;grid-template-columns:110px minmax(0,1fr) 110px 110px 90px 80px;gap:10px;padding:9px 4px;border-bottom:1px solid var(--line);font-size:13.5px;align-items:center}
.mt.h{font-size:12px;color:var(--faint)}
.mt .n{font-variant-numeric:tabular-nums;text-align:right}
.au{display:grid;grid-template-columns:120px 120px minmax(0,1fr);gap:12px;padding:10px 4px;border-bottom:1px solid var(--line);font-size:14px}
.au .t{font-size:12.5px;color:var(--faint)}
/* терміни й доступ */
.acc4{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}
.ac{display:flex;flex-direction:column;gap:10px;padding:16px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.ac b{font:var(--wh) 19px/1.2 var(--f-h);color:var(--strong)}
.ac ul{display:flex;flex-direction:column;gap:6px}
.ac li{font-size:13.5px;line-height:1.4;display:grid;grid-template-columns:18px 1fr;gap:6px;color:var(--tx)}
.ac li .i{color:var(--acc2);margin-top:1px}
.ac li.no{color:var(--faint)} .ac li.no .i{color:var(--faint)}
.k-pal .ac{background:transparent;box-shadow:none;border-top:1px solid var(--acc);border-radius:0;padding:14px 0}
.k-obl .ac{box-shadow:none;border-radius:24px}
.tt{display:grid;grid-template-columns:220px minmax(0,1fr) 240px;gap:14px;padding:10px 4px;border-bottom:1px solid var(--line);font-size:14px}
.tt b{color:var(--strong)}
.tt .no{color:var(--faint);text-decoration:line-through}
.tt.h{font-size:12px;color:var(--faint)}
.k-pal .tt{border-bottom:1px dotted var(--line)}
/* завантаження */
.ld-g{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}
.ld-c{display:flex;flex-direction:column;gap:12px;padding:16px;border-radius:var(--rcard);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.k-pal .ld-c{background:transparent;box-shadow:none;border-top:1px solid var(--line);border-radius:0;padding:14px 0}
.k-obl .ld-c{box-shadow:none;border-radius:24px}
.tl3{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}
.tl3 div{display:flex;flex-direction:column;gap:4px;padding:12px;border-radius:var(--rin);box-shadow:inset 0 0 0 1px var(--line);font-size:13.5px}
.tl3 b{font:var(--wh) 20px/1 var(--f-num);color:var(--acc)}
.stp{display:flex;flex-direction:column;gap:8px}
.stp div{display:flex;align-items:center;gap:10px;font-size:14px}
.stp .ok{color:var(--acc2)} .stp .cur{color:var(--strong);font-weight:600}
`;
const CSS = READER_CSS + CHROME_CSS + SITE_CSS + COMM_CSS + STUDIO_CSS + ADMIN_CSS;

// ── Адміністрування ──
const AM = [['ov', 'cols', 'Огляд'], ['mod', 'flag', 'Скарги', '3'], ['users', 'users', 'Люди й ролі', '1'], ['set', 'gear', 'Налаштування сайту'], ['stat', 'pulse', 'Аналітика'], ['audit', 'clock', 'Журнал дій'], ['wallet', 'plus', 'Гаманець власника']];
const amenu = (on) => `<nav class="trm" aria-label="Адміністрування">${AM.map(([id, i, t, n]) => `<a href="#" class="${id === on ? 'on' : ''}" onClick="{{ go.${id} }}">${ic(i, 18)}${t}${n ? `<span class="n">${n}</span>` : ''}</a>`).join('')}</nav>`;
const ov = `<h1 class="h1">Адміністрування</h1><div class="att">${[['3', 'скарги чекають', 'mod', 'Розібрати'], ['1', 'запит на автопереклад із сайту', 'users', 'Відповісти'], ['2', 'запуски в черзі · 1 іде', 'stat', 'Дивитися'], ['$41,20', 'баланс провайдера ШІ · ≈ [N] шагів', 'wallet', 'Гаманець']].map(([n, t, id, a]) => `<a href="#" onClick="{{ go.${id} }}" class="at"><b>${n}</b><span>${t}</span><span class="acc">${a} ›</span></a>`).join('')}</div>
<section class="sec"><div class="sec-h"><h2 class="h2">Приховане</h2></div>${[['Переклад «Академія під дощем»', 'скарга правовласника · приховано автоматично · 2 дні тому'], ['Коментар @spam_bot у «Маг води», гл. 50', 'приховала orysia · учора']].map(([t, m]) => `<div class="rq"><span class="col"><b class="strong">${t}</b><span class="faint" style="font-size:13px">${m}</span></span><button type="button" class="btn sm out">Повернути</button></div>`).join('')}</section>`;
const mod = `<h1 class="h1">Скарги</h1><div class="flt-row"><span class="chip on">Нові · 3</span><span class="chip">Розглянуті</span><span class="chip">Приховане · 2</span></div>
<div class="cmp-t h"><span>На що</span><span>Що й чому</span><span>Хто, коли</span><span></span></div>
${[['правовласник', 'Переклад «Академія під дощем»', '«Я видавець оригіналу, ліцензії немає» · переклад уже приховано', 'han.publisher · 2 дні'], ['коментар', '«Маг води», гл. 50 · @spam_bot', 'спам: посилання на сторонній сайт', 'nika · 3 год'], ['повідомлення', 'Особиста розмова · @rude_one', 'образи · модератор бачить лише це й 2 сусідні', 'tanya · 1 год']].map(([k, t, w, m]) => `<div class="cmp-t"><span class="k">${k}</span><span class="col"><span>${t}</span><span class="m">${w}</span></span><span class="m">${m}</span><span class="row" style="gap:6px"><button type="button" class="btn sm out">Відкрити</button><button type="button" class="btn sm">Приховати</button><button type="button" class="btn sm out">Відхилити</button></span></div>`).join('')}`;
const users = `<div class="bar-act"><h1 class="h1">Люди й ролі</h1><span class="field" style="max-width:280px">${ic('search', 16)} Нік чи пошта</span></div>
<div class="banner" style="box-shadow:inset 0 0 0 1px var(--acc)">${ic('relay', 20)}<span class="col grow" style="gap:4px"><b class="strong">taras.p просить доступ до автоперекладу із сайту</b><span class="dim" style="font-size:13px">«Хочу вести "Сто днів у вежі Аліти" з syosetu» · учора</span></span><button type="button" class="btn sm pri">Відкрити</button><button type="button" class="btn sm out">Відмовити</button></div>
<div class="us h"><span></span><span>Людина</span><span>Роль на сайті</span><span>Шаги</span><span>Автопереклад із сайту</span><span></span></div>
${[['О', 'orysia', 'модераторка', '120', 'відкрито'], ['Т', 'taras.p', 'користувач', '40', 'запит'], ['М', 'marta_k', 'користувачка', '86', 'закрито'], ['Н', 'nika', 'користувачка', '0', 'закрито']].map(([a, n, r, s, at]) => `<div class="us"><span class="av">${a}</span><span>${n}</span><span><span class="chip">${r} ${ic('down', 12)}</span></span><span class="m">${s} · <a href="#" class="acc">нарахувати</a></span><span class="state ${at === 'відкрито' ? 'pub' : at === 'запит' ? 'dr' : 'q'}"><i></i>${at}</span><button type="button" class="ib" aria-label="Ще">${ic('more', 18)}</button></div>`).join('')}`;
const sset = `<h1 class="h1">Налаштування сайту</h1><div class="col" style="max-width:820px">${[['Сховати ШІ для людей без шагів', 'немає жодної кнопки з ШІ; «Новий переклад» пропонує «Вставлю оригінал» без ШІ', '<span class="tog on"></span>'], ['Реєстрація', '', '<div class="seg" style="min-width:220px"><span class="on">Відкрита</span><span>За запрошенням</span></div>'], ['Автопереклад із сайту', '', '<div class="seg" style="min-width:240px"><span>Лише власнику</span><span class="on">З дозволу</span></div>'], ['18+ для гостей', 'гості й непідтверджені не бачать', '<span class="tog on"></span>'], ['Строк естафети', 'без глав і відповіді власника', '<span class="field" style="width:110px;height:34px">3 місяці</span>'], ['Межа довгої глави', 'ділиться на частини для ШІ', '<span class="field" style="width:130px;height:34px">40 000 знаків</span>'], ['Собівартість шагу', '1 шаг = витрати на моделі', '<span class="field" style="width:110px;height:34px">$0,07</span>'], ['Тема й каркас за замовчуванням', 'для нових відвідувачів', '<span class="dim">Пульт · Сланець</span>'], ['Моделі й набори', 'Найкраща · Висока · Швидка чернетка', '<a href="#" class="acc">Змінити ›</a>']].map(([t, s, r]) => `<div class="set-r"><span>${t}${s ? `<small>${s}</small>` : ''}</span>${r}</div>`).join('')}</div>`;
const stat = `<div class="bar-act"><h1 class="h1">Аналітика</h1><div class="seg" style="flex:none"><span>7 днів</span><span class="on">30 днів</span><span>Рік</span></div></div><div class="att">${[['$38,40', 'витрати на моделі'], ['412', 'глав перекладено'], ['$0,09', 'середня ціна глави'], ['6,1 %', 'абзаців виправили люди']].map(([n, t]) => `<div class="at"><b>${n}</b><span>${t}</span></div>`).join('')}</div>
<section class="sec"><div class="sec-h"><h2 class="h2">Витрати за днями</h2><span class="faint" style="font-size:12.5px">переклад · вичитка</span></div><div class="chart">${[30, 45, 22, 60, 72, 40, 55, 80, 66, 35, 50, 90, 70, 44].map((h, i) => `<i class="${i % 3 === 2 ? 'b' : ''}" style="height:${h}%"></i>`).join('')}</div></section>
<section class="sec"><div class="sec-h"><h2 class="h2">Моделі на кроках</h2></div><div class="mt h"><span>Крок</span><span>Модель</span><span>Ціна гл.</span><span>Швидкість</span><span>Збої</span><span>Правлять</span></div>${[['Переклад', 'Claude Sonnet', '$0,061', '42 с', '0,4 %', '5,8 %'], ['Вичитка', 'Claude Haiku', '$0,012', '11 с', '0,1 %', '—'], ['Словник', 'Claude Haiku', '$0,008', '9 с', '0,2 %', 'погоджено 91 %']].map(r => `<div class="mt">${r.map((c, i) => `<span class="${i > 1 ? 'n' : ''}">${c}</span>`).join('')}</div>`).join('')}</section>`;
const audit = `<h1 class="h1">Журнал дій</h1>${[['сьогодні 12:04', 'власник', 'Нараховано 50 шагів marta_k · «за вичитку тому 1»'], ['сьогодні 09:30', 'власник', 'Відкрито автопереклад із сайту для orysia'], ['учора 18:12', 'orysia', 'Приховано коментар @spam_bot у «Маг води», гл. 50'], ['учора 10:00', 'власник', 'Модель перекладу «Висока»: Claude Sonnet замість GPT'], ['20 жовт.', 'власник', 'Увімкнено «Сховати ШІ для людей без шагів»']].map(([t, w, d]) => `<div class="au"><span class="t">${t}</span><span>${w}</span><span>${d}</span></div>`).join('')}`;
const wallet = `<h1 class="h1">Гаманець власника</h1><div class="att">${[['$41,20', 'залишок OpenRouter'], ['≈ [N]', 'шагів за собівартістю'], ['3', 'одночасно глав'], ['$0,036', 'за 10 тис. знаків, мін.']].map(([n, t]) => `<div class="at"><b>${n}</b><span>${t}</span></div>`).join('')}</div>
<div class="col" style="max-width:820px">${[['Показувати мені шаги', 'інакше — долари', '<span class="tog on"></span>'], ['Ціна шагу для людей', '', '<span class="field" style="width:110px;height:34px">$0,07</span>'], ['Одночасно глав', 'різних новел', '<div class="seg" style="min-width:200px"><span>1</span><span>2</span><span class="on">3</span><span>4</span><span>5</span></div>'], ['Моделі картинок', '', '<a href="#" class="acc">Змінити ›</a>']].map(([t, s, r]) => `<div class="set-r"><span>${t}${s ? `<small>${s}</small>` : ''}</span>${r}</div>`).join('')}</div>
<section class="sec"><div class="sec-h"><h2 class="h2">Собівартість глави</h2></div><div class="mt h"><span>Набір</span><span>Моделі</span><span>Мін.</span><span>Середня</span><span>Макс.</span><span>Глав</span></div>${[['Найкраща', 'Opus · Sonnet', '$0,14', '$0,22', '$0,41', '38'], ['Висока', 'Sonnet · Haiku', '$0,05', '$0,08', '$0,15', '311'], ['Швидка', 'Haiku', '$0,01', '$0,02', '$0,04', '63']].map(r => `<div class="mt">${r.map((c, i) => `<span class="${i > 1 ? 'n' : ''}">${c}</span>`).join('')}</div>`).join('')}</section>`;
const BODY = { ov, mod, users, set: sset, stat, audit, wallet };
const adBody = `<div class="page">${AM.map(([id], i) => iff(`is.${id}`, `<div class="ad-g">${amenu(id)}<div class="col" style="gap:16px;min-width:0">${BODY[id]}</div></div>`, i === 0)).join('')}</div>`;
export const AdminK = { file: 'AdminK.dc.html', title: 'Адміністрування — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['огляд', 'скарги', 'люди й ролі', 'налаштування сайту', 'аналітика', 'журнал дій', 'гаманець власника'], ids: AM.map(x => x[0]), extra: CSS,
  root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: '<b>адміністрування</b>', main: adBody })}</div>` };

// ── Терміни й доступ (рішення 14, 15, 20) ──
const yes = (t) => `<li>${ic('check', 16)}<span>${t}</span></li>`;
const no = (t) => `<li class="no">${ic('x', 16)}<span>${t}</span></li>`;
const terms = `<div class="page"><div class="col" style="gap:8px"><h1 class="h1">Хто що може</h1><p class="dim" style="font-size:14.5px">Доступ майже однаковий: перекладати може кожен, хто увійшов. З дозволу власника сайту — лише автопереклад із сайту.</p></div>
<div class="acc4"><div class="ac"><b>Гість</b><ul>${yes('Читає й шукає')}${yes('Читає загальний чат')}${no('Бібліотека, правки, коментарі, повідомлення')}</ul></div>
<div class="ac"><b>Читач</b><span class="faint" style="font-size:12.5px">увійшов</span><ul>${yes('Бібліотека й підписки')}${yes('Правки, коментарі, оцінки, повідомлення')}${yes('Пропонує, що перекласти')}</ul></div>
<div class="ac"><b>Перекладач</b><span class="faint" style="font-size:12.5px">будь-хто, хто увійшов</span><ul>${yes('Вставляє оригінал, перекладає сам')}${yes('«Перекласти з ШІ» — за свої шаги')}${yes('Команда, словник, правки читачів')}</ul></div>
<div class="ac"><b>З дозволу</b><span class="faint" style="font-size:12.5px">власника сайту</span><ul>${yes('Автопереклад із сайту за посиланням')}${yes('Нові глави щотижня, «до глави N»')}${no('Без дозволу — замок і «Попросити доступ»')}</ul></div></div>
<section class="sec"><div class="sec-h"><h2 class="h2">Терміни</h2><span class="faint" style="font-size:12.5px">однаково в меню, кнопках, сповіщеннях, листах і Telegram</span></div>
<div class="tt h"><span>Кажемо</span><span>Що означає</span><span>Не кажемо</span></div>
${[['Каркас', 'будова сайту: Пульт, Звичний, Палітурка, Обкладинка', 'шаблон, скін'], ['Тема', 'кольори, незалежно від каркаса', 'палітра'], ['Студія', 'застосунок для перекладу, /studio', 'майстерня, адмінка'], ['Переклад', 'українська версія новели від однієї команди', 'видання, едішн'], ['Перекласти з ШІ', 'ШІ перекладає вставлений оригінал', 'автопереклад вставленого'], ['Автопереклад із сайту', 'глави беруться самі за посиланням', 'імпорт за посиланням'], ['Хід перекладу', 'переклад наживо й усі запуски', 'журнал, лог'], ['Заготовка сторінки', 'сірі форми, поки вміст вантажиться', 'скелетон, каркас'], ['Сповіщення', 'дзвоник: нові глави, відповіді, правки', 'вхідні, нотифікації'], ['Шаги', 'гроші сайту', 'кредити, токени']].map(([a, b, c]) => `<div class="tt"><b>${a}</b><span>${b}</span><span class="no">${c}</span></div>`).join('')}</section></div>`;
export const TermsK = { file: 'TermsK.dc.html', title: 'Терміни й доступ — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['терміни'], ids: ['t'], extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: '<b>терміни й доступ</b>', main: terms })}</div>` };

// ── Завантаження й очікування (рішення 16, 34) ──
const loading = `<div class="page"><div class="col" style="gap:8px"><h1 class="h1">Завантаження й очікування</h1><p class="dim" style="font-size:14.5px">Шапка й меню з’являються одразу. Анімації вимикаються, якщо система просить менше руху.</p></div>
<div class="tl3"><div><b>0 с</b>шапка й меню — одразу</div><div><b>до 0,3 с</b>нічого не показуємо</div><div><b>0,3 с</b>заготовка сторінки: сірі форми розміру вмісту</div><div><b>8 с</b>«Довше, ніж зазвичай» і «Спробувати ще раз»</div></div>
<div class="ld-g">
<div class="ld-c"><span class="lab">Заготовка списку</span>${[1, 2, 3].map(() => `<div class="row" style="gap:12px"><span class="sk" style="width:44px;height:64px;flex:none"></span><span class="col grow" style="gap:8px"><span class="sk" style="height:14px;width:75%"></span><span class="sk" style="height:12px;width:45%"></span></span></div>`).join('')}</div>
<div class="ld-c"><span class="lab">Заготовка глави</span><span class="sk" style="height:22px;width:70%"></span>${[100, 96, 100, 60, 100, 92].map(w => `<span class="sk" style="height:13px;width:${w}%"></span>`).join('')}</div>
<div class="ld-c"><span class="lab">Довше, ніж зазвичай</span><div class="banner" style="box-shadow:inset 0 0 0 1px var(--line)"><span class="spin" style="color:var(--acc)">${ic('ring', 20, 2.2)}</span><span class="dim">Довше, ніж зазвичай. Повільний інтернет — чекаємо далі.</span></div><button type="button" class="btn sm out" style="align-self:flex-start">${ic('refresh', 15)} Спробувати ще раз</button></div>
<div class="ld-c"><span class="lab">Помилка — людськими словами</span><b class="strong">Не вдалося зберегти главу</b><p class="dim" style="font-size:13.5px;line-height:1.5">Сайт не відповів. Ваш текст лишився в чернетці на цьому пристрої.</p><button type="button" class="btn sm pri" style="align-self:flex-start">Спробувати ще раз</button></div>
<div class="ld-c"><span class="lab">Дія — стан на самій кнопці</span><div class="col" style="gap:8px;align-items:flex-start"><button type="button" class="btn pri">Опублікувати</button><button type="button" class="btn pri" aria-busy="true"><span class="spin">${ic('ring', 16, 2.4)}</span> Публікуємо…</button><button type="button" class="btn sec">${ic('check', 16)} Опубліковано</button></div><span class="faint" style="font-size:12.5px">Сторінка не блокується, решту можна робити далі.</span></div>
<div class="ld-c"><span class="lab">Довга робота</span><b class="strong">EPUB «Доглядач маяка», том 1</b><div class="stp"><div class="ok">${ic('check', 16)} Глави зібрано · 24</div><div class="cur">${ic('ring', 16)} Картинки · 9 з 14</div><div class="faint">${ic('book', 16)} Книжка</div></div><span class="prog"><i style="width:62%"></i></span><span class="faint" style="font-size:12.5px">Сторінку можна закрити — прийде сповіщення.</span></div>
</div></div>`;
export const LoadingK = { file: 'LoadingK.dc.html', title: 'Завантаження й очікування — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: ['усі стани'], ids: ['all'], extra: CSS, root: `<div class="{{ cls }}" style="width:1440px;height:900px">${deskFrame({ active: -1, path: '<b>завантаження</b>', main: loading })}</div>` };
