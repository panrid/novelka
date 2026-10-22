// Перенесено зі сторінки напрямів (mockup/directions/directions.html): робоче місце комп'ютера в кожному каркасі.
import { ic, fmt, iff } from './lib.mjs';
import { ROWS, CHAPS } from './content.mjs';
const themeBtn = (cls) => `<button type="button" class="${cls}" onClick="{{ toggleLight }}" aria-label="Світла чи темна тема">${iff('dark', ic('sun', 18), true)}${iff('light', ic('moon', 18))}</button>`;
export const WS_CSS = `/* ───────── А · «Палітурка»: зелене сукно, латунь, книжкові засічки; без карток, лише поля й лінійки ───────── */
.k-pal {--disp:'Cormorant Garamond', Georgia, serif; --rd:'Spectral', Georgia, serif; --ui:'Alegreya Sans', 'Segoe UI', sans-serif;}
.k-pal .sc { font-variant: all-small-caps; letter-spacing: .09em; font-weight: 500 }
.k-pal .term { background: var(--term); border-radius: 2px; box-shadow: 0 1px 0 var(--acc) }
.a-ph { height: 100%; display: flex; flex-direction: column; background: var(--bg) }
.a-run { height: 50px; flex: none; display: flex; align-items: center; gap: 12px; padding: 0 16px; border-bottom: 1px solid var(--line); color: var(--dim) }
.a-run .t { flex: 1; min-width: 0; font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.a-run .t em { font-style: normal; color: var(--tx) }
.a-run .on { color: var(--acc) }
.a-run .aa { font: 600 19px/1 var(--disp) }
.a-scroll { flex: 1; overflow: auto; padding: 0 24px 40px; scrollbar-width: none }
.a-chap { text-align: center; padding: 38px 0 22px; display: grid; gap: 6px }
.a-chap .sc { color: var(--acc); font-size: 15px }
.a-chap h1 { margin: 0; font: 600 31px/1.12 var(--disp); text-wrap: balance }
.a-orn { color: var(--faint); font: 20px/1 var(--disp); letter-spacing: .5em; padding-left: .5em }
.a-txt p { font: 17.5px/1.72 var(--rd); color: var(--read); text-align: justify; hyphens: auto; text-indent: 1.5em }
.a-txt p.first { text-indent: 0 }
.a-txt p.first::first-letter { font: 600 3.1em/0.8 var(--disp); float: left; padding: 6px 6px 0 0; color: var(--acc) }
.a-edit { margin: 10px -10px; padding: 12px 10px 12px; background: var(--s2); border-top: 1px solid var(--acc); border-bottom: 1px solid var(--line); display: grid; gap: 10px }
.a-edit .lab { display: flex; justify-content: space-between; color: var(--acc); font-size: 14px }
.a-edit .field { font: 17.5px/1.7 var(--rd); color: var(--tx) }
.a-edit .row { display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: 15px; color: var(--dim); align-items: center }
.a-edit .row button { border-bottom: 1px dotted currentColor }
.a-edit .act { display: flex; justify-content: flex-end; gap: 10px; align-items: center }
.a-btn { height: 40px; padding: 0 16px; border: 1px solid var(--line); border-radius: 2px; font-size: 15px }
.a-pri { height: 40px; padding: 0 18px; background: var(--acc); color: var(--onacc); border-radius: 2px; font-weight: 700; font-size: 15px }
.a-note { font-size: 13px; color: var(--faint) }
.a-div { text-align: center; padding: 40px 0 18px; display: grid; gap: 4px }
.a-div .fl { color: var(--acc); font: 30px/1 var(--disp) }
.a-div .sc { color: var(--dim) }
.a-div h2 { margin: 0; font: 600 25px/1.2 var(--disp) }
.a-foot { height: 40px; flex: none; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; font-size: 13px; color: var(--faint); border-top: 1px solid var(--line) }
.a-foot .bar { flex: 1; height: 1px; background: var(--line); margin: 0 14px; position: relative }
.a-foot .bar i { position: absolute; left: 0; top: -1px; height: 3px; width: 34%; background: var(--acc) }
.a-dk { height: 100%; display: grid; grid-template-rows: 60px 44px 1fr; background: var(--bg) }
.a-top { display: flex; align-items: center; gap: 28px; padding: 0 28px; border-bottom: 1px solid var(--line) }
.a-brand { display: flex; align-items: baseline; gap: 10px }
.a-brand .w { font: italic 600 27px/1 var(--disp) }
.a-brand .sc { color: var(--acc); font-size: 15px }
.a-crumb { flex: 1; color: var(--dim); font-size: 15px }
.a-crumb i { font-style: normal; color: var(--faint); margin: 0 8px }
.a-crumb em { color: var(--tx); font: italic 500 19px/1 var(--disp) }
.a-topr { display: flex; align-items: center; gap: 22px; font-size: 15px }
.a-topr .st { color: var(--faint) }
.a-topr .tx { color: var(--tx); border-bottom: 1px dotted var(--dim) }
.a-bar { display: flex; align-items: center; gap: 22px; padding: 0 28px; border-bottom: 1px solid var(--line); font-size: 15px; color: var(--dim) }
.a-bar label { display: flex; align-items: center; gap: 8px; cursor: pointer }
.a-bar .bx { width: 14px; height: 14px; border: 1px solid var(--dim); display: grid; place-items: center }
.a-bar .bx.on { background: var(--acc); border-color: var(--acc) }
.a-bar .bx.on::after { content: ""; width: 6px; height: 3px; border-left: 1.6px solid var(--onacc); border-bottom: 1.6px solid var(--onacc); transform: rotate(-45deg) translate(1px, -1px) }
.a-bar .vr { width: 1px; height: 18px; background: var(--line) }
.a-bar .seg b { color: var(--tx); font-weight: 500; border-bottom: 2px solid var(--acc); padding-bottom: 2px }
.a-bar .rt { margin-left: auto; color: var(--tx) }
.a-body { display: grid; grid-template-columns: 250px minmax(0, 1fr) 300px; min-height: 0 }
.a-toc { border-right: 1px solid var(--line); padding: 22px 20px; overflow: auto; display: grid; align-content: start; gap: 2px }
.a-toc .sc { color: var(--acc); margin-bottom: 10px }
.a-toc .r { display: grid; grid-template-columns: 26px minmax(0, 1fr); gap: 2px 6px; padding: 8px 0; border-bottom: 1px dotted var(--line); font-size: 15px }
.a-toc .r .n { font: 600 18px/1.1 var(--disp); color: var(--dim) }
.a-toc .r .s { grid-column: 2; font-size: 13px; color: var(--faint) }
.a-toc .r.cur .n, .a-toc .r.cur .t { color: var(--acc) }
.a-toc .add { margin-top: 14px; color: var(--tx); font-size: 15px; border-bottom: 1px dotted var(--dim); justify-self: start }
.a-spread { overflow: auto; padding: 26px 30px 40px; background: var(--s1); scrollbar-width: thin }
.a-row { display: grid; grid-template-columns: minmax(0, 1fr) 52px minmax(0, 1fr); align-items: start }
.a-row.h { color: var(--acc); padding-bottom: 14px; font-size: 15px }
.a-row.h > :first-child { text-align: right }
.a-row .o, .a-row .u { padding: 10px 0; position: relative }
.a-row .o { font: 15.5px/1.62 var(--rd); color: var(--dim); text-align: right; hyphens: auto }
.a-row .u { font: 17px/1.62 var(--rd); color: var(--read) }
.a-row .num { align-self: stretch; display: grid; justify-items: center; align-content: start; padding-top: 12px; font: 600 17px/1 var(--disp); color: var(--faint); border-left: 1px solid var(--line); border-right: 1px solid var(--line); margin: 0 10px }
.a-row.sel .u { background: var(--s2); box-shadow: -10px 0 0 var(--s2), 10px 0 0 var(--s2) }
.a-row.sel .num { color: var(--acc) }
.a-row .selw { background: var(--selw); border-radius: 2px }
.a-pop { position: absolute; left: 70px; top: 100%; z-index: 2; margin-top: -6px; background: var(--bg); border: 1px solid var(--acc); padding: 10px 14px; display: grid; gap: 6px; font: 15px/1.3 var(--ui); color: var(--tx); width: 300px; box-shadow: 0 10px 30px rgba(0,0,0,.35) }
.a-pop .f { color: var(--dim) }
.a-pop .f i { color: var(--tx); font-style: italic }
.a-pop .l { display: flex; gap: 16px }
.a-pop .l button { border-bottom: 1px dotted var(--dim) }
.a-acts { grid-column: 3; display: flex; flex-wrap: wrap; gap: 6px 18px; padding: 74px 0 12px; font-size: 15px; color: var(--dim) }
.a-acts button { border-bottom: 1px dotted var(--dim) }
.a-row del { color: var(--del); text-decoration-thickness: 1.5px }
.a-row ins { color: var(--ins); text-decoration: underline; text-decoration-thickness: 1.5px; text-underline-offset: 3px }
.a-sug { grid-column: 3; display: flex; align-items: center; gap: 14px; padding: 0 0 12px; font-size: 15px; color: var(--dim) }
.a-sug .sc { color: var(--acc) }
.a-sug button.x { border-bottom: 1px dotted var(--dim) }
.a-row .empty { color: var(--faint); font: italic 17px/1.6 var(--rd) }
.a-row .empty button { font: 15px var(--ui); color: var(--tx); border-bottom: 1px dotted var(--dim); margin-left: 8px; font-style: normal }
.a-margin { border-left: 1px solid var(--line); padding: 22px 22px; overflow: auto; display: grid; align-content: start; gap: 16px }
.a-margin .sc { color: var(--acc) }
.a-note2 { display: grid; gap: 3px; font-size: 15px; padding-bottom: 14px; border-bottom: 1px dotted var(--line) }
.a-note2 .w { font: 600 20px/1.1 var(--disp) }
.a-note2 .o { color: var(--dim); font-style: italic }
.a-note2 .m { color: var(--faint); font-size: 14px }
.a-note2 .l { display: flex; gap: 14px; margin-top: 4px }
.a-note2 .l .a-pri { height: 32px; padding: 0 12px; font-size: 14px }
.a-note2 .l button.x { border-bottom: 1px dotted var(--dim) }
.a-cnt { display: grid; gap: 6px; font-size: 15px }
.a-cnt div { display: flex; justify-content: space-between; border-bottom: 1px dotted var(--line); padding-bottom: 6px }
.a-cnt b { font: 600 19px/1 var(--disp); color: var(--acc) }

/* ───────── Б · «Пульт»: сланцевий робочий стіл, бірюза — оригінал, бурштин — переклад; моно для чисел, щільно, рядок стану ───────── */
.k-pult {--ui:'IBM Plex Sans', 'Segoe UI', sans-serif; --mono:'IBM Plex Mono', ui-monospace, monospace; --rd:'IBM Plex Serif', Georgia, serif;}
.k-pult .mono { font-family: var(--mono) }
.k-pult .term { background: var(--term); border-radius: 2px }
.b-ph { height: 100%; display: flex; flex-direction: column; background: var(--bg) }
.b-stat { height: 34px; flex: none; display: flex; align-items: center; gap: 8px; padding: 0 16px; font: 12px/1 var(--mono); color: var(--dim) }
.b-stat .t { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.b-stat .t b { color: var(--tx); font-weight: 500 }
.b-prog { height: 2px; flex: none; background: var(--line) }
.b-prog i { display: block; height: 100%; width: 34%; background: var(--acc) }
.b-scroll { flex: 1; overflow: auto; padding: 26px 20px 30px; scrollbar-width: none }
.b-h { display: grid; gap: 6px; padding-bottom: 20px }
.b-h .k { font: 12px/1 var(--mono); color: var(--acc2) }
.b-h h1 { margin: 0; font: 600 23px/1.25 var(--rd) }
.b-txt { display: grid; gap: 14px }
.b-txt p { font: 17px/1.7 var(--rd); color: var(--read) }
.b-edit { border: 1px solid var(--acc2); background: var(--s1); display: grid }
.b-edit .lab { display: flex; justify-content: space-between; padding: 7px 10px; font: 12px/1 var(--mono); color: var(--acc2); border-bottom: 1px solid var(--line) }
.b-edit .field { padding: 10px; font: 17px/1.65 var(--rd) }
.b-edit .row { display: grid; grid-template-columns: 1fr 1fr; border-top: 1px solid var(--line) }
.b-edit .row button { height: 44px; font-size: 14px; display: flex; align-items: center; justify-content: center; gap: 6px }
.b-edit .row button + button { border-left: 1px solid var(--line) }
.b-edit .row .pri { background: var(--acc); color: var(--onacc); font-weight: 600 }
.b-edit .hint { padding: 7px 10px; font: 12px/1.3 var(--mono); color: var(--faint); border-top: 1px solid var(--line) }
.b-div { display: flex; align-items: center; gap: 10px; padding: 22px 0 6px; font: 12px/1 var(--mono); color: var(--acc2) }
.b-div::before, .b-div::after { content: ""; flex: 1; height: 1px; background: var(--line) }
.b-cmd { height: 58px; flex: none; display: grid; grid-template-columns: 52px 1fr 52px 52px 52px 52px; border-top: 1px solid var(--line); background: var(--s0) }
.b-cmd > * { display: flex; align-items: center; justify-content: center; border-right: 1px solid var(--line); color: var(--dim) }
.b-cmd > :last-child { border-right: 0 }
.b-cmd .mid { font: 13px/1 var(--mono); color: var(--tx); gap: 8px }
.b-cmd .on { color: var(--acc) }
.b-cmd .aa { font: 600 16px/1 var(--ui) }
.b-dk { height: 100%; display: grid; grid-template-columns: 54px 240px minmax(0, 1fr) 310px; grid-template-rows: 44px minmax(0, 1fr) 28px; background: var(--bg) }
.b-rail { grid-row: 1 / 3; background: var(--s0); border-right: 1px solid var(--line); display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 10px 0 }
.b-rail .logo { width: 30px; height: 30px; display: grid; place-items: center; background: var(--acc); color: var(--onacc); font: 700 17px/1 var(--mono); margin-bottom: 10px }
.b-rail button { width: 40px; height: 40px; display: grid; place-items: center; color: var(--dim); position: relative }
.b-rail button.on { color: var(--tx); background: var(--s2) }
.b-rail .dot { position: absolute; right: 6px; top: 6px; min-width: 15px; height: 15px; padding: 0 3px; background: var(--acc); color: var(--onacc); font: 600 10px/15px var(--mono); text-align: center }
.b-rail .sp { flex: 1 }
.b-exp { grid-row: 1 / 3; background: var(--s1); border-right: 1px solid var(--line); display: grid; grid-template-rows: 44px auto 1fr; min-height: 0 }
.b-exp .hd { display: flex; align-items: center; padding: 0 14px; font: 600 11px/1 var(--mono); letter-spacing: .08em; color: var(--dim); border-bottom: 1px solid var(--line) }
.b-exp .q { margin: 10px 12px; height: 30px; border: 1px solid var(--line); display: flex; align-items: center; gap: 8px; padding: 0 8px; color: var(--faint); font-size: 13px }
.b-list { overflow: auto; padding-bottom: 10px }
.b-list .grp { padding: 8px 14px 4px; font: 11px/1 var(--mono); color: var(--faint) }
.b-list .r { display: grid; grid-template-columns: 30px minmax(0, 1fr) 10px; align-items: center; gap: 4px; padding: 6px 14px; font-size: 13px }
.b-list .r .n { font: 12px/1 var(--mono); color: var(--dim) }
.b-list .r .t { white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.b-list .r.cur { background: var(--s2); color: var(--tx) }
.b-list .r.cur .n { color: var(--acc) }
.b-st { width: 8px; height: 8px; border-radius: 50%; border: 1.5px solid var(--dim) }
.b-st.pub { background: var(--acc2); border-color: var(--acc2) }
.b-st.dr { border-color: var(--acc); background: linear-gradient(90deg, var(--acc) 50%, transparent 50%) }
.b-st.q { border-style: dotted }
.b-tabs { grid-column: 3 / 5; display: flex; align-items: stretch; background: var(--s0); border-bottom: 1px solid var(--line) }
.b-tab { white-space: nowrap; display: flex; align-items: center; gap: 8px; padding: 0 14px; border-right: 1px solid var(--line); font-size: 13px; color: var(--dim) }
.b-tab.on { background: var(--bg); color: var(--tx); box-shadow: inset 0 2px 0 var(--acc) }
.b-tab .mono { font-size: 12px }
.b-tools { margin-left: auto; display: flex; align-items: center; gap: 6px; padding: 0 10px }
.b-k { height: 28px; min-width: 240px; border: 1px solid var(--line); display: flex; align-items: center; gap: 8px; padding: 0 8px; color: var(--faint); font-size: 13px; margin-right: 8px }
.b-k kbd, .k-pult kbd { font: 11px/1 var(--mono); color: var(--dim); border: 1px solid var(--line); padding: 2px 4px; margin-left: auto }
.b-tg { white-space: nowrap; height: 28px; padding: 0 9px; display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--dim); border: 1px solid transparent }
.b-tg.on { color: var(--tx); border-color: var(--line); background: var(--s1) }
.b-pub { height: 28px; padding: 0 14px; background: var(--acc); color: var(--onacc); font-weight: 600; font-size: 13px; margin-left: 4px }
.b-ed { grid-column: 3; overflow: auto; min-height: 0 }
.b-grid { display: grid; grid-template-columns: 46px minmax(0, 1fr) minmax(0, 1fr) }
.b-grid > .h { position: sticky; top: 0; z-index: 1; background: var(--bg); border-bottom: 1px solid var(--line); padding: 10px 14px; font: 11px/1 var(--mono); letter-spacing: .06em }
.b-grid > .h.o { color: var(--acc2) } .b-grid > .h.u { color: var(--acc) }
.b-grid > .c { padding: 12px 14px; border-bottom: 1px solid var(--line); position: relative }
.b-grid > .c.n { font: 12px/1.9 var(--mono); color: var(--faint); text-align: right; padding-right: 10px }
.b-grid > .c.o { font: 14.5px/1.6 var(--rd); color: var(--dim); border-left: 1px solid var(--line) }
.b-grid > .c.u { font: 15.5px/1.6 var(--rd); color: var(--read); border-left: 1px solid var(--line) }
.b-grid > .c.sel { background: var(--s2) }
.b-grid > .c.n.sel { color: var(--acc) }
.b-grid .selw { background: var(--selw) }
.b-grid del { color: var(--del); background: var(--delbg); text-decoration-thickness: 1.5px }
.b-grid ins { color: var(--ins); background: var(--insbg); text-decoration: none; box-shadow: inset 0 -1.5px 0 var(--ins) }
.b-bar { grid-column: 1 / -1; display: flex; flex-wrap: wrap; gap: 6px; padding: 8px 14px 8px 60px; background: var(--s2); border-bottom: 1px solid var(--line) }
.b-bar button { height: 28px; padding: 0 10px; border: 1px solid var(--line); background: var(--s1); display: flex; align-items: center; gap: 8px; font-size: 13px }
.b-bar .lab { font: 12px/28px var(--mono); color: var(--acc); margin-right: 6px }
.b-bar .acc { background: var(--acc); color: var(--onacc); border-color: var(--acc) }
.b-pop { position: absolute; left: 120px; top: 64px; z-index: 2; background: var(--s1); border: 1px solid var(--line); box-shadow: 0 12px 28px rgba(0,0,0,.4); width: 300px; font-size: 13px }
.b-pop .f { padding: 8px 10px; border-bottom: 1px solid var(--line); color: var(--dim) }
.b-pop .f b { color: var(--acc2); font: 500 13px var(--mono) }
.b-pop button { display: flex; width: 100%; padding: 7px 10px; gap: 8px; align-items: center }
.b-pop button:hover { background: var(--s2) }
.b-empty { color: var(--faint); font: italic 15px/1.6 var(--rd) }
.b-empty button { font: 13px var(--ui); color: var(--acc); margin-left: 8px; font-style: normal }
.b-ins { grid-column: 4; border-left: 1px solid var(--line); background: var(--s1); display: grid; grid-template-rows: 38px 1fr; min-height: 0 }
.b-ins .tb { display: flex; border-bottom: 1px solid var(--line) }
.b-ins .tb button { padding: 0 12px; font-size: 13px; color: var(--dim) }
.b-ins .tb button.on { color: var(--tx); box-shadow: inset 0 -2px 0 var(--acc) }
.b-ins .bd { overflow: auto; padding: 4px 0 }
.b-ent { padding: 10px 14px; border-bottom: 1px solid var(--line); display: grid; gap: 3px }
.b-ent .u { font-weight: 600; color: var(--acc) }
.b-ent .o { font: 12.5px/1.3 var(--mono); color: var(--acc2) }
.b-ent .m { font-size: 12.5px; color: var(--dim) }
.b-ent .chip { justify-self: start; font: 11px/1 var(--mono); padding: 3px 5px; border: 1px solid var(--acc); color: var(--acc) }
.b-ent .l { display: flex; gap: 6px; margin-top: 4px }
.b-ent .l button { height: 26px; padding: 0 9px; border: 1px solid var(--line); font-size: 12.5px }
.b-ent .l .acc { background: var(--acc); color: var(--onacc); border-color: var(--acc) }
.b-cnt { padding: 12px 14px; display: grid; gap: 7px; font-size: 13px }
.b-cnt div { display: flex; justify-content: space-between }
.b-cnt b { font: 500 13px var(--mono); color: var(--tx) }
.b-status { grid-column: 1 / -1; display: flex; align-items: center; gap: 18px; padding: 0 12px; background: var(--s0); border-top: 1px solid var(--line); font: 12px/1 var(--mono); color: var(--dim) }
.b-status .amb { color: var(--acc) }
.b-status .rt { margin-left: auto }

/* ───────── В · «Обкладинка»: сливовий вечір, рожевий і лимонний, широкий гротеск і великі числа; округлі пухкі форми ───────── */
.k-obl {--disp:'Unbounded', 'Arial Black', sans-serif; --ui:'Manrope', 'Segoe UI', sans-serif; --rd:'Lora', Georgia, serif;}
.k-obl .term { background: var(--term); border-radius: 6px; padding: 0 2px }
.c-ph { height: 100%; position: relative; background: var(--bg) }
.c-scroll { position: absolute; inset: 0; overflow: auto; padding: 88px 22px 120px; scrollbar-width: none }
.c-head { position: absolute; left: 12px; right: 12px; top: 12px; z-index: 2; height: 56px; border-radius: 28px; background: color-mix(in srgb, var(--s2) 88%, transparent); backdrop-filter: blur(10px); display: flex; align-items: center; gap: 6px; padding: 0 6px }
.c-head button { width: 44px; height: 44px; border-radius: 22px; display: grid; place-items: center }
.c-head .on { background: var(--acc); color: var(--onacc) }
.c-head .t { flex: 1; min-width: 0; display: grid; line-height: 1.2 }
.c-head .t b { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.c-head .t span { font-size: 12px; color: var(--dim) }
.c-head .aa { font: 700 15px/1 var(--disp) }
.c-hero { display: grid; gap: 8px; padding-bottom: 22px }
.c-hero .big { font: 800 80px/0.9 var(--disp); color: var(--acc); letter-spacing: -.03em }
.c-hero h1 { margin: 0; font: 700 27px/1.2 var(--rd); text-wrap: balance }
.c-chips { display: flex; gap: 6px; flex-wrap: wrap }
.c-chips span { height: 28px; padding: 0 12px; border-radius: 14px; background: var(--s2); font-size: 13px; display: flex; align-items: center; color: var(--dim) }
.c-txt { display: grid; gap: 16px }
.c-txt p { font: 18px/1.72 var(--rd); color: var(--read) }
.c-edit { background: var(--s1); border-radius: 24px; padding: 16px; box-shadow: 0 0 0 2px var(--acc); display: grid; gap: 12px; margin: 0 -8px }
.c-edit .lab { font: 700 12px/1 var(--disp); color: var(--acc); letter-spacing: .02em }
.c-edit .field { font: 18px/1.65 var(--rd) }
.c-edit .row { display: flex; gap: 8px; flex-wrap: wrap }
.c-edit .soft { height: 38px; padding: 0 14px; border-radius: 19px; background: var(--s2); font-size: 14px; font-weight: 600; display: flex; align-items: center; gap: 6px }
.c-edit .act { display: grid; grid-template-columns: 1fr 1.4fr; gap: 8px }
.c-edit .act button { height: 50px; border-radius: 25px; font-weight: 700; font-size: 15px }
.c-edit .act .gh { background: var(--s2) }
.c-edit .act .pri { background: var(--acc); color: var(--onacc) }
.c-edit .note { font-size: 13px; color: var(--faint) }
.c-next { margin: 34px -8px 18px; border-radius: 28px; background: var(--s1); padding: 20px; display: grid; grid-template-columns: auto 1fr; gap: 4px 16px; align-items: center }
.c-next .big { grid-row: 1 / 3; font: 800 54px/1 var(--disp); color: var(--acc2) }
.c-next b { font: 700 20px/1.2 var(--rd) }
.c-next span { font-size: 13px; color: var(--dim) }
.c-dock { position: absolute; left: 16px; right: 16px; bottom: 16px; height: 64px; border-radius: 32px; background: var(--s2); display: grid; grid-template-columns: 52px 1fr 52px; align-items: center; padding: 0 6px; box-shadow: 0 10px 30px rgba(0,0,0,.35) }
.c-dock button { width: 52px; height: 52px; border-radius: 26px; display: grid; place-items: center }
.c-dock .nx { background: var(--acc); color: var(--onacc) }
.c-dock .mid { display: grid; gap: 6px; padding: 0 10px }
.c-dock .mid .bar { height: 8px; border-radius: 4px; background: var(--line); overflow: hidden }
.c-dock .mid .bar i { display: block; height: 100%; width: 34%; background: var(--acc); border-radius: 4px }
.c-dock .mid span { font-size: 12px; color: var(--dim); display: flex; justify-content: space-between }
.c-dk { height: 100%; display: grid; grid-template-columns: 240px minmax(0, 1fr) 296px; gap: 14px; padding: 14px; background: var(--bg) }
.c-side { background: var(--s1); border-radius: 28px; padding: 22px 16px; display: flex; flex-direction: column; gap: 16px; min-height: 0 }
.c-logo { display: flex; align-items: center; gap: 10px; padding: 0 6px }
.c-logo .w { font: 800 21px/1 var(--disp); letter-spacing: -.02em }
.c-logo .w i { color: var(--acc); font-style: normal }
.c-logo .tag { height: 22px; padding: 0 9px; border-radius: 11px; background: var(--acc2); color: var(--onacc2); font: 700 10px/22px var(--disp); letter-spacing: .04em }
.c-nav { display: grid; gap: 4px }
.c-nav button { height: 42px; border-radius: 21px; padding: 0 14px; display: flex; align-items: center; gap: 10px; font-weight: 600; font-size: 14px; color: var(--dim) }
.c-nav button.on { background: var(--s2); color: var(--tx) }
.c-nav .n { margin-left: auto; min-width: 22px; height: 22px; border-radius: 11px; background: var(--acc); color: var(--onacc); font: 700 11px/22px var(--ui); text-align: center }
.c-book { display: grid; grid-template-columns: 64px 1fr; gap: 12px; align-items: center; padding: 10px; border-radius: 20px; background: var(--s2) }
.c-cover { width: 64px; height: 92px; border-radius: 12px; background: var(--acc); color: var(--onacc); font: 800 10px/1.15 var(--disp); padding: 8px; display: flex; align-items: flex-end }
.c-book b { font-size: 15px; display: block }
.c-book span { font-size: 12px; color: var(--dim) }
.c-chs { overflow: auto; display: grid; gap: 4px; align-content: start; min-height: 0 }
.c-chs .r { display: grid; grid-template-columns: 34px minmax(0, 1fr); gap: 0 6px; padding: 8px 10px; border-radius: 16px; align-items: center }
.c-chs .r .n { font: 700 14px/1 var(--disp); color: var(--faint) }
.c-chs .r .t { font-size: 13.5px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.c-chs .r .s { grid-column: 2; font-size: 12px; color: var(--dim) }
.c-chs .r.cur { background: var(--acc); color: var(--onacc) }
.c-chs .r.cur .n, .c-chs .r.cur .s { color: var(--onacc) }
.c-chs .add { height: 40px; border-radius: 20px; border: 1.5px dashed var(--line); font-weight: 600; font-size: 14px; color: var(--dim) }
.c-main { display: grid; grid-template-rows: auto auto minmax(0, 1fr); gap: 14px; min-height: 0 }
.c-mh { display: flex; align-items: center; gap: 18px; padding: 6px 6px 0 }
.c-mh .big { font: 800 66px/0.9 var(--disp); color: var(--acc); letter-spacing: -.03em }
.c-mh .tt { flex: 1; min-width: 0; display: grid; gap: 4px }
.c-mh .tt span { font-size: 13px; color: var(--dim) }
.c-mh h1 { margin: 0; font: 700 29px/1.15 var(--rd) }
.c-pill { height: 44px; padding: 0 18px; border-radius: 22px; background: var(--s1); font-weight: 700; font-size: 14px; display: flex; align-items: center; gap: 8px }
.c-pill.pri { background: var(--acc); color: var(--onacc); padding: 0 22px }
.c-round { width: 44px; height: 44px; border-radius: 22px; background: var(--s1); display: grid; place-items: center }
.c-tg { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 0 6px }
.c-tg .c-pill { height: 42px }
.c-seg { display: flex; background: var(--s1); border-radius: 22px; padding: 4px; gap: 2px }
.c-seg button { height: 34px; padding: 0 14px; border-radius: 17px; font-size: 13.5px; font-weight: 600; color: var(--dim); display: flex; align-items: center; gap: 6px }
.c-seg button.on { background: var(--s2); color: var(--tx) }
.c-prog { margin-left: auto; display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--dim) }
.c-prog .bar { width: 140px; height: 8px; border-radius: 4px; background: var(--s2); overflow: hidden }
.c-prog .bar i { display: block; width: 57%; height: 100%; background: var(--acc2) }
.c-cards { overflow: auto; display: grid; gap: 10px; align-content: start; padding: 2px 4px 20px; min-height: 0 }
.c-card { display: grid; grid-template-columns: 40px minmax(0, 1fr) minmax(0, 1fr); gap: 0 18px; background: var(--s1); border-radius: 22px; padding: 16px 18px; position: relative }
.c-card .n { width: 32px; height: 32px; border-radius: 16px; background: var(--s2); display: grid; place-items: center; font: 700 12px/1 var(--disp); color: var(--dim) }
.c-card .o { font: 14.5px/1.6 var(--ui); color: var(--dim) }
.c-card .u { font: 16.5px/1.6 var(--rd); color: var(--read) }
.c-card.sel { box-shadow: 0 0 0 2px var(--acc) }
.c-card.sel .n { background: var(--acc); color: var(--onacc) }
.c-card .selw { background: var(--selw); border-radius: 6px; padding: 0 2px }
.c-card .acts { grid-column: 3; display: flex; flex-wrap: wrap; gap: 6px; margin-top: 12px }
.c-card .acts button { height: 34px; padding: 0 13px; border-radius: 17px; background: var(--s2); font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 6px }
.c-card del { color: var(--del); background: var(--delbg); border-radius: 4px; text-decoration-thickness: 1.5px }
.c-card ins { color: var(--ins); background: var(--insbg); border-radius: 4px; text-decoration: none }
.c-card .flag { position: absolute; top: -9px; right: 18px; height: 22px; padding: 0 10px; border-radius: 11px; background: var(--acc2); color: var(--onacc2); font: 700 10.5px/22px var(--disp) }
.c-card .acts .acc { background: var(--acc2); color: var(--onacc2) }
.c-card.todo { background: transparent; box-shadow: inset 0 0 0 1.5px var(--line) }
.c-card .empty { color: var(--faint); font: italic 16px/1.6 var(--rd); display: flex; align-items: center; gap: 12px; flex-wrap: wrap }
.c-card .empty button { height: 34px; padding: 0 14px; border-radius: 17px; background: var(--acc); color: var(--onacc); font: 700 13px var(--ui); font-style: normal }
.c-card .u { position: relative }
.c-pop { position: absolute; left: 40px; top: calc(100% + 8px); z-index: 2; background: var(--s2); border-radius: 18px; padding: 12px; width: 290px; display: grid; gap: 8px; box-shadow: 0 14px 34px rgba(0,0,0,.4); font: 13.5px/1.4 var(--ui) }
.c-pop .f { color: var(--dim) } .c-pop .f b { color: var(--tx) }
.c-pop .l { display: flex; gap: 6px }
.c-pop .l button { height: 32px; padding: 0 12px; border-radius: 16px; background: var(--s1); font-weight: 600 }
.c-drawer { background: var(--s1); border-radius: 28px; padding: 22px 18px; display: flex; flex-direction: column; gap: 14px; min-height: 0; overflow: auto }
.c-drawer h3 { margin: 0; font: 700 15px/1.2 var(--disp) }
.c-ent { display: grid; grid-template-columns: 42px 1fr; gap: 2px 12px; align-items: center; padding: 10px; border-radius: 20px; background: var(--s2) }
.c-ent .av { grid-row: 1 / 3; width: 42px; height: 42px; border-radius: 21px; display: grid; place-items: center; font: 800 16px/1 var(--disp); background: var(--acc); color: var(--onacc) }
.c-ent .av.b { background: var(--acc2); color: var(--onacc2) }
.c-ent .av.c { background: var(--line); color: var(--tx) }
.c-ent b { font-size: 15px }
.c-ent span { font-size: 12.5px; color: var(--dim) }
.c-ent.new { box-shadow: inset 0 0 0 2px var(--acc2) }
.c-ent .l { grid-column: 1 / -1; display: flex; gap: 6px; margin-top: 8px }
.c-ent .l button { height: 34px; padding: 0 14px; border-radius: 17px; background: var(--s1); font-weight: 700; font-size: 13px }
.c-ent .l .acc { background: var(--acc2); color: var(--onacc2) }
.c-tiles { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px }
.c-tiles div { background: var(--s2); border-radius: 18px; padding: 12px 10px; display: grid; gap: 4px }
.c-tiles b { font: 800 26px/1 var(--disp); color: var(--acc) }
.c-tiles span { font-size: 12px; color: var(--dim); line-height: 1.25 }

/* ───────── Г · «Звичний»: нинішній сайт, доопрацьований — Golos і Literata, м'які картки, легший низ читалки ───────── */
.k-zv {--ui:'Golos Text', -apple-system, 'Segoe UI', sans-serif; --rd:'Literata', Georgia, serif;}
.k-zv .term { background: var(--term); border-radius: 4px; padding: 0 1px }
.g-ph { height: 100%; display: flex; flex-direction: column; background: var(--bg) }
.g-top { height: 58px; flex: none; display: flex; align-items: center; gap: 4px; padding: 0 8px; border-bottom: 1px solid var(--line) }
.g-top button { width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; color: var(--dim) }
.g-top .on { color: var(--acc); background: var(--s2) }
.g-top .aa { font: 600 17px/1 var(--rd); color: var(--tx) }
.g-top .t { flex: 1; min-width: 0; display: grid; line-height: 1.25; padding-left: 2px }
.g-top .t b { font-size: 15px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.g-top .t span { font-size: 12.5px; color: var(--dim) }
.g-scroll { flex: 1; overflow: auto; padding: 22px 20px 30px; scrollbar-width: none }
.g-h { display: grid; gap: 6px; padding-bottom: 18px }
.g-h span { font-size: 13px; color: var(--dim) }
.g-h h1 { margin: 0; font: 700 25px/1.25 var(--rd); text-wrap: balance }
.g-txt { display: grid; gap: 14px }
.g-txt p { font: 17.5px/1.7 var(--rd); color: var(--read); text-align: justify; hyphens: auto }
.g-edit { background: var(--s1); border: 1px solid var(--acc); border-radius: 14px; padding: 14px; display: grid; gap: 12px; margin: 0 -6px }
.g-edit .lab { display: flex; justify-content: space-between; font-size: 13px; color: var(--acc); font-weight: 600 }
.g-edit .lab span:last-child { color: var(--faint); font-weight: 400 }
.g-edit .field { font: 17.5px/1.65 var(--rd) }
.g-edit .row { display: flex; gap: 8px; flex-wrap: wrap }
.g-edit .row button { height: 36px; padding: 0 12px; border-radius: 10px; background: var(--s2); font-size: 14px; display: flex; align-items: center; gap: 6px }
.g-edit .act { display: grid; grid-template-columns: 1fr 1fr; gap: 8px }
.g-edit .act button { height: 46px; border-radius: 12px; font-weight: 600; font-size: 15px; background: var(--s2) }
.g-edit .act .pri { background: var(--acc); color: var(--onacc) }
.g-edit .note { font-size: 13px; color: var(--faint) }
.g-div { display: grid; justify-items: center; gap: 6px; padding: 30px 0 14px }
.g-div span { height: 26px; padding: 0 12px; border-radius: 13px; background: var(--s2); font-size: 13px; color: var(--dim); display: flex; align-items: center }
.g-div h2 { margin: 0; font: 700 21px/1.25 var(--rd) }
.g-foot { flex: none; border-top: 1px solid var(--line); background: var(--bg) }
.g-foot .bar { height: 3px; background: var(--s2) }
.g-foot .bar i { display: block; height: 100%; width: 34%; background: var(--acc) }
.g-foot .row { height: 54px; display: grid; grid-template-columns: 54px 1fr 54px 54px; align-items: center }
.g-foot button { width: 54px; height: 54px; display: grid; place-items: center; color: var(--dim) }
.g-foot .mid { text-align: center; font-size: 13px; color: var(--dim) }
.g-foot .mid b { color: var(--tx); font-weight: 600 }
.g-dk { height: 100%; display: grid; grid-template-rows: 60px 56px minmax(0, 1fr); background: var(--bg) }
.g-top2 { display: flex; align-items: center; gap: 18px; padding: 0 20px; border-bottom: 1px solid var(--line) }
.g-logo { font: 700 22px/1 var(--rd); letter-spacing: -.01em }
.g-logo i { color: var(--acc); font-style: normal }
.g-chip { height: 24px; padding: 0 9px; border-radius: 8px; background: var(--s2); color: var(--acc); font-size: 12px; font-weight: 700; letter-spacing: .06em; display: flex; align-items: center }
.g-crumb { flex: 1; min-width: 0; font-size: 14px; color: var(--dim); white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.g-crumb b { color: var(--tx); font-weight: 600 }
.g-st { font-size: 13px; color: var(--faint) }
.g-b { height: 38px; padding: 0 14px; border-radius: 10px; background: var(--s2); font-size: 14px; font-weight: 500; display: flex; align-items: center; gap: 8px; white-space: nowrap }
.g-b.pri { background: var(--acc); color: var(--onacc); font-weight: 600 }
.g-b.ic { width: 38px; padding: 0; justify-content: center }
.g-tools { display: flex; align-items: center; gap: 12px; padding: 0 20px }
.g-seg { display: flex; gap: 2px; padding: 3px; border-radius: 12px; background: var(--s1); border: 1px solid var(--line) }
.g-seg button { height: 32px; padding: 0 12px; border-radius: 9px; font-size: 13.5px; color: var(--dim); display: flex; align-items: center; gap: 6px }
.g-seg button.on { background: var(--s2); color: var(--tx); font-weight: 500 }
.g-prog { margin-left: auto; display: flex; align-items: center; gap: 10px; font-size: 13.5px; color: var(--dim) }
.g-prog .bar { width: 160px; height: 6px; border-radius: 3px; background: var(--s2); overflow: hidden }
.g-prog .bar i { display: block; width: 57%; height: 100%; background: var(--acc) }
.g-body { display: grid; grid-template-columns: 252px minmax(0, 1fr) 306px; gap: 14px; padding: 0 16px 16px; min-height: 0 }
.g-card { background: var(--s1); border: 1px solid var(--line); border-radius: 14px; min-height: 0; overflow: auto }
.g-card .hd { position: sticky; top: 0; background: var(--s1); padding: 14px 14px 10px; font-size: 13px; font-weight: 600; color: var(--dim); display: flex; justify-content: space-between; z-index: 1 }
.g-chs { padding: 0 8px 10px; display: grid; gap: 2px }
.g-chs .r { display: grid; grid-template-columns: 30px minmax(0, 1fr); gap: 1px 4px; padding: 8px; border-radius: 10px }
.g-chs .r .n { font-size: 13px; color: var(--faint); font-variant-numeric: tabular-nums }
.g-chs .r .t { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis }
.g-chs .r .s { grid-column: 2; font-size: 12px; color: var(--faint) }
.g-chs .r.cur { background: var(--s2) }
.g-chs .r.cur .t { color: var(--acc); font-weight: 600 }
.g-chs .add { margin: 6px 8px 0; height: 36px; border-radius: 10px; border: 1px dashed var(--line); font-size: 13.5px; color: var(--dim) }
.g-grid { display: grid; grid-template-columns: 40px minmax(0, 1fr) minmax(0, 1fr); padding: 0 8px 12px }
.g-grid > .h { position: sticky; top: 0; z-index: 1; background: var(--s1); padding: 14px 10px 10px; font-size: 12.5px; font-weight: 600; color: var(--dim); border-bottom: 1px solid var(--line) }
.g-grid > .c { padding: 12px 10px; border-bottom: 1px solid var(--line); position: relative }
.g-grid > .c.n { font-size: 12.5px; color: var(--faint); text-align: right; padding-top: 15px; font-variant-numeric: tabular-nums }
.g-grid > .c.o { font: 14.5px/1.6 var(--rd); color: var(--dim) }
.g-grid > .c.u { font: 16px/1.6 var(--rd); color: var(--read) }
.g-grid > .c.sel { background: var(--s2) }
.g-grid > .c.n.sel { color: var(--acc); border-radius: 10px 0 0 10px }
.g-grid > .c.u.sel { border-radius: 0 10px 10px 0 }
.g-grid .selw { background: var(--selw); border-radius: 4px }
.g-grid del { color: var(--del); background: var(--delbg); border-radius: 3px; text-decoration-thickness: 1.5px }
.g-grid ins { color: var(--ins); background: var(--insbg); border-radius: 3px; text-decoration: none }
.g-acts { grid-column: 2 / 4; display: flex; flex-wrap: wrap; gap: 6px; padding: 10px 10px 12px; border-bottom: 1px solid var(--line) }
.g-acts button { height: 32px; padding: 0 11px; border-radius: 9px; background: var(--s2); font-size: 13px; display: flex; align-items: center; gap: 6px }
.g-acts .acc { background: var(--acc); color: var(--onacc); font-weight: 600 }
.g-acts .tag { height: 32px; display: flex; align-items: center; font-size: 12.5px; font-weight: 600; color: var(--acc2); margin-right: 4px }
.g-pop { position: absolute; left: 60px; top: calc(100% - 6px); z-index: 2; width: 300px; background: var(--s2); border: 1px solid var(--line); border-radius: 12px; padding: 10px; display: grid; gap: 8px; font: 13.5px/1.4 var(--ui); box-shadow: 0 12px 30px rgba(0,0,0,.35) }
.g-pop .f { color: var(--dim) } .g-pop .f b { color: var(--tx); font-weight: 600 }
.g-pop .l { display: flex; gap: 6px }
.g-pop .l button { height: 30px; padding: 0 10px; border-radius: 8px; background: var(--s1) }
.g-empty { color: var(--faint); font: italic 15px/1.6 var(--rd) }
.g-empty button { font: 600 13px var(--ui); color: var(--acc); margin-left: 8px; font-style: normal }
.g-side { padding: 0 12px 12px; display: grid; gap: 8px }
.g-ent { padding: 10px 12px; border-radius: 12px; background: var(--s2); display: grid; gap: 2px }
.g-ent b { font-weight: 600 }
.g-ent .o { font-size: 13px; color: var(--dim) }
.g-ent .m { font-size: 12.5px; color: var(--faint) }
.g-ent.new { box-shadow: inset 0 0 0 1px var(--acc2) }
.g-ent .tag { justify-self: start; font-size: 11.5px; font-weight: 700; color: var(--acc2) }
.g-ent .l { display: flex; gap: 6px; margin-top: 6px }
.g-ent .l button { height: 30px; padding: 0 11px; border-radius: 8px; background: var(--s1); font-size: 13px }
.g-ent .l .acc { background: var(--acc); color: var(--onacc); font-weight: 600 }
.g-cnt { display: grid; gap: 6px; font-size: 13.5px; padding: 4px 4px 0 }
.g-cnt div { display: flex; justify-content: space-between; color: var(--dim) }
.g-cnt b { color: var(--tx); font-weight: 600; font-variant-numeric: tabular-nums }

`;
export const WS_PAL = () => `
<div class="a-dk">
  <header class="a-top">
    <div class="a-brand"><span class="w">Новелка</span><span class="sc">студія</span></div>
    <nav class="a-crumb">Доглядач маяка<i>/</i>Глава 12<i>/</i><em>Лінза, що пам’ятає світло</em></nav>
    <div class="a-topr"><span class="st">чернетка · збережено щойно</span><button class="tx">Історія · 5</button><button class="tx">Вичитати главу з ШІ</button><button class="a-pri">Опублікувати</button>${themeBtn('')}</div>
  </header>
  <div class="a-bar">
    <label><span class="bx on"></span>Глави</label><label><span class="bx on"></span>Оригінал</label><label><span class="bx on"></span>Словник</label>
    <span class="vr"></span><span class="seg"><b>Поруч</b></span><span class="seg">Як у книжці</span>
    <span class="rt">Перевірено 4 з 7</span>
  </div>
  <div class="a-body">
    <aside class="a-toc"><div class="sc">Зміст · 40 глав</div>
      ${CHAPS.map(c => `<div class="r ${c.cur ? 'cur' : ''}"><span class="n">${c.n}</span><span class="t">${c.t || '<span style="color:var(--faint)">без назви</span>'}</span><span class="s">${c.s}</span></div>`).join('')}
      <button class="add">+ Нова глава</button>
    </aside>
    <main class="a-spread">
      <div class="a-row h"><span class="sc">Оригінал · англійська</span><span></span><span class="sc">Переклад</span></div>
      ${ROWS.map((r, i) => `
      <div class="a-row ${r.sel ? 'sel' : ''}">
        <div class="o">${fmt(r.en)}</div><div class="num">${i + 1}</div>
        <div class="u">${r.uk ? fmt(r.uk) : '<span class="empty">Ще не перекладено<button>Перекласти абзац</button></span>'}
          ${r.sel ? `<div class="a-pop"><span class="f">з оригіналу: <i>the great lens</i></span><span class="l"><button>До словника</button><button>Замінити в главі</button></span></div>` : ''}
        </div>
        ${r.sel ? `<div class="a-acts"><button>Інший варіант</button><button>Вичитати абзац</button><button>Повернути машинний</button><button>Історія абзацу</button></div>` : ''}
        ${r.sug ? `<div class="a-sug"><span class="sc">вичитка ШІ</span><button class="a-pri" style="height:30px;padding:0 12px;font-size:14px">Прийняти</button><button class="x">Лишити як є</button></div>` : ''}
      </div>`).join('')}
    </main>
    <aside class="a-margin">
      <div class="sc">На полях · словник глави</div>
      <div class="a-note2"><span class="w">Ліна</span><span class="o">Lina</span><span class="m">персонаж · жін. · учениця доглядача</span></div>
      <div class="a-note2"><span class="w">майстер Орест</span><span class="o">Master Orest</span><span class="m">персонаж · чол. · доглядач маяка</span></div>
      <div class="a-note2"><span class="w">велика лінза</span><span class="o">the great lens</span><span class="m">нове · термін · з цієї глави</span><span class="l"><button class="a-pri">Погодити</button><button class="x">Змінити</button></span></div>
      <div class="sc">У цій главі</div>
      <div class="a-cnt"><div><span>Правки читачів</span><b>3</b></div><div><span>Пропозиції вичитки ШІ</span><b>1</b></div><div><span>Неперекладені абзаци</span><b>1</b></div></div>
    </aside>
  </div>
</div>`;
export const WS_PULT = () => `
<div class="b-dk">
  <nav class="b-rail"><span class="logo">н</span>
    <button class="on" aria-label="Мої переклади">${ic('layers', 20)}</button>
    <button aria-label="Хід перекладу">${ic('pulse', 20)}<span class="dot">1</span></button>
    <button aria-label="Словник">${ic('book', 20)}</button>
    <button aria-label="Правки">${ic('pen', 20)}<span class="dot">3</span></button>
    <button aria-label="Команди">${ic('users', 20)}</button>
    <span class="sp"></span>
    <button aria-label="Сповіщення">${ic('bell', 20)}</button>
    <button aria-label="Повідомлення">${ic('chat', 20)}</button>
    ${themeBtn('')}
  </nav>
  <aside class="b-exp">
    <div class="hd">ДОГЛЯДАЧ МАЯКА</div>
    <div class="q">${ic('search', 15)} Глава чи назва</div>
    <div class="b-list"><div class="grp">ТОМ 1 · 40 глав · 28 опубл.</div>
      ${CHAPS.map(c => `<div class="r ${c.cur ? 'cur' : ''}"><span class="n">${String(c.n).padStart(3, '0')}</span><span class="t">${c.t || '<span style="color:var(--faint)">немає оригіналу</span>'}</span><span class="b-st ${c.k}"></span></div>`).join('')}
      <div class="r" style="color:var(--acc)"><span class="n">+</span><span class="t">Нова глава</span></div>
    </div>
  </aside>
  <div class="b-tabs">
    <div class="b-tab on"><span class="mono">12</span> Лінза, що пам’ятає світло <span style="color:var(--acc)">●</span></div>
    <div class="b-tools">
      <button class="b-tg" aria-label="Знайти чи виконати">${ic('search', 15)}<kbd style="margin:0">⌘K</kbd></button>
      <button class="b-tg on">${ic('cols', 15)} Глави</button><button class="b-tg on">Оригінал</button><button class="b-tg on">Словник</button><button class="b-tg">Як у книжці</button>
      <button class="b-tg">${ic('clock', 15)} Історія · 5</button><button class="b-tg">${ic('spark', 15)} Вичитати з ШІ</button>
      <button class="b-pub">Опублікувати</button>
    </div>
  </div>
  <main class="b-ed"><div class="b-grid">
    <div class="h">¶</div><div class="h o">EN · ОРИГІНАЛ</div><div class="h u">UK · ПЕРЕКЛАД</div>
    ${ROWS.map((r, i) => `
      <div class="c n ${r.sel ? 'sel' : ''}">${i + 1}</div>
      <div class="c o ${r.sel ? 'sel' : ''}">${fmt(r.en)}</div>
      <div class="c u ${r.sel ? 'sel' : ''}">${r.uk ? fmt(r.uk) : '<span class="b-empty">не перекладено<button>Перекласти абзац</button></span>'}
