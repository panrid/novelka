// Спільне для всіх екранів нового макета: теми (кольори), каркаси (будова), значки, обгортка
// файлу .dc.html і простий показ без полотна для перевірки (preview).

export const KARKASY = [['Пульт', 'pult'], ['Звичний', 'zv'], ['Палітурка', 'pal'], ['Обкладинка', 'obl']];
export const TEMY = [['Сланець', 'slanets'], ['Графіт', 'graphite'], ['Сукно', 'sukno'], ['Слива', 'slyva']];

const FONTS = 'https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500;600&amp;family=IBM+Plex+Sans:wght@400;500;600;700&amp;family=IBM+Plex+Serif:ital,wght@0,400;0,600;1,400&amp;family=Golos+Text:wght@400;500;600;700&amp;family=Literata:ital,opsz,wght@0,7..72,400;0,7..72,600;0,7..72,700;1,7..72,400&amp;family=Cormorant+Garamond:ital,wght@0,500;0,600;0,700;1,500;1,600&amp;family=Spectral:ital,wght@0,400;0,500;0,600;1,400&amp;family=Alegreya+Sans:ital,wght@0,400;0,500;0,700;1,400&amp;family=Unbounded:wght@500;600;700;800&amp;family=Manrope:wght@400;500;600;700;800&amp;family=Lora:ital,wght@0,400;0,600;0,700;1,400&amp;display=swap';

// ── Теми: лише кольори; однакові змінні для всіх каркасів ──
const THEMES = `
.th-slanets{--bg:#1C1F25;--s0:#17191E;--s1:#22262D;--s2:#2A303A;--line:#333842;--tx:#D6DAE1;--strong:#EEF0F4;--read:#D9DCE2;--dim:#8D95A4;--faint:#636A77;--acc:#F2B35B;--onacc:#1D1404;--acc2:#5CCFBF;--onacc2:#0A1F1C;--term:rgba(92,207,191,.16);--selw:rgba(242,179,91,.30);--del:#F39479;--delbg:rgba(243,148,121,.13);--ins:#F5C27A;--insbg:rgba(242,179,91,.16);--skel:#2B3038;--scrim:rgba(8,9,12,.62);--warn:#F2B35B}
.th-slanets.lt{--bg:#F3F4F6;--s0:#E8EAEE;--s1:#FFFFFF;--s2:#EEF1F5;--line:#D5D9E0;--tx:#1B1F27;--strong:#0B0E14;--read:#1E222A;--dim:#596070;--faint:#8C93A0;--acc:#A86200;--onacc:#FFFFFF;--acc2:#0B786B;--onacc2:#FFFFFF;--term:#D7F1EC;--selw:#F7D9A6;--del:#B23A17;--delbg:#FBE4DC;--ins:#7A4600;--insbg:#FCEBCF;--skel:#E3E6EB;--scrim:rgba(20,24,32,.35);--warn:#8A5200}
.th-graphite{--bg:#0F1115;--s0:#0B0D10;--s1:#171A20;--s2:#1F232B;--line:#2A2F39;--tx:#E8EAEE;--strong:#F1F2F5;--read:#DADDE3;--dim:#A3A9B6;--faint:#6F7584;--acc:#8EA6FF;--onacc:#0B0D12;--acc2:#F2C46B;--onacc2:#1A1405;--term:rgba(255,196,64,.20);--selw:rgba(142,166,255,.28);--del:#FFB48A;--delbg:rgba(255,140,80,.18);--ins:#D3DCFF;--insbg:rgba(142,166,255,.22);--skel:#23272F;--scrim:rgba(5,6,9,.62);--warn:#F2C46B}
.th-graphite.lt{--bg:#F3F4F6;--s0:#E9EBEF;--s1:#FFFFFF;--s2:#F1F2F5;--line:#E3E5EA;--tx:#15171C;--strong:#0B0D12;--read:#1B1D22;--dim:#5B6170;--faint:#8A909C;--acc:#2A4BD7;--onacc:#FFFFFF;--acc2:#7A4B00;--onacc2:#FFFFFF;--term:#FFF1C2;--selw:#DCE6FF;--del:#7A3410;--delbg:#FDE6D3;--ins:#102A8C;--insbg:#DCE6FF;--skel:#E6E8EC;--scrim:rgba(15,17,21,.35);--warn:#7A4B00}
.th-sukno{--bg:#0E1613;--s0:#0A110E;--s1:#131E1A;--s2:#1A2722;--line:#2A3A33;--tx:#E9E2CC;--strong:#F4EEDC;--read:#DCD5BE;--dim:#A9A58F;--faint:#767B6C;--acc:#D2A84E;--onacc:#17120A;--acc2:#9CC7A4;--onacc2:#0E1613;--term:rgba(210,168,78,.17);--selw:rgba(210,168,78,.34);--del:#E59072;--delbg:rgba(229,144,114,.14);--ins:#EBCB7C;--insbg:rgba(210,168,78,.14);--skel:#22302A;--scrim:rgba(4,8,6,.62);--warn:#D2A84E}
.th-sukno.lt{--bg:#E6E9E1;--s0:#DCE1D6;--s1:#F7F8F2;--s2:#EDEFE6;--line:#C9D0C2;--tx:#18221D;--strong:#0C130F;--read:#1E2621;--dim:#535E4F;--faint:#7F877A;--acc:#85601A;--onacc:#FFFFFF;--acc2:#2F6B45;--onacc2:#FFFFFF;--term:#F1E2B7;--selw:#E6CB82;--del:#A4431E;--delbg:#F6DDD2;--ins:#6A4A0C;--insbg:#F1E2B7;--skel:#DDE2D6;--scrim:rgba(14,22,19,.35);--warn:#85601A}
.th-slyva{--bg:#191120;--s0:#140D1A;--s1:#241830;--s2:#2F2140;--line:#3F2E50;--tx:#F7ECF4;--strong:#FFF6FB;--read:#EEE1EA;--dim:#BCA7BE;--faint:#8E7A94;--acc:#FF7EA4;--onacc:#2B0716;--acc2:#F4E27C;--onacc2:#2A2306;--term:rgba(255,126,164,.2);--selw:rgba(255,126,164,.36);--del:#FFB08F;--delbg:rgba(255,140,100,.16);--ins:#F4E27C;--insbg:rgba(244,226,124,.15);--skel:#2E2240;--scrim:rgba(10,4,14,.62);--warn:#F4E27C}
.th-slyva.lt{--bg:#FFF4F7;--s0:#FBE9EF;--s1:#FFFFFF;--s2:#FCE7EF;--line:#F1D2DE;--tx:#2A1430;--strong:#1A0A1F;--read:#2A1430;--dim:#6D5370;--faint:#9F89A1;--acc:#CF2E66;--onacc:#FFFFFF;--acc2:#7C6300;--onacc2:#FFFFFF;--term:#FFD9E5;--selw:#FFC2D5;--del:#A93A12;--delbg:#FFE3D6;--ins:#6B5400;--insbg:#FFF1B8;--skel:#F6DCE6;--scrim:rgba(42,20,48,.35);--warn:#7C6300}
`;

