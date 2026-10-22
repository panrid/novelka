// Читалка: телефон (ReaderT), планшет (ReaderP), комп'ютер (ReaderK) і панелі читалки (ReaderPanelsT, ReaderPanelsK).
import { ic, fmt, kk, iff } from './lib.mjs';
import { CH12, EDITED, CH13, WHO, COMMENTS, CHAPS } from './content.mjs';

export const READER_CSS = `
/* ── текст глави ── */
.rs{flex:1;min-height:0;overflow:auto;scrollbar-width:none;position:relative}
.rs::-webkit-scrollbar{display:none}
.rt{display:flex;flex-direction:column;gap:14px}
.rt p{font:17px/1.7 var(--f-rd);color:var(--read);hyphens:auto;position:relative}
.k-zv .rt p{font-size:17.5px;text-align:justify}
.k-pal .rt{gap:0}
.k-pal .rt p{font-size:17.5px;line-height:1.72;text-align:justify;text-indent:1.5em}
.k-pal .rt p.first{text-indent:0}
.k-pal .rt p.first::first-letter{font:600 3.1em/.8 var(--f-h);float:left;padding:6px 6px 0 0;color:var(--acc)}
.k-obl .rt{gap:16px}
.k-obl .rt p{font-size:18px;line-height:1.72}
.ph .rs{padding:24px 20px 32px}
.k-pal.ph .rs{padding:0 24px 40px}
.k-obl.ph .rs{padding:92px 22px 120px}
.mine{display:block;font:500 12px/1.3 var(--f-ui);color:var(--acc);margin-bottom:2px;text-indent:0}
.mine-p{box-shadow:-10px 0 0 var(--bg),-12px 0 0 var(--acc)}
.k-pal .mine{font-variant:all-small-caps;letter-spacing:.08em;font-size:14px}
.k-pult .mine{font:500 11px/1.3 var(--f-num)}
/* заголовок глави */
.chh{display:flex;flex-direction:column;gap:6px;padding-bottom:20px}
.chh-n,.chh-m{display:none}
.chh-k{font-size:13px;color:var(--dim)}
.chh-t{font:var(--wh) 25px/1.22 var(--f-h);color:var(--strong);text-wrap:balance}
.k-pult .chh-k{font:500 12px/1 var(--f-num);color:var(--acc2);letter-spacing:.06em;text-transform:uppercase}
.k-pult .chh-t{font:600 23px/1.25 var(--f-rd)}
.k-pal .chh{align-items:center;text-align:center;padding:38px 0 22px}
.k-pal .chh-k{font-variant:all-small-caps;letter-spacing:.1em;color:var(--acc);font-size:15px}
.k-pal .chh-t{font-size:31px;line-height:1.12}
.k-pal .chh::after{content:"· · ·";color:var(--faint);letter-spacing:.5em;padding-left:.5em;font:20px/1 var(--f-h);margin-top:4px}
.k-obl .chh-n{display:block;font:800 80px/.9 var(--f-num);color:var(--acc);letter-spacing:-.03em}
.k-obl .chh-t{font:700 27px/1.2 var(--f-rd)}
.chh-meta{display:flex;gap:6px;flex-wrap:wrap}
.k-pal .chh-meta{order:-1}
.k-obl .chh-k,.k-obl .chh-m{display:inline-flex;height:28px;padding:0 12px;border-radius:14px;background:var(--s2);align-items:center;font-size:13px;color:var(--dim)}
/* правка на місці */
.ed{display:flex;flex-direction:column;gap:12px;padding:14px;background:var(--s1);border-radius:var(--rcard);box-shadow:inset 0 0 0 1px var(--acc);margin:0 -8px}
.ed-l{display:flex;justify-content:space-between;gap:8px;font-size:13px;font-weight:600;color:var(--acc)}
.ed-l span:last-child{font-weight:400;color:var(--faint)}
.ed-f{font:17px/1.65 var(--f-rd);color:var(--strong)}
.ed-t{display:flex;gap:8px;flex-wrap:wrap}
.ed-t button{display:inline-flex;align-items:center;gap:6px;height:var(--hbs);padding:0 12px;border-radius:var(--rb);background:var(--s2);font-size:14px}
.ed-a{display:grid;grid-template-columns:1fr 1fr;gap:8px}
.ed-a .btn{width:100%}
.rt .ed-n,.ed-n{font:12.5px/1.4 var(--f-ui);color:var(--faint);text-indent:0;text-align:left}
.ed-k{display:none;font:12px/1 var(--f-num);color:var(--faint)}
.k-pult .ed{padding:0;gap:0;margin:0;box-shadow:inset 0 0 0 1px var(--acc2)}
.k-pult .ed-l{padding:8px 10px;font:500 12px/1 var(--f-num);color:var(--acc2);border-bottom:1px solid var(--line)}
.k-pult .ed-f{padding:10px}
.k-pult .ed-t{display:grid;grid-template-columns:1fr 1fr;gap:0;border-top:1px solid var(--line)}
.k-pult .ed-t button{height:44px;justify-content:center;border-radius:0;background:none}
.k-pult .ed-t button+button,.k-pult .ed-a .btn+.btn{box-shadow:inset 1px 0 0 var(--line)}
.k-pult .ed-a{gap:0;border-top:1px solid var(--line)}
.k-pult .ed-a .btn{box-shadow:none;height:44px}
.k-pult .rt .ed-n,.k-pult .ed-n{padding:8px 10px;border-top:1px solid var(--line);font:11.5px/1.3 var(--f-num)}
.k-pal .ed{margin:12px -10px;padding:12px 10px;border-radius:0;background:var(--s2);box-shadow:none;border-top:1px solid var(--acc);border-bottom:1px solid var(--line)}
.k-pal .ed-l{font-variant:all-small-caps;letter-spacing:.08em;font-size:15px;font-weight:500}
.k-pal .ed-f{font-size:17.5px;text-indent:0}
.k-pal .ed-t button{background:none;padding:0;height:auto;border-radius:0;border-bottom:1px dotted var(--dim);color:var(--dim);font-size:15px}
.k-pal .ed-t button svg{display:none}
.k-pal .ed-a{display:flex;justify-content:flex-end}
.k-pal .ed-a .btn{width:auto}
.k-obl .ed{border-radius:24px;padding:16px;box-shadow:0 0 0 2px var(--acc);gap:14px}
.k-obl .ed-l{font:700 11.5px/1 var(--f-h);letter-spacing:.03em;text-transform:uppercase}
.k-obl .ed-f{font-size:18px}
.k-obl .ed-t button{height:38px;border-radius:19px;font-weight:600}
.k-obl .ed-a{grid-template-columns:1fr 1.4fr}
.k-obl .ed-a .btn{height:50px}
/* панель виділення */
.selbar{display:flex;gap:2px;padding:4px;background:var(--s1);border-radius:var(--rcard);box-shadow:0 10px 30px rgba(0,0,0,.35),inset 0 0 0 1px var(--line);align-self:flex-start;margin:-6px 0 2px;z-index:3;text-indent:0}
.selbar button{display:flex;flex-direction:column;align-items:center;gap:3px;padding:7px 9px;border-radius:var(--rb);font-size:12px;color:var(--tx);min-width:62px}
.selbar button:first-child{color:var(--acc)}
.k-pult .selbar{padding:0;gap:0}
.k-pult .selbar button{border-radius:0;box-shadow:inset -1px 0 0 var(--line)}
.k-pal .selbar{background:var(--bg);box-shadow:0 10px 30px rgba(0,0,0,.35),inset 0 0 0 1px var(--acc);margin:6px 0 8px}
.k-pal .selbar button{font-variant:all-small-caps;letter-spacing:.06em;font-size:14px}
.k-obl .selbar{border-radius:22px;padding:6px;background:var(--s2)}
.k-obl .selbar button{border-radius:16px;font-weight:600}
.k-obl .selbar button:first-child{background:var(--acc);color:var(--onacc)}
/* роздільник глав (безшовно) */
.chd-p{display:flex;align-items:center;gap:10px;padding:22px 0 8px;font:500 12px/1 var(--f-num);color:var(--acc2)}
.chd-p::before,.chd-p::after{content:"";flex:1;height:1px;background:var(--line)}
.chd-z{display:flex;flex-direction:column;align-items:center;gap:6px;padding:30px 0 12px}
.chd-z span{height:26px;padding:0 12px;border-radius:13px;background:var(--s2);font-size:13px;color:var(--dim);display:flex;align-items:center}
.chd-z h2{font:700 21px/1.25 var(--f-h);color:var(--strong)}
.chd-a{display:flex;flex-direction:column;align-items:center;gap:4px;padding:40px 0 18px;text-align:center}
.chd-a .fl{color:var(--acc);font:30px/1 var(--f-h)}
.chd-a .k{font-variant:all-small-caps;letter-spacing:.1em;color:var(--dim);font-size:15px}
.chd-a h2{font:600 25px/1.2 var(--f-h);color:var(--strong)}
.chd-o{margin:30px -8px 14px;border-radius:28px;background:var(--s1);padding:20px;display:grid;grid-template-columns:auto 1fr;gap:4px 16px;align-items:center}
.chd-o .n{grid-row:1/3;font:800 54px/1 var(--f-num);color:var(--acc2)}
.chd-o b{font:700 20px/1.2 var(--f-rd);color:var(--strong)}
.chd-o span:last-child{font-size:13px;color:var(--dim)}
/* кінець глави, естафета */
.end{display:flex;flex-direction:column;gap:14px;padding-top:26px}
.end-mark{text-align:center;font-size:13px;color:var(--faint)}
.k-pal .end-mark{font-variant:all-small-caps;letter-spacing:.1em;font-size:15px}
.k-pult .end-mark{font:500 12px/1 var(--f-num)}
.end-box{padding:14px;display:flex;flex-direction:column;gap:10px}
.k-pal .end-box{padding:14px 0}
.end-box .row .btn{flex:1}
.end-next{display:flex;align-items:center;gap:12px;padding:14px}
.k-pal .end-next{padding:14px 0}
.end-next b{display:block;font:600 17px/1.25 var(--f-h);color:var(--strong)}
.k-obl .end-next b{font-family:var(--f-rd)}
.cm{display:grid;grid-template-columns:auto minmax(0,1fr);gap:4px 10px}
.cm .av{width:32px;height:32px;font-size:13px}
.cm-h{display:flex;gap:8px;align-items:baseline;font-size:13.5px}
.cm-h b{color:var(--strong)}
.cm-t{font-size:14.5px;line-height:1.5}
.cm-q{font-size:13.5px;color:var(--dim);padding-left:10px;box-shadow:inset 2px 0 0 var(--line);margin-bottom:2px}
.cm-sp{background:var(--s2);color:transparent;border-radius:4px;text-shadow:0 0 8px var(--dim)}
.cm-f{display:flex;gap:14px;font-size:12.5px;color:var(--faint)}
.cm-f span{display:inline-flex;align-items:center;gap:4px}
/* стани: повідомлення */
.msg{display:flex;flex-direction:column;align-items:center;text-align:center;gap:14px;padding:110px 18px 0}
.msg .i{color:var(--dim)}
.msg b{font:var(--wh) 21px/1.3 var(--f-h);color:var(--strong)}
.msg p{font-size:14.5px;line-height:1.5;color:var(--dim);max-width:300px}
.offl{display:flex;align-items:center;gap:8px;padding:8px 12px;margin:-8px -4px 16px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);font-size:13px;color:var(--dim)}
.offl .i{color:var(--warn)}
.ld{display:flex;flex-direction:column;gap:11px}
/* сторінки */
.pg-edge{position:absolute;right:0;top:0;bottom:0;width:18px;background:linear-gradient(90deg,transparent,rgba(0,0,0,.18))}

/* ── хром телефона ── */
.r-top-p{height:40px;flex:none;display:flex;align-items:center;gap:6px;padding:0 10px 0 2px;font:12px/1 var(--f-num);color:var(--dim)}
.r-top-p b{color:var(--tx);font-weight:500}
.r-top-p .ib{width:36px;height:36px}
.r-cmd{height:58px;flex:none;display:grid;grid-template-columns:52px 1fr 52px 52px 52px 52px;border-top:1px solid var(--line);background:var(--s0)}
.r-cmd>*{display:flex;align-items:center;justify-content:center;gap:8px;box-shadow:inset -1px 0 0 var(--line);color:var(--dim)}
.r-cmd>:last-child{box-shadow:none}
.r-cmd .mid{font:13px/1 var(--f-num);color:var(--tx)}
.r-cmd .on{color:var(--acc)}
.r-cmd .aa{font:600 16px/1 var(--f-ui);color:var(--tx)}
.r-top-z{height:58px;flex:none;display:flex;align-items:center;gap:2px;padding:0 6px;border-bottom:1px solid var(--line)}
.r-top-z b{font-size:15px;font-weight:600;color:var(--strong)}
.r-top-z .dim{font-size:12.5px}
.aa{font:600 17px/1 var(--f-rd);color:var(--tx)}
.r-bot-z{flex:none;border-top:1px solid var(--line);background:var(--bg)}
.r-bot-z .prog{height:3px;border-radius:0}
.r-bot-z .r3{height:54px;display:grid;grid-template-columns:54px 1fr 54px 54px;align-items:center}
.r-bot-z .r3>button{height:54px;display:grid;place-items:center;color:var(--dim)}
.r-bot-z .mid{text-align:center;font-size:13px;color:var(--dim)}
.r-bot-z .mid b{color:var(--strong)}
.r-top-a{height:50px;flex:none;display:flex;align-items:center;gap:4px;padding:0 10px 0 4px;border-bottom:1px solid var(--line);color:var(--dim)}
.r-top-a .t{flex:1;min-width:0;font-size:14px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.r-top-a em{font-style:normal;color:var(--strong)}
.r-top-a .aa{font:600 20px/1 var(--f-h)}
.r-bot-a{height:40px;flex:none;display:flex;justify-content:space-between;align-items:center;padding:0 20px;font-size:13px;color:var(--faint);border-top:1px solid var(--line)}
.r-bot-a .bar{flex:1;height:1px;background:var(--line);margin:0 14px;position:relative}
.r-bot-a .bar i{position:absolute;left:0;top:-1px;height:3px;width:34%;background:var(--acc)}
.r-top-o{position:absolute;left:12px;right:12px;top:12px;z-index:5;height:56px;border-radius:28px;background:color-mix(in srgb,var(--s2) 90%,transparent);backdrop-filter:blur(10px);display:flex;align-items:center;gap:4px;padding:0 6px}
.r-top-o .ib{width:44px;height:44px}
.r-top-o b{font-size:14px;color:var(--strong)}
.r-top-o .dim{font-size:12px}
.r-top-o .aa{font:700 15px/1 var(--f-h)}
.r-dock{position:absolute;left:16px;right:16px;bottom:16px;z-index:5;height:64px;border-radius:32px;background:var(--s2);display:grid;grid-template-columns:52px 1fr 52px;align-items:center;padding:0 6px;box-shadow:0 10px 30px rgba(0,0,0,.35)}
.r-dock>button{width:52px;height:52px;border-radius:26px;display:grid;place-items:center}
.r-dock .nx{background:var(--acc);color:var(--onacc)}
.r-dock .mid{display:flex;flex-direction:column;gap:6px;padding:0 10px}
.r-dock .mid span{display:flex;justify-content:space-between;font-size:12px;color:var(--dim)}
.r-dock .mid b{color:var(--strong)}

/* ── комп'ютер і планшет ── */
.dr-body{flex:1;min-height:0;overflow:auto;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,700px) minmax(0,1fr);column-gap:48px;align-items:start;scrollbar-width:thin}
.dr-col{grid-column:2;padding:40px 0 80px;position:relative}
.dr-mar{grid-column:3;padding-top:180px;max-width:300px}
.tb .dr-mar{display:none}
.tb .dr-body{grid-template-columns:minmax(0,1fr) minmax(0,640px) minmax(0,1fr);column-gap:0}
.dk .rt p{font-size:19px;line-height:1.72}
.k-pal.dk .dr-col{padding-top:20px}
.k-obl .dr-col{padding-top:110px;padding-bottom:140px}
.pen{position:absolute;left:-44px;top:2px;width:32px;height:32px;display:grid;place-items:center;color:var(--dim);border-radius:var(--rb);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);text-indent:0}
.dk .ed-k,.tb .ed-k{display:block}
.who-m{display:flex;flex-direction:column;gap:10px;padding:16px;background:var(--s1);border-radius:var(--rcard);box-shadow:inset 0 0 0 1px var(--line)}
.who-m .av{width:44px;height:44px;font-size:18px}
.who-m li{font-size:13.5px;line-height:1.45;display:grid;grid-template-columns:42px 1fr;gap:6px}
.who-m li span{color:var(--faint);font-size:12px;padding-top:2px}
.k-pal .who-m{background:transparent;box-shadow:none;padding:0 0 0 16px;border-left:1px solid var(--line)}
.k-pult .who-m{box-shadow:inset 0 0 0 1px var(--line),inset 0 2px 0 var(--acc2)}
.two{display:grid;grid-template-columns:1fr 1fr;gap:64px;height:100%}
.two .rt{overflow:hidden}
.two-f{display:flex;justify-content:space-between;font-size:13px;color:var(--faint);padding-top:14px}
.d-top-p{height:44px;flex:none;display:flex;align-items:center;gap:6px;padding:0 10px 0 6px;border-bottom:1px solid var(--line);background:var(--s0);font:12.5px/1 var(--f-num);color:var(--dim)}
.d-top-p b{color:var(--tx);font-weight:500}
.d-top-p .ib{width:34px;height:34px}
.d-st-p{height:30px;flex:none;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-top:1px solid var(--line);background:var(--s0);font:12px/1 var(--f-num);color:var(--dim)}
.d-st-p .acc{color:var(--acc)}
.d-top-z{height:60px;flex:none;display:flex;align-items:center;gap:6px;padding:0 16px;border-bottom:1px solid var(--line)}
.d-top-z .t{flex:1;text-align:center}
.d-top-z .t b{display:block;font-size:15px;color:var(--strong)}
.d-top-z .t span{font-size:13px;color:var(--dim)}
.d-bot-z{flex:none;border-top:1px solid var(--line)}
.d-bot-z .prog{height:3px;border-radius:0}
.d-bot-z .r{height:52px;display:flex;align-items:center;justify-content:space-between;padding:0 16px;font-size:14px;color:var(--dim)}
.d-bot-z .r b{color:var(--strong)}
.d-top-a{height:56px;flex:none;display:flex;align-items:center;gap:6px;padding:0 20px 0 10px;border-bottom:1px solid var(--line);color:var(--dim)}
.d-top-a .w{font:italic 600 24px/1 var(--f-h);color:var(--strong);margin-right:16px}
.d-top-a .t{flex:1;text-align:center;font:italic 500 19px/1 var(--f-h);color:var(--strong)}
.d-top-a .aa{font:600 20px/1 var(--f-h)}
.d-top-o{position:absolute;top:16px;left:50%;transform:translateX(-50%);width:820px;z-index:5;height:60px;border-radius:30px;background:color-mix(in srgb,var(--s2) 92%,transparent);backdrop-filter:blur(10px);display:flex;align-items:center;gap:6px;padding:0 8px}
.tb .d-top-o{width:760px}
.d-top-o .w{font:800 18px/1 var(--f-h);color:var(--strong);padding:0 10px}
.d-top-o .w i{color:var(--acc);font-style:normal}
.d-top-o .t{flex:1;min-width:0;display:flex;flex-direction:column}
.d-top-o .t b{font-size:14px;color:var(--strong)}
.d-top-o .t span{font-size:12px;color:var(--dim)}
.d-top-o .aa{font:700 15px/1 var(--f-h)}
.d-dock{position:absolute;bottom:18px;left:50%;transform:translateX(-50%);width:560px;z-index:5;height:64px;border-radius:32px;background:var(--s2);display:grid;grid-template-columns:auto 1fr auto;gap:12px;align-items:center;padding:0 8px;box-shadow:0 10px 30px rgba(0,0,0,.35)}
.d-dock .btn{height:48px}
.d-dock .mid{display:flex;flex-direction:column;gap:6px}
.d-dock .mid span{display:flex;justify-content:space-between;font-size:12.5px;color:var(--dim)}
.aa-pop{top:52px;right:24px;width:360px;padding:18px}
.k-obl .aa-pop{top:84px;right:calc(50% - 400px)}
.tb.k-obl .aa-pop{right:48px}
/* «Aa» */
.aa-b{display:flex;flex-direction:column;gap:16px}
.aa-sw{display:flex;gap:10px}
.aa-sw>span{display:flex;flex-direction:column;align-items:center;gap:6px;font-size:12px;color:var(--dim);flex:1}
.aa-sw .sw{width:44px;height:44px;border-radius:var(--rin);display:grid;place-items:center;font:600 15px/1 var(--f-rd)}
.k-obl .aa-sw .sw{border-radius:50%}
.aa-sw .on .sw{box-shadow:0 0 0 2px var(--acc)}
.aa-sw .on{color:var(--strong)}
.aa-r{display:flex;align-items:center;justify-content:space-between;gap:12px;font-size:14.5px}
.aa-r small{display:block;font-size:12px;color:var(--faint)}
.aa-r .seg{min-width:190px}
.aa-more{font-size:14px;color:var(--acc);display:flex;align-items:center;gap:6px}
/* «Хто це», зміст, коментарі, скарга */
.who-h{display:flex;gap:14px;align-items:center}
.who-h .av{width:64px;height:64px;font-size:26px}
.who-f{display:flex;flex-direction:column;gap:10px}
.who-f li{display:grid;grid-template-columns:52px 1fr;gap:8px;font-size:14.5px;line-height:1.45}
.who-f li span{font-size:12.5px;color:var(--faint);padding-top:2px}
.k-pult .who-f li span{font-family:var(--f-num)}
.k-pal .who-f li span{font:600 16px/1.2 var(--f-h);color:var(--acc)}
.note{display:flex;gap:8px;align-items:flex-start;font-size:13px;color:var(--dim);padding:10px 12px;border-radius:var(--rin);background:var(--s2)}
.k-pal .note{background:transparent;padding:0;font-style:italic}
.toc-v{display:flex;justify-content:space-between;align-items:baseline;padding:4px 0}
.toc-r{display:grid;grid-template-columns:34px 1fr auto;gap:8px;align-items:center;padding:10px 4px;border-bottom:1px solid var(--line);font-size:14.5px}
.toc-r .n{color:var(--faint);font-variant-numeric:tabular-nums}
.toc-r.cur{color:var(--acc);font-weight:600}
.toc-r.cur .n{color:var(--acc)}
.toc-r .ok{color:var(--acc2)}
.k-pult .toc-r .n{font-family:var(--f-num);font-size:12.5px}
.k-pal .toc-r{border-bottom:1px dotted var(--line)}
.k-pal .toc-r .n{font:600 18px/1 var(--f-h)}
.k-obl .toc-r{border:0;border-radius:16px;padding:10px 12px}
.k-obl .toc-r.cur{background:var(--acc);color:var(--onacc)}
.k-obl .toc-r.cur .n{color:var(--onacc)}
.rep li{display:flex;gap:12px;align-items:flex-start;padding:10px 0;border-bottom:1px solid var(--line);font-size:14.5px}
.rad{width:20px;height:20px;border-radius:50%;box-shadow:inset 0 0 0 1.5px var(--dim);flex:none;margin-top:1px}
.rad.on{box-shadow:inset 0 0 0 6px var(--acc)}
.k-pult .rad{border-radius:0}
.ta{min-height:88px;padding:10px 12px;border-radius:var(--rin);background:var(--bg);box-shadow:inset 0 0 0 1px var(--line);font-size:14px;color:var(--faint)}
.drawer{position:absolute;top:0;bottom:0;right:0;width:440px;z-index:21;background:var(--s1);display:flex;flex-direction:column;box-shadow:-20px 0 40px rgba(0,0,0,.3)}
.drawer.left{left:0;right:auto;box-shadow:20px 0 40px rgba(0,0,0,.3)}
.k-pal .drawer{background:var(--bg);border-left:1px solid var(--acc)}
.k-obl .drawer{top:12px;bottom:12px;right:12px;border-radius:28px}
.k-obl .drawer.left{left:12px}
.modal{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:520px;z-index:21;background:var(--s1);border-radius:var(--rsheet);box-shadow:0 30px 80px rgba(0,0,0,.45)}
.k-pal .modal{background:var(--bg);box-shadow:0 30px 80px rgba(0,0,0,.45),inset 0 0 0 1px var(--acc)}
`;