</div>
      ${r.sel ? `<div class="b-bar"><span class="lab">¶2</span><button>${ic('spark', 15)} Інший варіант<kbd>⌥1</kbd></button><button>Вичитати абзац<kbd>⌥2</kbd></button><button>${ic('undo', 15)} Повернути машинний</button><button>${ic('clock', 15)} Історія абзацу</button><span class="lab" style="margin-left:14px">«велику лінзу» ← <span style="color:var(--acc2)">the great lens</span></span><button>${ic('book', 15)} До словника<kbd>D</kbd></button><button>${ic('search', 15)} Замінити в главі<kbd>R</kbd></button></div>` : ''}
      ${r.sug ? `<div class="b-bar"><span class="lab">ВИЧИТКА ШІ</span><button class="acc">${ic('check', 15)} Прийняти<kbd style="border-color:currentColor;color:inherit">⌘↵</kbd></button><button>Лишити як є<kbd>Esc</kbd></button></div>` : ''}
    `).join('')}
  </div></main>
  <aside class="b-ins">
    <div class="tb"><button class="on">Словник глави</button><button>Вичитка · 1</button><button>Історія</button></div>
    <div class="bd">
      <div class="b-ent"><span class="u">Ліна</span><span class="o">Lina</span><span class="m">персонаж · жін. · учениця доглядача</span></div>
      <div class="b-ent"><span class="u">майстер Орест</span><span class="o">Master Orest</span><span class="m">персонаж · чол. · доглядач маяка</span></div>
      <div class="b-ent"><span class="chip">НОВЕ</span><span class="u">велика лінза</span><span class="o">the great lens</span><span class="m">термін · з цієї глави</span><span class="l"><button class="acc">Погодити</button><button>Змінити</button></span></div>
      <div class="b-cnt"><div><span>Правки читачів</span><b>3</b></div><div><span>Пропозиції вичитки ШІ</span><b>1</b></div><div><span>Неперекладені абзаци</span><b>1</b></div><div><span>Перевірено</span><b>4/7</b></div></div>
    </div>
  </aside>
  <footer class="b-status"><span class="amb">● чернетка</span><span>збережено 12:04</span><span>¶ 7 · перевірено 4/7</span><span>правки 3 · вичитка 1</span><span class="rt">Esc скасувати · ⌘↵ зберегти · ⌘K команди</span></footer>
</div>`;
export const WS_OBL = () => `
<div class="c-dk">
  <aside class="c-side">
    <div class="c-logo"><span class="w">новелка<i>.</i></span><span class="tag">СТУДІЯ</span></div>
    <nav class="c-nav">
      <button class="on">${ic('layers', 18)} Мої переклади</button>
      <button>${ic('pulse', 18)} Хід перекладу<span class="n">1</span></button>
      <button>${ic('users', 18)} Команди</button>
      <button>${ic('trash', 18)} Кошик</button>
    </nav>
    <div class="c-book"><div class="c-cover">Доглядач маяка</div><div><b>Доглядач маяка</b><span>40 глав · 28 опубліковано</span></div></div>
    <div class="c-chs">
      ${CHAPS.map(c => `<div class="r ${c.cur ? 'cur' : ''}"><span class="n">${c.n}</span><span class="t">${c.t || 'Без назви'}</span><span class="s">${c.s}</span></div>`).join('')}
      <button class="add">+ Нова глава</button>
    </div>
  </aside>
  <main class="c-main">
    <div class="c-mh"><span class="big">12</span><div class="tt"><span>Доглядач маяка · чернетка · збережено щойно</span><h1>Лінза, що пам’ятає світло</h1></div>
      <button class="c-pill pri">Опублікувати</button>${themeBtn('c-round')}</div>
    <div class="c-tg">
      <div class="c-seg"><button class="on">${ic('check', 15)} Глави</button><button class="on">${ic('check', 15)} Оригінал</button><button class="on">${ic('check', 15)} Словник</button></div>
      <div class="c-seg"><button class="on">Поруч</button><button>Як у книжці</button></div>
      <button class="c-pill">${ic('clock', 17)} Історія · 5</button><button class="c-pill">${ic('spark', 17)} Вичитати з ШІ</button>
    </div>
    <div class="c-cards">
      ${ROWS.map((r, i) => r.uk ? `
      <div class="c-card ${r.sel ? 'sel' : ''}">
        ${r.sug ? '<span class="flag">ВИЧИТКА ШІ</span>' : ''}
        <span class="n">${i + 1}</span><div class="o">${fmt(r.en)}</div><div class="u">${fmt(r.uk)}${r.sel ? `<div class="c-pop"><span class="f">з оригіналу: <b>the great lens</b></span><span class="l"><button>До словника</button><button>Замінити в главі</button></span></div>` : ''}</div>
        ${r.sel ? `<div class="acts" style="margin-top:84px"><button>${ic('spark', 15)} Інший варіант</button><button>Вичитати абзац</button><button>${ic('undo', 15)} Повернути машинний</button><button>${ic('clock', 15)} Історія</button></div>` : ''}
        ${r.sug ? `<div class="acts"><button class="acc">${ic('check', 15)} Прийняти</button><button>Лишити як є</button></div>` : ''}
      </div>` : `
      <div class="c-card todo"><span class="n">${i + 1}</span><div class="o">${fmt(r.en)}</div><div class="empty">Ще не перекладено <button>${ic('spark', 14)} Перекласти абзац</button></div></div>`).join('')}
    </div>
  </main>
  <aside class="c-drawer">
    <h3>Словник глави</h3>
    <div class="c-ent"><span class="av">Л</span><b>Ліна</b><span>Lina · персонаж · жін.</span></div>
    <div class="c-ent"><span class="av b">О</span><b>майстер Орест</b><span>Master Orest · персонаж · чол.</span></div>
    <div class="c-ent new"><span class="av c">✦</span><b>велика лінза</b><span>the great lens · нове · термін</span><div class="l"><button class="acc">Погодити</button><button>Змінити</button></div></div>
    <h3 style="margin-top:6px">У цій главі</h3>
    <div class="c-prog" style="margin:0">Перевірено 4 з 7<span class="bar"><i></i></span></div>
    <div class="c-tiles"><div><b>3</b><span>правки читачів</span></div><div><b>1</b><span>вичитка ШІ</span></div><div><b>1</b><span>не перекладено</span></div></div>
  </aside>
</div>`;
export const WS_ZV = () => `
<div class="g-dk">
  <header class="g-top2"><span class="g-logo">новелка<i>.</i></span><span class="g-chip">СТУДІЯ</span>
    <span class="g-crumb">Мої переклади / Доглядач маяка / <b>Глава 12 · Лінза, що пам’ятає світло</b></span>
    <span class="g-st">чернетка · збережено щойно</span>
    <button class="g-b">${ic('clock', 16)} Історія · 5</button><button class="g-b">${ic('spark', 16)} Вичитати главу з ШІ</button><button class="g-b pri">Опублікувати</button>${themeBtn('g-b ic')}
  </header>
  <div class="g-tools">
    <div class="g-seg"><button class="on">${ic('check', 14)} Глави</button><button class="on">${ic('check', 14)} Оригінал</button><button class="on">${ic('check', 14)} Словник</button></div>
    <div class="g-seg"><button class="on">Поруч</button><button>Як у книжці</button></div>
    <div class="g-prog">Перевірено 4 з 7<span class="bar"><i></i></span></div>
  </div>
  <div class="g-body">
    <aside class="g-card"><div class="hd"><span>Глави</span><span>40</span></div><div class="g-chs">
      ${CHAPS.map(c => `<div class="r ${c.cur ? 'cur' : ''}"><span class="n">${c.n}</span><span class="t">${c.t || '<span style="color:var(--faint)">без назви</span>'}</span><span class="s">${c.s}</span></div>`).join('')}
      <button class="add">+ Нова глава</button></div></aside>
    <main class="g-card"><div class="g-grid">
      <div class="h">№</div><div class="h">Оригінал · англійська</div><div class="h">Переклад</div>
      ${ROWS.map((r, i) => `
        <div class="c n ${r.sel ? 'sel' : ''}">${i + 1}</div>
        <div class="c o ${r.sel ? 'sel' : ''}">${fmt(r.en)}</div>
        <div class="c u ${r.sel ? 'sel' : ''}">${r.uk ? fmt(r.uk) : '<span class="g-empty">Ще не перекладено<button>Перекласти абзац</button></span>'}
          ${r.sel ? `<div class="g-pop"><span class="f">з оригіналу: <b>the great lens</b></span><span class="l"><button>До словника</button><button>Замінити в главі</button></span></div>` : ''}</div>
        ${r.sel ? `<div class="g-acts" style="padding-top:70px"><button>${ic('spark', 15)} Інший варіант</button><button>Вичитати абзац</button><button>${ic('undo', 15)} Повернути машинний</button><button>${ic('clock', 15)} Історія абзацу</button></div>` : ''}
        ${r.sug ? `<div class="g-acts"><span class="tag">Вичитка ШІ пропонує</span><button class="acc">${ic('check', 15)} Прийняти</button><button>Лишити як є</button></div>` : ''}
      `).join('')}
    </div></main>
    <aside class="g-card"><div class="hd"><span>Словник у главі</span><span>Весь словник</span></div><div class="g-side">
      <div class="g-ent"><b>Ліна</b><span class="o">Lina</span><span class="m">персонаж · жін. · учениця доглядача</span></div>
      <div class="g-ent"><b>майстер Орест</b><span class="o">Master Orest</span><span class="m">персонаж · чол. · доглядач маяка</span></div>
      <div class="g-ent new"><span class="tag">нове</span><b>велика лінза</b><span class="o">the great lens</span><span class="m">термін · з цієї глави</span><span class="l"><button class="acc">Погодити</button><button>Змінити</button></span></div>
      <div class="hd" style="position:static;padding:10px 4px 0">У цій главі</div>
      <div class="g-cnt"><div><span>Правки читачів</span><b>3</b></div><div><span>Пропозиції вичитки ШІ</span><b>1</b></div><div><span>Неперекладені абзаци</span><b>1</b></div></div>
    </div></aside>
  </div>
</div>`;
