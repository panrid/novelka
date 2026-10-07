/*
 * Runs before the page draws anything, so it opens in the person's look instead of flashing
 * the default one first. The app (src/appearance/store.ts) keeps the computed variables of the
 * site's look in the browser; the names here mirror src/appearance/model.ts.
 */
(function () {
    try {
        var root = document.documentElement;
        var saved = JSON.parse(localStorage.getItem('novelka:appearance-css') || 'null');
        var raw = localStorage.getItem('novelka:appearance');
        var look = raw ? JSON.parse(raw) : {};
        var theme = 'dark';
        var preset = 'default';
        if (saved && saved.vars) {
            theme = saved.theme || theme;
            preset = saved.preset || preset;
            root.dataset.motion = saved.motion || 'system';
        } else if (!raw) {
            var old = localStorage.getItem('novelka:theme');
            theme = old === 'light' ? 'light' : old === 'black' ? 'black' : 'dark';
            preset = old === 'light' ? 'light' : old === 'black' ? 'night' : 'default';
        }
        var readerThemes = { paper: 'light', sepia: 'light', gray: 'light', tea: 'light', night: 'dark', dusk: 'dark', black: 'black' };
        var reading = /^\/n\/[^/]+\/\d+\/?$/.test(location.pathname);
        var colors = look.reader && readerThemes[look.reader.colors];
        var ownColors = reading && colors;
        var colorVars = ['--bg', '--surface', '--surface-2', '--line', '--text', '--text-reading', '--muted', '--faint',
            '--accent', '--accent-ink', '--focus', '--card-border', '--card-shadow'];
        if (saved && saved.vars) {
            for (var name in saved.vars) {
                if (ownColors && colorVars.indexOf(name) >= 0) continue;
                root.style.setProperty(name, saved.vars[name]);
            }
        }
        if (ownColors) {
            theme = colors;
            root.dataset.reader = look.reader.colors;
        }
        root.dataset.style = preset;
        root.dataset.theme = theme;
    } catch {
        // Storage blocked: the default look.
    }
})();