// ── шматки тексту ──
const P = (i, extra = '') => `<p class="${i === 0 ? 'first' : ''}"${extra}>${fmt(CH12[i])}</p>`;
const chHead = `<header class="chh"><span class="chh-n">12</span><h1 class="chh-t">Лінза, що пам’ятає світло</h1><div class="chh-meta"><span class="chh-k">Том 1 · глава 12</span><span class="chh-m">ще 6 хв</span></div></header>`;
const editBlock = (dk) => `<div class="ed"><div class="ed-l"><span>Ваша правка · абзац 2</span><span>не надіслано</span></div>
<div class="ed-f">${EDITED}<span class="caret"></span></div>
<div class="ed-t"><button type="button">${ic('spark', 16)} Варіант від ШІ</button><button type="button">${ic('eye', 16)} Показати оригінал</button></div>
<div class="ed-a"><button type="button" class="btn">Скасувати</button><button type="button" class="btn pri">Зберегти</button></div>
<p class="ed-n">Правка піде команді на перевірку; вам її видно в тексті.</p>${dk ? '<span class="ed-k">Esc — скасувати · ⌘↵ — зберегти</span>' : ''}</div>`;
const selbar = `<div class="selbar" role="toolbar" aria-label="Дії з виділеним"><button type="button">${ic('user', 18)}Хто це</button><button type="button">${ic('pen', 18)}Виправити</button><button type="button">${ic('repl', 18)}Замінити</button><button type="button">${ic('quote', 18)}Цитувати</button></div>`;
const divider = kk({
  pult: `<div class="chd-p">гл 13 · ${CH13.t}</div>`,
  zv: `<div class="chd-z"><span>Глава 13</span><h2>${CH13.t}</h2></div>`,
  pal: `<div class="chd-a"><span class="fl">❦</span><span class="k">Глава 13</span><h2>${CH13.t}</h2></div>`,
  obl: `<div class="chd-o"><span class="n">13</span><b>${CH13.t}</b><span>Далі без зупинок · глава підвантажилась</span></div>`
});
const skel = (n = 5) => `<div class="ld" aria-label="Глава 13 вантажиться">${[100, 97, 100, 88, 62, 100, 94, 70].slice(0, n).map(w => `<span class="sk" style="height:14px;width:${w}%"></span>`).join('')}</div>`;
const cm = (c) => `<div class="cm"><span class="av">${c.av}</span><div class="col" style="gap:4px"><div class="cm-h"><b>${c.who}</b><span class="faint">${c.when}</span></div>${c.quote ? `<div class="cm-q">«${c.quote}»</div>` : ''}<p class="cm-t">${c.spoiler ? c.text.replace(/\|\|(.+?)\|\|/, '<span class="cm-sp">$1</span>') : c.text}</p><div class="cm-f"><span>${ic('up2', 14)} ${c.votes}</span><span>${ic('reply', 14)} Відповісти${c.replies ? ' · ' + c.replies : ''}</span></div></div></div>`;
const endBlock = `<section class="end">
<p class="end-mark">Кінець глави 12</p>
<div class="card end-box"><div class="row"><span class="grow"><b class="strong">2 правки не надіслано</b><br><span class="dim" style="font-size:13.5px">Їх бачите лише ви, доки не надішлете команді.</span></span></div><div class="row"><button type="button" class="btn sm">Переглянути</button><button type="button" class="btn sm pri">Надіслати команді</button></div></div>
<a href="ReaderT.dc.html" class="card end-next"><span class="grow"><span class="lab">Далі</span><b>13. ${CH13.t}</b></span>${ic('next', 22)}</a>
<div class="row"><button type="button" class="btn sm out">${ic('star', 16)} Оцінити переклад</button><button type="button" class="btn sm out">${ic('flag', 16)} Поскаржитися</button></div>
<div class="col" style="gap:14px;padding-top:6px"><div class="row"><span class="h3 grow">Обговорення глави</span><span class="faint">14</span></div>${cm(COMMENTS[0])}${cm(COMMENTS[2])}<div class="field">Написати коментар…</div></div>
</section>`;
const relayBlock = `<section class="end">
<p class="end-mark">Кінець глави 40 · переклад «Солоної води»</p>
<div class="card end-box"><span class="lab">Естафета</span><b class="h2">Далі перекладає команда «Світанок»</b><p class="dim" style="font-size:14.5px;line-height:1.5">Вона продовжила переклад з глави 41. Місце читання й бібліотека переходять разом з вами.</p><a href="ReaderT.dc.html" class="btn pri wide">Читати главу 41</a><a href="NovelT.dc.html" class="btn out wide">Про переклад «Світанку»</a></div>
</section>`;
const errBlock = `<div class="msg" role="alert">${ic('alert', 40, 1.5)}<b>Не вдалося відкрити главу</b><p>Сайт не відповів. Спробуйте ще раз за хвилину — місце в главі збережено.</p><button type="button" class="btn pri">${ic('refresh', 18)} Спробувати ще раз</button><a href="NovelT.dc.html" class="btn out">До змісту</a></div>`;
const offBlock = `<div class="offl">${ic('wifioff', 18)} Ви офлайн — показуємо збережені глави</div><div class="msg" style="padding-top:70px" role="status">${ic('wifioff', 40, 1.5)}<b>Глава 12 не збережена</b><p>Без інтернету можна читати збережені глави: 9–11. Нові зберігаються самі, коли є зв’язок — до трьох наперед.</p><a href="ReaderT.dc.html" class="btn pri">Читати главу 11</a></div>`;
const loadBlock = `<div class="ld" aria-busy="true" aria-label="Глава відкривається"><span class="sk" style="height:12px;width:30%;margin-bottom:6px"></span><span class="sk" style="height:26px;width:82%;margin-bottom:18px"></span>${[100, 100, 96, 64].map(w => `<span class="sk" style="height:14px;width:${w}%"></span>`).join('')}<span style="height:10px"></span>${[100, 98, 100, 80].map(w => `<span class="sk" style="height:14px;width:${w}%"></span>`).join('')}<span style="height:10px"></span>${[100, 92, 52].map(w => `<span class="sk" style="height:14px;width:${w}%"></span>`).join('')}</div>`;

