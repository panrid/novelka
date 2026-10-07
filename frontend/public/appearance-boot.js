/*
 * Runs before the page draws anything, so it opens in the person's style instead of flashing
 * the default one first. The app (src/appearance/store.ts) takes over once it loads; the
 * names here mirror src/appearance/model.ts.
 */
(function () {
    try {
        var root = document.documentElement;
        var raw = localStorage.getItem('novelka:appearance');
        var look = raw ? JSON.parse(raw) : {};
        if (!raw) {
            var old = localStorage.getItem('novelka:theme');
            look = { site: { preset: old === 'light' ? 'light' : old === 'black' ? 'night' : 'default' } };
        }
        var siteThemes = { default: 'dark', light: 'light', night: 'black' };
        var readerThemes = { paper: 'light', night: 'dark', black: 'black' };
        var preset = look.site && siteThemes[look.site.preset] ? look.site.preset : 'default';
        var theme = siteThemes[preset];
        var reading = /^\/n\/[^/]+\/\d+\/?$/.test(location.pathname);
        var colors = look.reader && readerThemes[look.reader.colors];
        if (reading && colors) theme = colors;
        root.dataset.style = preset;
        root.dataset.theme = theme;
    } catch {
        // Storage blocked: the default style.
    }
})();