// ── Каркаси: шрифти, кути, щільність, форма деталей ──
const KARKAS_VARS = `
.k-pult{--f-ui:'IBM Plex Sans','Segoe UI',sans-serif;--f-rd:'IBM Plex Serif',Georgia,serif;--f-h:'IBM Plex Sans','Segoe UI',sans-serif;--f-num:'IBM Plex Mono',ui-monospace,monospace;--fs:14px;--wh:600;
  --hb:34px;--hbs:28px;--pb:12px;--rb:0px;--wb:500;--fsb:13.5px;--hc:26px;--pc:8px;--rc:0px;--fsc:12.5px;--rcard:0px;--rsheet:0px;--rsk:0px;--rin:0px;--hp:2px;--rp:0px;--gap:12px}
.k-zv{--f-ui:'Golos Text',-apple-system,'Segoe UI',sans-serif;--f-rd:'Literata',Georgia,serif;--f-h:'Literata',Georgia,serif;--f-num:'Golos Text',sans-serif;--fs:15px;--wh:700;
  --hb:40px;--hbs:32px;--pb:16px;--rb:10px;--wb:600;--fsb:14.5px;--hc:30px;--pc:12px;--rc:999px;--fsc:13px;--rcard:14px;--rsheet:20px;--rsk:6px;--rin:10px;--hp:4px;--rp:2px;--gap:14px}
.k-pal{--f-ui:'Alegreya Sans','Segoe UI',sans-serif;--f-rd:'Spectral',Georgia,serif;--f-h:'Cormorant Garamond',Georgia,serif;--f-num:'Cormorant Garamond',Georgia,serif;--fs:16px;--wh:600;
  --hb:40px;--hbs:32px;--pb:16px;--rb:2px;--wb:700;--fsb:15px;--hc:28px;--pc:10px;--rc:2px;--fsc:14px;--rcard:0px;--rsheet:0px;--rsk:2px;--rin:2px;--hp:2px;--rp:0px;--gap:16px}
.k-obl{--f-ui:'Manrope','Segoe UI',sans-serif;--f-rd:'Lora',Georgia,serif;--f-h:'Unbounded','Arial Black',sans-serif;--f-num:'Unbounded','Arial Black',sans-serif;--fs:15px;--wh:700;
  --hb:44px;--hbs:34px;--pb:18px;--rb:999px;--wb:700;--fsb:14px;--hc:32px;--pc:14px;--rc:999px;--fsc:13px;--rcard:22px;--rsheet:28px;--rsk:10px;--rin:16px;--hp:8px;--rp:4px;--gap:12px}
.ph.k-pult{--hb:44px;--hbs:36px} .ph.k-zv{--hb:46px;--hbs:38px} .ph.k-pal{--hb:44px;--hbs:38px} .ph.k-obl{--hb:50px;--hbs:40px}
`;