// ── стани тексту (спільні для пристроїв) ──
function body(dev) {
  const dk = dev !== 'ph';
  return [
    iff('is.read', `${chHead}<div class="rt">${P(0)}${P(1)}${P(2)}<p><span class="mine">ваша правка · на перевірці</span>${fmt(CH12[3])}</p>${P(4)}${P(5)}${P(6)}${P(7)}</div>`, true),
    iff('is.sel', `${chHead}<div class="rt"><p class="first">Маяк стояв темним уже третю ніч, коли <span class="selw">Ліна</span> нарешті піднялася сходами.</p>${selbar}${P(1)}${P(2)}${P(3)}${P(4)}${P(5)}</div>`),
    iff('is.edit', `${chHead}<div class="rt">${P(0)}${dk ? `<div style="position:relative">${editBlock(true)}</div>` : editBlock(false)}${dk ? `<p><button type="button" class="pen" aria-label="Виправити абзац">${ic('pen', 16)}</button>${fmt(CH12[2])}</p>` : P(2)}${P(3)}${P(4)}${P(5)}</div>`),
    iff('is.seam', `<div class="rt"><p>${fmt(CH12[5])}</p>${P(6)}${P(7)}</div>${divider}<div class="rt"><p class="first">${CH13.p}</p></div><div style="height:14px"></div>${skel(dk ? 8 : 5)}`),
    iff('is.end', `<div class="rt"><p>${fmt(CH12[6])}</p>${P(7)}</div>${endBlock}`),
    iff('is.relay', `<div class="rt"><p>Світло лінзи пройшло над водою і зникло в тумані. Цього разу ніхто не запізнився.</p></div>${relayBlock}`),
    iff('is.load', loadBlock),
    iff('is.err', errBlock),
    iff('is.off', offBlock)
  ].join('\n');
}

// ── телефон ──
const lamp = `<button type="button" class="ib on" aria-label="Підказки зі словника" aria-pressed="true">${ic('bulb', 20)}</button>`;
const phoneTop = kk({
  pult: `<div class="r-top-p"><a href="NovelT.dc.html" class="ib" aria-label="До новели">${ic('back', 18)}</a><span class="grow ell">Доглядач маяка › <b>гл 12</b></span>${iff('not.pages', '<span>34% · 6 хв</span>', true)}${iff('is.pages', '<span>с 4/11</span>')}</div><div class="prog" style="border-radius:0"><i style="width:34%"></i></div>`,
  zv: `<div class="r-top-z"><a href="NovelT.dc.html" class="ib" aria-label="До новели">${ic('back', 21)}</a><div class="grow col"><b class="ell">Доглядач маяка</b><span class="dim">Глава 12 з 40</span></div>${lamp}<button type="button" class="ib aa" aria-label="Вигляд читалки">Aa</button><button type="button" class="ib" aria-label="Зміст">${ic('list', 20)}</button></div>`,
  pal: `<div class="r-top-a"><a href="NovelT.dc.html" class="ib" aria-label="До новели">${ic('back', 20)}</a><span class="t">Доглядач маяка · <em>глава 12</em></span>${lamp}<button type="button" class="ib aa" aria-label="Вигляд читалки">Aa</button><button type="button" class="ib" aria-label="Зміст">${ic('list', 20)}</button></div>`,
  obl: `<div class="r-top-o"><a href="NovelT.dc.html" class="ib" aria-label="До новели">${ic('back', 20)}</a><div class="grow col"><b class="ell">Доглядач маяка</b><span class="dim">Глава 12 з 40</span></div>${lamp}<button type="button" class="ib aa" aria-label="Вигляд читалки">Aa</button></div>`
});
const phoneBot = kk({
  pult: `<nav class="r-cmd" aria-label="Читалка"><button type="button" aria-label="Попередня глава">${ic('back', 20)}</button><button type="button" class="mid">${ic('list', 18)} ${'<sc-if value="{{ is.pages }}" hint-placeholder-val="{{ false }}">с 4 / 11</sc-if><sc-if value="{{ not.pages }}" hint-placeholder-val="{{ true }}">12 / 40</sc-if>'}</button><button type="button" class="aa" aria-label="Вигляд читалки">Aa</button><button type="button" class="on" aria-label="Підказки зі словника" aria-pressed="true">${ic('bulb', 20)}</button><button type="button" aria-label="Обговорення глави">${ic('chat', 20)}</button><button type="button" aria-label="Наступна глава">${ic('next', 20)}</button></nav>`,
  zv: `<div class="r-bot-z"><div class="prog"><i style="width:34%"></i></div><div class="r3"><button type="button" aria-label="Попередня глава">${ic('back', 20)}</button><span class="mid">${iff('is.pages', 'Сторінка <b>4</b> з 11')}${iff('not.pages', '<b>34 %</b> · ще 6 хв', true)}</span><button type="button" aria-label="Обговорення глави">${ic('chat', 20)}</button><button type="button" aria-label="Наступна глава">${ic('next', 20)}</button></div></div>`,
  pal: `<div class="r-bot-a"><span>${iff('is.pages', 'с. 4 з 11')}${iff('not.pages', '34 % · ще 6 хв', true)}</span><span class="bar"><i></i></span><span>12 / 40</span></div>`,
  obl: `<div class="r-dock"><button type="button" aria-label="Попередня глава">${ic('back', 20)}</button><div class="mid"><div class="prog"><i style="width:34%"></i></div><span><b>${iff('is.pages', 'с. 4 з 11')}${iff('not.pages', '34%', true)}</b><span>Зміст</span></span></div><button type="button" class="nx" aria-label="Наступна глава">${ic('next', 20)}</button></div>`
});
const pagesPhone = iff('is.pages', `<div class="rt" style="height:100%;overflow:hidden">${P(2)}${P(3)}${P(4)}${P(5)}${P(6)}${P(7)}</div>`);