// ── Спільні деталі, що беруть вигляд від змінних каркаса й теми ──
const BASE = `
body{margin:0;background:#101114}
.nv{position:relative;overflow:hidden;box-sizing:border-box;background:var(--bg);color:var(--tx);font:var(--fs)/1.45 var(--f-ui);-webkit-font-smoothing:antialiased}
.nv *,.nv *::before,.nv *::after{box-sizing:border-box}
.nv :where(button){font:inherit;color:inherit;background:none;border:0;padding:0;cursor:pointer;text-align:inherit}
.nv :where(a){color:inherit;text-decoration:none}
.nv :where(p,h1,h2,h3,h4,ul,ol,figure){margin:0;padding:0}
.nv :where(ul,ol){list-style:none}
.nv :where(input,textarea,select){font:inherit;color:inherit}
.nv :where(button,a,input,textarea,select):focus-visible{outline:2px solid var(--acc);outline-offset:2px}
.nv svg.i{display:block;flex:none}
.sk{display:block;background:var(--skel);border-radius:var(--rsk);animation:nvP 1.6s ease-in-out infinite}
@keyframes nvP{50%{opacity:.45}}
@keyframes nvB{50%{opacity:0}}
@keyframes nvS{to{transform:rotate(360deg)}}
.spin{animation:nvS .9s linear infinite}
.caret{display:inline-block;width:2px;height:1.1em;vertical-align:-.18em;background:currentColor;margin-left:1px;animation:nvB 1s steps(1) infinite}
@media (prefers-reduced-motion:reduce){.sk,.caret,.spin{animation:none}}
.term{background:var(--term);border-radius:3px}
.k-pal .term{box-shadow:0 1px 0 var(--acc);border-radius:2px}
.k-obl .term{border-radius:6px;padding:0 2px}
.selw{background:var(--selw);border-radius:3px}
.mono{font-family:var(--f-num)}
.dim{color:var(--dim)} .faint{color:var(--faint)} .strong{color:var(--strong)} .acc{color:var(--acc)} .acc2{color:var(--acc2)}
.grow{flex:1;min-width:0}
.row{display:flex;align-items:center;gap:8px;min-width:0}
.col{display:flex;flex-direction:column;min-width:0}
.ell{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.num{font-variant-numeric:tabular-nums}
.btn{display:inline-flex;align-items:center;justify-content:center;gap:8px;height:var(--hb);padding:0 var(--pb);border-radius:var(--rb);background:var(--s2);color:var(--tx);font-weight:var(--wb);font-size:var(--fsb);white-space:nowrap;flex:none}
.btn.pri{background:var(--acc);color:var(--onacc)}
.btn.sec{background:var(--acc2);color:var(--onacc2)}
.btn.out{background:transparent;box-shadow:inset 0 0 0 1px var(--line)}
.btn.sm{height:var(--hbs);padding:0 calc(var(--pb) * .75);font-size:calc(var(--fsb) - 1px)}
.btn.wide{width:100%}
.btn.dis{opacity:.45;cursor:default}
.k-pal .btn{background:transparent;box-shadow:inset 0 0 0 1px var(--line);font-variant:all-small-caps;letter-spacing:.06em;font-size:calc(var(--fsb) + 2px)}
.k-pal .btn.pri{background:var(--acc);box-shadow:none}
.k-pal .btn.sec{background:transparent;box-shadow:inset 0 0 0 1px var(--acc2);color:var(--acc2)}
.k-pult .btn.out,.k-pult .btn{box-shadow:inset 0 0 0 1px var(--line)}
.k-pult .btn.pri,.k-pult .btn.sec{box-shadow:none}
.ib{width:var(--hb);height:var(--hb);display:grid;place-items:center;border-radius:var(--rb);color:var(--dim);flex:none;position:relative}
.ib.on{color:var(--acc)}
.k-zv .ib.on{background:var(--s2)}
.k-obl .ib.on{background:var(--acc);color:var(--onacc)}
.ib .dot{position:absolute;top:4px;right:2px;min-width:16px;height:16px;padding:0 4px;border-radius:8px;background:var(--acc);color:var(--onacc);font:700 10px/16px var(--f-ui);text-align:center}
.k-pult .ib .dot{border-radius:0;font-family:var(--f-num);font-weight:500}
.chip{display:inline-flex;align-items:center;gap:6px;height:var(--hc);padding:0 var(--pc);border-radius:var(--rc);background:var(--s2);color:var(--dim);font-size:var(--fsc);white-space:nowrap;flex:none}
.chip.on{background:var(--acc);color:var(--onacc)}
.k-pult .chip{background:transparent;box-shadow:inset 0 0 0 1px var(--line);font-family:var(--f-num);font-size:12px}
.k-pult .chip.on{background:var(--acc);box-shadow:none}
.k-pal .chip{background:transparent;padding:0 2px;height:auto;color:var(--dim);border-bottom:1px dotted var(--dim);border-radius:0}
.k-pal .chip.on{color:var(--acc);border-bottom:1px solid var(--acc);background:transparent}
.lab{font-size:12px;letter-spacing:.06em;text-transform:uppercase;color:var(--dim);font-weight:600}
.k-pult .lab{font:500 11px/1.3 var(--f-num);letter-spacing:.08em}
.k-pal .lab{text-transform:none;font-variant:all-small-caps;letter-spacing:.1em;font-size:15px;font-weight:500;color:var(--acc)}
.k-obl .lab{font:700 11px/1.3 var(--f-h);letter-spacing:.04em}
.h1{font:var(--wh) 26px/1.2 var(--f-h);color:var(--strong);text-wrap:balance}
.h2{font:var(--wh) 20px/1.25 var(--f-h);color:var(--strong);text-wrap:balance}
.h3{font:600 16px/1.3 var(--f-ui);color:var(--strong)}
.k-pal .h1{font-size:32px;font-weight:600}
.k-pal .h2{font-size:25px}
.k-obl .h1{font-size:24px;font-weight:700;letter-spacing:-.01em}
.k-obl .h2{font-size:18px;letter-spacing:-.01em}
.k-pult .h1{font-size:22px}
.k-pult .h2{font-size:17px}
.card{background:var(--s1);border-radius:var(--rcard)}
.k-pult .card{box-shadow:inset 0 0 0 1px var(--line)}
.k-zv .card{box-shadow:inset 0 0 0 1px var(--line)}
.k-pal .card{background:transparent;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
.prog{height:var(--hp);background:var(--s2);border-radius:var(--rp);overflow:hidden;flex:none}
.prog>i{display:block;height:100%;background:var(--acc);border-radius:var(--rp)}
.field{display:flex;align-items:center;gap:8px;height:var(--hb);padding:0 12px;border-radius:var(--rin);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line);color:var(--faint);width:100%}
.k-pal .field{background:transparent;box-shadow:none;border-bottom:1px solid var(--dim);border-radius:0;padding:0 2px}
.sw{width:22px;height:22px;border-radius:50%;box-shadow:inset 0 0 0 1px rgba(128,128,128,.4);flex:none}
.tog{width:40px;height:24px;border-radius:12px;background:var(--s2);box-shadow:inset 0 0 0 1px var(--line);position:relative;flex:none}
.tog::after{content:"";position:absolute;top:3px;left:3px;width:18px;height:18px;border-radius:50%;background:var(--dim)}
.tog.on{background:var(--acc);box-shadow:none}
.tog.on::after{left:19px;background:var(--onacc)}
.k-pult .tog,.k-pult .tog::after{border-radius:0}
.k-pal .tog,.k-pal .tog::after{border-radius:2px}
.seg{display:flex;padding:3px;gap:2px;border-radius:calc(var(--rb) + 3px);background:var(--s1);box-shadow:inset 0 0 0 1px var(--line)}
.seg>*{flex:1;height:calc(var(--hb) - 8px);display:grid;place-items:center;border-radius:var(--rb);color:var(--dim);font-size:var(--fsc);padding:0 8px;white-space:nowrap}
.seg>.on{background:var(--s2);color:var(--strong);font-weight:600}
.k-pult .seg{padding:0;gap:0;border-radius:0}
.k-pult .seg>*{height:var(--hbs);border-radius:0;box-shadow:inset -1px 0 0 var(--line)}
.k-pult .seg>.on{background:var(--acc);color:var(--onacc)}
.k-pal .seg{background:transparent;box-shadow:none;padding:0;gap:14px}
.k-pal .seg>*{flex:none;height:auto;padding:2px 0;border-radius:0;font-variant:all-small-caps;letter-spacing:.08em;font-size:16px}
.k-pal .seg>.on{background:transparent;color:var(--acc);border-bottom:1.5px solid var(--acc)}
.k-obl .seg>.on{background:var(--acc);color:var(--onacc)}
.av{width:36px;height:36px;border-radius:50%;display:grid;place-items:center;background:var(--s2);color:var(--strong);font-weight:700;font-size:14px;flex:none}
.k-pult .av{border-radius:0;font-family:var(--f-num);font-weight:500}
.k-obl .av{background:var(--acc);color:var(--onacc);font-family:var(--f-h)}
.k-pal .av{font-family:var(--f-h);font-size:18px;background:transparent;box-shadow:inset 0 0 0 1px var(--acc);color:var(--acc)}
.scrim{position:absolute;inset:0;background:var(--scrim);z-index:20}
.sheet{position:absolute;left:0;right:0;bottom:0;z-index:21;background:var(--s1);border-radius:var(--rsheet) var(--rsheet) 0 0;display:flex;flex-direction:column;max-height:86%;box-shadow:0 -10px 40px rgba(0,0,0,.35)}
.k-pult .sheet{border-top:1px solid var(--line)}
.k-pal .sheet{background:var(--bg);border-top:1px solid var(--acc)}
.k-obl .sheet{left:8px;right:8px;bottom:8px;border-radius:var(--rsheet)}
.sheet .grab{width:40px;height:4px;border-radius:2px;background:var(--line);margin:8px auto 2px;flex:none}
.k-pult .sheet .grab,.k-pal .sheet .grab{display:none}
.sheet .sh-h{display:flex;align-items:center;gap:8px;padding:10px 16px 10px 20px;flex:none}
.sheet .sh-b{overflow:auto;padding:4px 20px 22px;display:flex;flex-direction:column;gap:18px}
.k-pult .sheet .sh-h{border-bottom:1px solid var(--line);padding:8px 8px 8px 16px}
.pop{position:absolute;z-index:21;background:var(--s1);border-radius:calc(var(--rcard) * .8);box-shadow:0 14px 40px rgba(0,0,0,.4),inset 0 0 0 1px var(--line)}
.k-pal .pop{background:var(--bg);box-shadow:0 14px 40px rgba(0,0,0,.4),inset 0 0 0 1px var(--acc)}
.hr{height:1px;background:var(--line);flex:none}
.k-pal .hr{background:none;border-top:1px dotted var(--line)}
.state{display:inline-flex;align-items:center;gap:6px;font-size:12.5px;color:var(--dim);white-space:nowrap}
.state i{width:8px;height:8px;border-radius:50%;background:var(--dim);flex:none}
.state.pub i{background:var(--acc2)} .state.dr i{background:var(--acc)} .state.q i{background:transparent;box-shadow:inset 0 0 0 1.5px var(--dim)}
.k-pult .state{font-family:var(--f-num);font-size:11.5px}
.cover{position:relative;overflow:hidden;background:var(--s2);border-radius:calc(var(--rcard) * .5);display:flex;flex-direction:column;justify-content:flex-end;padding:10px;color:var(--strong);font:700 13px/1.15 var(--f-h);flex:none}
.cover::before{content:"";position:absolute;left:-20%;right:-20%;top:18%;height:46%;border-radius:50%;background:var(--acc);opacity:.22}
.cover::after{content:"";position:absolute;left:46%;top:10%;width:6%;height:52%;background:var(--strong);opacity:.5;border-radius:2px 2px 0 0}
.cover b{position:relative;z-index:1}
.k-pult .cover{border-radius:0}
.k-pal .cover{border-radius:2px;box-shadow:inset 0 0 0 1px var(--acc)}
.k-pal .cover b{font-weight:600;font-size:15px}
.k-obl .cover{border-radius:14px}
.cover.c1{background:#1D3B44;color:#F4EBD8}.cover.c1::before{background:#E9B65C;opacity:.35}
.cover.c2{background:#5A2A1E;color:#FBE7D3}.cover.c2::before{background:#F08A4B;opacity:.4}
.cover.c3{background:#1E2A5A;color:#E5ECFF}.cover.c3::before{background:#5FB6E8;opacity:.4}
.cover.c4{background:#3B3F22;color:#F1EED7}.cover.c4::before{background:#C9C26A;opacity:.35}
.cover.c5{background:#2B1830;color:#F6E7F1}.cover.c5::before{background:#C7C9D8;opacity:.3}
.cover.c6{background:#18363A;color:#E3F4F1}.cover.c6::before{background:#E47CA0;opacity:.35}
.cover.c1::after,.cover.c2::after,.cover.c3::after,.cover.c4::after,.cover.c5::after,.cover.c6::after{background:currentColor}
`;