export const READER_STANY = ['читання', 'виділення', 'правка', 'безшовно', 'сторінки', 'кінець глави', 'естафета', 'заготовка', 'помилка', 'без інтернету'];
export const READER_IDS = ['read', 'sel', 'edit', 'seam', 'pages', 'end', 'relay', 'load', 'err', 'off'];

export const ReaderT = {
  file: 'ReaderT.dc.html', title: 'Читалка — телефон', w: 390, h: 844, dev: 'ph', stany: READER_STANY, ids: READER_IDS, extra: READER_CSS,
  root: `<div class="{{ cls }}" style="width:390px;height:844px;display:flex;flex-direction:column">
${phoneTop}
<main class="rs">${body('ph')}${pagesPhone}</main>
${phoneBot}
</div>`
};

// ── «Aa», «Хто це», зміст, коментарі, скарга ──
export const aaBody = `<div class="aa-b">
<div class="col" style="gap:10px"><span class="lab">Тло тексту</span><div class="aa-sw">
<span class="on"><span class="sw" style="background:var(--bg);color:var(--read)">Аа</span>Як сайт</span>
<span><span class="sw" style="background:#F4F2EC;color:#1C1B18">Аа</span>Папір</span>
<span><span class="sw" style="background:#2A251D;color:#E6D9BF">Аа</span>Сепія</span>
<span><span class="sw" style="background:#1B1E24;color:#C9CDD5">Аа</span>Сірий</span>
<span><span class="sw" style="background:#000;color:#BFC3CC">Аа</span>Чорний</span></div></div>
<div class="aa-r"><span>Шрифт</span><div class="seg"><span class="on">Каркаса</span><span>Інший</span><span>Для дислексії</span></div></div>
<div class="aa-r"><span>Розмір</span><div class="row"><button type="button" class="ib" aria-label="Менше">${ic('minus', 18)}</button><b class="num strong" style="width:28px;text-align:center">18</b><button type="button" class="ib" aria-label="Більше">${ic('plus', 18)}</button></div></div>
<div class="aa-r"><span>Міжрядок</span><div class="seg"><span>1,5</span><span class="on">1,7</span><span>1,9</span></div></div>
<div class="aa-r"><span>Вирівнювання</span><div class="seg"><span class="on">По ширині</span><span>Ліворуч</span></div></div>
<div class="aa-r"><span>Читати</span><div class="seg"><span class="on">Прокруткою</span><span>Сторінками</span></div></div>
<div class="aa-r"><span>Безшовно<small>наступна глава починається одразу</small></span><span class="tog on" role="switch" aria-checked="true"></span></div>
<div class="aa-r"><span>Підказки зі словника<small>лампочка в шапці</small></span><span class="tog on" role="switch" aria-checked="true"></span></div>
<a href="#" class="aa-more">Усі налаштування читання ${ic('next', 16)}</a></div>`;
const whoBody = `<div class="who-h"><span class="av">${WHO.letter}</span><div class="col" style="gap:3px"><b class="h2">${WHO.name}</b><span class="dim">${WHO.kind} · ${WHO.role}</span></div></div>
<ul class="who-f">${WHO.facts.map(([n, t]) => `<li><span>гл. ${n}</span>${t}</li>`).join('')}</ul>
<p class="note">${ic('eye', 16)} Лише те, що відомо до глави 12 — без спойлерів.</p>
<div class="row"><a href="NovelT.dc.html" class="btn sm grow">Усі персонажі</a><button type="button" class="btn sm out">Тут не про неї</button></div>
<p class="faint" style="font-size:12.5px">«Тут не про неї» бачить лише команда перекладу: слово відв’язується від запису словника.</p>`;
const tocBody = `<div class="field">${ic('search', 16)} Номер або назва глави</div>
<div class="toc-v"><span class="lab">Том 1 · Сіль і світло</span><span class="faint" style="font-size:12.5px">прочитано 11 з 24</span></div>
<div>${CHAPS.map(c => `<div class="toc-r ${c.cur ? 'cur' : ''}"><span class="n">${c.n}</span><span class="ell">${c.t}</span>${c.read ? `<span class="ok">${ic('check', 16)}</span>` : '<span></span>'}</div>`).join('')}</div>
<div class="toc-v"><span class="lab">Том 2 · Журнал доглядача</span><span class="faint" style="font-size:12.5px">25–40</span></div>`;
const commBody = `<div class="seg"><span class="on">Найкращі</span><span>Нові</span></div>${COMMENTS.map(cm).join('<div class="hr"></div>')}<div class="field" style="height:auto;min-height:var(--hb);padding:10px 12px">Написати коментар… виділіть текст у главі, щоб процитувати</div>`;
const repBody = `<p class="dim" style="font-size:14px">Що не так із главою 12?</p>
<ul class="rep"><li><span class="rad on"></span>Помилки перекладу чи пропущений текст</li><li><span class="rad"></span>Вміст 18+ без позначки</li><li><span class="rad"></span>Спам чи реклама</li><li><span class="rad"></span>Порушення прав — я правовласник</li></ul>
<div class="ta">Що саме не так? Наприклад: абзаци 5–7 не перекладено.</div>
<p class="faint" style="font-size:12.5px">Причину побачать лише модератори. Відповідь прийде у «Сповіщення».</p>
<button type="button" class="btn pri wide">Надіслати скаргу</button>`;
const sheet = (title, b) => `<div class="scrim"></div><div class="sheet" role="dialog" aria-label="${title}"><div class="grab"></div><div class="sh-h"><b class="h2 grow">${title}</b><button type="button" class="ib" aria-label="Закрити">${ic('x', 20)}</button></div><div class="sh-b">${b}</div></div>`;

export const ReaderPanelsT = {
  file: 'ReaderPanelsT.dc.html', title: 'Панелі читалки — телефон', w: 390, h: 844, dev: 'ph',
  stany: ['Aa', 'Хто це', 'Зміст', 'Коментарі', 'Скарга на главу'], ids: ['aa', 'who', 'toc', 'comm', 'rep'], extra: READER_CSS,
  root: `<div class="{{ cls }}" style="width:390px;height:844px;display:flex;flex-direction:column">
${phoneTop}
<main class="rs">${chHead}<div class="rt">${P(0)}${P(1)}${P(2)}${P(3)}</div></main>
${phoneBot}
${iff('is.aa', sheet('Вигляд тексту', aaBody), true)}
${iff('is.who', sheet('Хто це', whoBody))}
${iff('is.toc', sheet('Зміст', tocBody))}
${iff('is.comm', sheet('Обговорення глави · 14', commBody))}
${iff('is.rep', sheet('Поскаржитися на главу', repBody))}
</div>`
};

// ── комп'ютер і планшет ──
const themeBtn = `<button type="button" class="ib" onClick="{{ toggleLight }}" aria-label="Світла чи темна тема">${iff('dark', ic('sun', 19), true)}${iff('light', ic('moon', 19))}</button>`;
const deskTop = kk({
  pult: `<div class="d-top-p"><a href="NovelK.dc.html" class="ib" aria-label="До новели">${ic('back', 18)}</a><span class="grow ell">Доглядач маяка › <b>гл 12 · Лінза, що пам’ятає світло</b></span><span>34% · ще 6 хв</span><span style="width:10px"></span>${lamp}<button type="button" class="ib aa" aria-label="Вигляд читалки">Aa</button><button type="button" class="ib" aria-label="Зміст">${ic('list', 19)}</button><button type="button" class="ib" aria-label="Обговорення">${ic('chat', 19)}</button>${themeBtn}</div><div class="prog" style="border-radius:0"><i style="width:34%"></i></div>`,
  zv: `<div class="d-top-z"><a href="NovelK.dc.html" class="ib" aria-label="До новели">${ic('back', 21)}</a><span style="width:170px"></span><div class="t"><b>Доглядач маяка</b><span>Глава 12 з 40 · Лінза, що пам’ятає світло</span></div>${lamp}<button type="button" class="ib aa" aria-label="Вигляд читалки">Aa</button><button type="button" class="ib" aria-label="Зміст">${ic('list', 20)}</button><button type="button" class="ib" aria-label="Обговорення">${ic('chat', 20)}</button>${themeBtn}</div>`,
  pal: `<div class="d-top-a"><a href="NovelK.dc.html" class="ib" aria-label="До новели">${ic('back', 20)}</a><span class="w">Новелка</span><span class="lab">Доглядач маяка</span><span class="t">глава 12 · Лінза, що пам’ятає світло</span>${lamp}<button type="button" class="ib aa" aria-label="Вигляд читалки">Aa</button><button type="button" class="ib" aria-label="Зміст">${ic('list', 20)}</button>${themeBtn}</div>`,
  obl: `<div class="d-top-o"><a href="NovelK.dc.html" class="ib" aria-label="До новели">${ic('back', 20)}</a><span class="w">новелка<i>.</i></span><div class="t"><b class="ell">Доглядач маяка</b><span>Глава 12 з 40</span></div>${lamp}<button type="button" class="ib aa" aria-label="Вигляд читалки">Aa</button><button type="button" class="ib" aria-label="Зміст">${ic('list', 20)}</button>${themeBtn}</div>`
});
const deskBot = kk({
  pult: `<div class="d-st-p"><span>‹ гл 11 · Пором о сьомій</span><span><span class="acc">●</span> гл 12 / 40 · ${'<sc-if value="{{ is.two }}" hint-placeholder-val="{{ false }}">с 7–8 / 11</sc-if><sc-if value="{{ not.two }}" hint-placeholder-val="{{ true }}">34%</sc-if>'} · ← → глави</span><span>гл 13 · Журнал доглядача ›</span></div>`,
  zv: `<div class="d-bot-z"><div class="prog"><i style="width:34%"></i></div><div class="r"><button type="button" class="row">${ic('back', 18)} Пором о сьомій</button><span>${iff('is.two', 'Сторінки <b>7–8</b> з 11')}${iff('not.two', '<b>34 %</b> · ще 6 хв', true)}</span><button type="button" class="row">Журнал доглядача ${ic('next', 18)}</button></div></div>`,
  pal: `<div class="r-bot-a" style="height:44px;padding:0 28px"><span>‹ 11 · Пором о сьомій</span><span class="bar"><i></i></span><span>${iff('is.two', 'с. 7–8 з 11')}${iff('not.two', '34 % · ще 6 хв', true)}</span><span class="bar" style="flex:.3"></span><span>13 · Журнал доглядача ›</span></div>`,
  obl: `<div class="d-dock"><button type="button" class="btn">${ic('back', 18)} 11</button><div class="mid"><div class="prog"><i style="width:34%"></i></div><span><b class="strong">${iff('is.two', 'с. 7–8 з 11')}${iff('not.two', '34% · ще 6 хв', true)}</b><span>Глава 12 з 40</span></span></div><button type="button" class="btn pri">13 ${ic('next', 18)}</button></div>`
});
const whoMargin = `<aside class="who-m" aria-label="Хто це"><div class="row" style="gap:10px"><span class="av">${WHO.letter}</span><div class="col"><b class="strong">${WHO.name}</b><span class="dim" style="font-size:12.5px">${WHO.role}</span></div></div><ul class="col" style="gap:8px">${WHO.facts.slice(1, 4).map(([n, t]) => `<li><span>гл. ${n}</span>${t}</li>`).join('')}</ul><span class="faint" style="font-size:12px">до глави 12 · без спойлерів</span></aside>`;
const deskBody = (dev) => `<div class="dr-body"><main class="dr-col">${body(dev)}${iff('is.two', `<div class="two"><div class="col"><div class="rt">${P(0)}${P(1)}${P(2)}</div><div class="two-f"><span>7</span></div></div><div class="col"><div class="rt">${P(3)}${P(4)}${P(5)}</div><div class="two-f"><span></span><span>8</span></div></div></div>`)}</main>${iff('is.read', `<div class="dr-mar">${whoMargin}</div>`, true)}</div>`;