export const CSS = THEMES + KARKAS_VARS + BASE;

// ── Значки: лінійні, 24×24 ──
const P = {
  back: '<path d="M15 5l-7 7 7 7"/>', next: '<path d="M9 5l7 7-7 7"/>', down: '<path d="M6 9l6 6 6-6"/>', up: '<path d="M6 15l6-6 6 6"/>',
  bulb: '<path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z"/>',
  list: '<path d="M9 6h11M9 12h11M9 18h11M4.5 6h.01M4.5 12h.01M4.5 18h.01"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.6 4.6l1.4 1.4M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4L6 18M18 6l1.4-1.4"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"/>',
  pen: '<path d="M4 20h4L19 9l-4-4L4 16v4z"/>', spark: '<path d="M12 3l1.7 5.3L19 10l-5.3 1.7L12 17l-1.7-5.3L5 10l5.3-1.7z"/>',
  search: '<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>', home: '<path d="M4 11l8-7 8 7v9H4z"/><path d="M10 20v-6h4v6"/>',
  compass: '<circle cx="12" cy="12" r="8.5"/><path d="M15.5 8.5l-2 5-5 2 2-5z"/>', lib: '<path d="M5 4h4v16H5zM10 4h4v16h-4z"/><path d="M15 5l4-1 2 15-4 1z"/>',
  layers: '<path d="M12 3l9 5-9 5-9-5 9-5z"/><path d="M3 13l9 5 9-5"/>', pulse: '<path d="M3 12h4l3-7 4 14 3-7h4"/>',
  book: '<path d="M4 5a2 2 0 0 1 2-2h14v16H6a2 2 0 0 0-2 2V5z"/><path d="M4 19a2 2 0 0 1 2-2h14"/>',
  bell: '<path d="M6 9a6 6 0 1 1 12 0c0 5 2 6 2 6H4s2-1 2-6"/><path d="M10 19a2 2 0 0 0 4 0"/>', chat: '<path d="M4 5h16v11H9l-5 4V5z"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>', users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M21.5 20a6.5 6.5 0 0 0-4-6"/>',
  more: '<circle cx="5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/>', bookmark: '<path d="M6 3h12v18l-6-4-6 4z"/>',
  bmplus: '<path d="M6 3h12v18l-6-4-6 4z"/><path d="M12 7v6M9 10h6"/>', star: '<path d="M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"/>',
  dl: '<path d="M12 4v11M7 10l5 5 5-5M5 20h14"/>', flag: '<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>', quote: '<path d="M7 7h4v4c0 3-1.5 5-4 6M14 7h4v4c0 3-1.5 5-4 6"/>',
  repl: '<path d="M4 7h13l-3-3M20 17H7l3 3"/>', x: '<path d="M6 6l12 12M18 6L6 18"/>', check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>', plus: '<path d="M12 5v14M5 12h14"/>', minus: '<path d="M5 12h14"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>', clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  undo: '<path d="M9 14L4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/>', wifioff: '<path d="M2 8.8a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5h.01"/><path d="M3 3l18 18"/>',
  alert: '<path d="M12 3l10 18H2z"/><path d="M12 10v4M12 17.5h.01"/>', refresh: '<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 5v6h-6"/>', ring: '<path d="M21 12a9 9 0 1 1-6.2-8.6"/>',
  filter: '<path d="M4 5h16l-6 8v6l-4-2v-4z"/>', sort: '<path d="M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4"/>', share: '<circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="6" r="2.5"/><circle cx="18" cy="18" r="2.5"/><path d="M8.2 10.8l7.6-3.6M8.2 13.2l7.6 3.6"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="1"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>', gear: '<circle cx="12" cy="12" r="3"/><path d="M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"/>',
  up2: '<path d="M12 19V5M6 11l6-6 6 6"/>', reply: '<path d="M10 8L4 13l6 5"/><path d="M4 13h10a6 6 0 0 1 6 6"/>', cols: '<rect x="3" y="4" width="18" height="16"/><path d="M9 4v16M15 4v16"/>',
  img: '<rect x="3" y="5" width="18" height="14" rx="1"/><circle cx="9" cy="10" r="1.6"/><path d="M21 16l-5-5-8 8"/>', relay: '<path d="M4 12h12M12 6l6 6-6 6"/><path d="M20 5v14"/>',
  trash: '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/>', pages: '<rect x="3" y="4" width="8" height="16"/><rect x="13" y="4" width="8" height="16"/>', scroll: '<path d="M7 3h10v18H7z"/><path d="M10 8h4M10 12h4M10 16h2"/>',
  type: '<path d="M4 7V5h16v2M9 19h6M12 5v14"/>'
};
export const ic = (n, s = 20, w = 1.8) => `<svg class="i" width="${s}" height="${s}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[n]}</svg>`;