const DSTANY = ['читання', 'правка', 'Aa', 'безшовно', 'дві сторінки', 'заготовка'];
const DIDS = ['read', 'edit', 'aa', 'seam', 'two', 'load'];
const deskRoot = (dev, w, h) => `<div class="{{ cls }}" style="width:${w}px;height:${h}px;display:flex;flex-direction:column">
${deskTop}
${deskBody(dev)}
${deskBot}
${iff('is.aa', `<div class="pop aa-pop" role="dialog" aria-label="Вигляд тексту">${aaBody}</div>`)}
</div>`;

export const ReaderK = {
  file: 'ReaderK.dc.html', title: 'Читалка — комп’ютер', w: 1440, h: 900, dev: 'dk', stany: DSTANY, ids: DIDS, alias: { readish: ['read', 'aa'] }, extra: READER_CSS,
  root: deskRoot('dk', 1440, 900)
};
export const ReaderP = {
  file: 'ReaderP.dc.html', title: 'Читалка — планшет', w: 834, h: 1194, dev: 'tb', stany: ['читання', 'правка', 'Aa', 'дві сторінки'], ids: ['read', 'edit', 'aa', 'two'], alias: { readish: ['read', 'aa'] }, extra: READER_CSS,
  root: deskRoot('tb', 834, 1194)
};
// «Aa» на комп'ютері й планшеті: текст глави лишається під панеллю
for (const s of [ReaderK, ReaderP]) s.root = s.root.replace(`<sc-if value="{{ is.read }}" hint-placeholder-val="{{ true }}">${chHead}`, `<sc-if value="{{ readish }}" hint-placeholder-val="{{ true }}">${chHead}`);

export const ReaderPanelsK = {
  file: 'ReaderPanelsK.dc.html', title: 'Панелі читалки — комп’ютер', w: 1440, h: 900, dev: 'dk',
  stany: ['Зміст', 'Коментарі', 'Скарга на главу'], ids: ['toc', 'comm', 'rep'], extra: READER_CSS,
  root: `<div class="{{ cls }}" style="width:1440px;height:900px;display:flex;flex-direction:column">
${deskTop}
<div class="dr-body"><main class="dr-col">${chHead}<div class="rt">${P(0)}${P(1)}${P(2)}${P(3)}${P(4)}</div></main></div>
${deskBot}
${iff('is.toc', `<div class="scrim"></div><div class="drawer left" role="dialog" aria-label="Зміст"><div class="sheet-like sh-h" style="display:flex;align-items:center;padding:16px 16px 8px 22px"><b class="h2 grow">Зміст</b><button type="button" class="ib" aria-label="Закрити">${ic('x', 20)}</button></div><div class="sh-b" style="overflow:auto;padding:4px 22px 22px;display:flex;flex-direction:column;gap:14px">${tocBody}</div></div>`, true)}
${iff('is.comm', `<div class="scrim"></div><div class="drawer" role="dialog" aria-label="Обговорення"><div style="display:flex;align-items:center;padding:16px 16px 8px 22px"><b class="h2 grow">Обговорення глави · 14</b><button type="button" class="ib" aria-label="Закрити">${ic('x', 20)}</button></div><div style="overflow:auto;padding:4px 22px 22px;display:flex;flex-direction:column;gap:16px">${commBody}</div></div>`)}
${iff('is.rep', `<div class="scrim"></div><div class="modal" role="dialog" aria-label="Скарга"><div style="display:flex;align-items:center;padding:18px 16px 6px 24px"><b class="h2 grow">Поскаржитися на главу</b><button type="button" class="ib" aria-label="Закрити">${ic('x', 20)}</button></div><div style="padding:6px 24px 24px;display:flex;flex-direction:column;gap:14px">${repBody}</div></div>`)}
</div>`
};