export const fmt = t => t.replace(/\[\[(.+?)\]\]/g, '<span class="term">$1</span>').replace(/\{\{(.+?)\}\}/g, '<span class="selw">$1</span>');
export const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');

// Розгалуження за каркасом: kk({pult: '…', zv: '…', pal: '…', obl: '…'})
export const kk = m => Object.entries(m).map(([k, v]) => `<sc-if value="{{ k.${k} }}" hint-placeholder-val="{{ ${k === 'pult'} }}">${v}</sc-if>`).join('\n');
export const iff = (path, body, hint = false) => `<sc-if value="{{ ${path} }}" hint-placeholder-val="{{ ${hint} }}">${body}</sc-if>`;

// Спільна логіка кожного екрана: каркас, тема, світла, стан
export function baseVals(props, state, ui, opt) {
  const K = { 'Пульт': 'pult', 'Звичний': 'zv', 'Палітурка': 'pal', 'Обкладинка': 'obl' };
  const T = { 'Сланець': 'slanets', 'Графіт': 'graphite', 'Сукно': 'sukno', 'Слива': 'slyva' };
  const kid = K[props.karkas] || 'pult';
  const tid = T[props.tema] || 'slanets';
  const light = state.light !== undefined ? state.light : !!props.svitla;
  const stan = state.stan || props.stan || opt.stany[0];
  const is = {}, not = {};
  opt.stany.forEach((s, i) => { is[opt.ids[i]] = s === stan; not[opt.ids[i]] = s !== stan; });
  const k = { pult: kid === 'pult', zv: kid === 'zv', pal: kid === 'pal', obl: kid === 'obl' };
  const alias = {};
  Object.entries(opt.alias || {}).forEach(([a, ids]) => { alias[a] = ids.some(id => is[id]); });
  const curId = opt.ids[opt.stany.indexOf(stan)];
  return {
    ...alias,
    scls: (opt.scls || {})[curId] || '',
    cls: 'nv ' + opt.dev + ' k-' + kid + ' th-' + tid + (light && opt.dev !== 'ph' ? ' lt' : ''),
    k, is, not, light, dark: !light,
    toggleLight: () => ui.setState({ light: !light }),
    go: Object.fromEntries(opt.ids.map((id, i) => [id, () => ui.setState({ stan: opt.stany[i] })]))
  };
}

// Обгортка файлу .dc.html
export function dcFile({ title, w, h, dev, stany, ids, alias, scls, root, extra = '' }) {
  const props = {
    karkas: { editor: 'enum', options: KARKASY.map(x => x[0]), default: 'Пульт' },
    tema: { editor: 'enum', options: TEMY.map(x => x[0]), default: 'Сланець' }
  };
  if (dev !== 'ph') props.svitla = { editor: 'boolean', default: false };
  if (stany.length > 1) props.stan = { editor: 'enum', options: stany, default: stany[0] };
  props.$preview = { width: w, height: h };
  const json = JSON.stringify(props).replace(/&/g, '&amp;').replace(/'/g, '&#39;');
  const opt = JSON.stringify({ dev, stany, ids, alias, scls });
  return `<!doctype html>
<html lang="uk">
<head>
<meta charset="utf-8">
<title>${title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="${FONTS}" rel="stylesheet">
<style>${CSS}${extra}</style>
</helmet>
${root}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='${json}'>
${baseVals.toString()}
class Component extends DCLogic {
  renderVals() {
    return baseVals(this.props, this.state || {}, this, ${opt});
  }
}
</script>
</body>
</html>
`;
}

// ── Показ без полотна: підставляє значення, sc-if і sc-for, як це робить середовище полотна ──
function get(scope, path) {
  path = path.trim();
  if (path === 'true') return true; if (path === 'false') return false;
  if (/^-?\d+(\.\d+)?$/.test(path)) return Number(path);
  return path.split('.').reduce((o, p) => (o == null ? undefined : o[p]), scope);
}
function findClose(s, tag, from) {
  let depth = 1, i = from;
  const open = '<' + tag, close = '</' + tag + '>';
  while (depth > 0) {
    const o = s.indexOf(open, i), c = s.indexOf(close, i);
    if (c < 0) throw new Error('unclosed ' + tag);
    if (o >= 0 && o < c) { depth++; i = o + open.length; } else { depth--; i = c + close.length; if (depth === 0) return c; }
  }
}
export function render(s, scope) {
  let out = '', i = 0;
  const re = /<(sc-if|sc-for)\b([^>]*)>/g;
  let m;
  while ((m = re.exec(s))) {
    out += holes(s.slice(i, m.index), scope);
    const tag = m[1], attrs = m[2];
    const bodyStart = m.index + m[0].length, closeAt = findClose(s, tag, bodyStart);
    const body = s.slice(bodyStart, closeAt);
    if (tag === 'sc-if') {
      const v = get(scope, /value="\{\{(.+?)\}\}"/.exec(attrs)[1]);
      if (v) out += render(body, scope);
    } else {
      const list = get(scope, /list="\{\{(.+?)\}\}"/.exec(attrs)[1]) || [];
      const as = /as="(\w+)"/.exec(attrs)[1];
      list.forEach((it, idx) => { out += render(body, { ...scope, [as]: it, $index: idx }); });
    }
    i = closeAt + tag.length + 3;
    re.lastIndex = i;
  }
  return out + holes(s.slice(i), scope);
}
function holes(s, scope) {
  return s.replace(/\s(on[A-Z]\w*)="\{\{[^}]+\}\}"/g, '').replace(/\{\{(.+?)\}\}/g, (_, p) => { const v = get(scope, p); return v == null || typeof v === 'function' ? '' : String(v); });
}
export function previewHtml({ title, root, w, h, dev, stany, ids, alias, scls, extra = '' }, props, state = {}) {
  const vals = baseVals(props, state, { setState() {} }, { dev, stany, ids, alias, scls });
  return `<!doctype html><html lang="uk"><head><meta charset="utf-8"><title>${title}</title><link href="${FONTS}" rel="stylesheet"><style>${CSS}${extra}</style></head><body>${render(root, vals)}</body></html>`;
}
