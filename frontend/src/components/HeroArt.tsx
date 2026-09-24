/**
 * Hero illustration: a river leaves the pages of an open book and winds towards the moon-lit hills,
 * paper cranes fly over it and a tanzaku slip carries 物語 ("stories"). Colours come from --art-* theme tokens.
 */
export function HeroArt() {
    return <svg className="hero-illustration" viewBox="0 0 520 380" role="img" aria-label="Розгорнута книга, з якої річка тече до гір під місяцем">
        <g className="art-stars" fill="var(--art-star)">
            <circle cx="58" cy="52" r="1.6" /><circle cx="112" cy="96" r="1.1" /><circle cx="418" cy="44" r="1.8" />
            <circle cx="470" cy="118" r="1.2" /><circle cx="214" cy="30" r="1.3" /><circle cx="392" cy="150" r="1" /><circle cx="36" cy="160" r="1.2" />
        </g>
        <circle cx="306" cy="128" r="92" fill="var(--art-moon)" />
        <circle cx="306" cy="128" r="112" fill="none" stroke="var(--art-line)" strokeOpacity=".45" />
        <circle cx="306" cy="128" r="134" fill="none" stroke="var(--art-line)" strokeOpacity=".22" strokeDasharray="2 7" />
        <path d="M0 246C70 204 138 222 206 196S340 164 402 186 486 170 520 176V380H0Z" fill="var(--art-hill-3)" />
        <path d="M0 282C86 248 168 268 248 244S392 220 452 246 500 240 520 236V380H0Z" fill="var(--art-hill-2)" />
        <path d="M232 334C252 302 300 302 292 274S242 250 262 228 312 214 320 196L326 198C320 220 292 224 280 238S300 258 306 278C316 308 268 314 288 334Z" fill="var(--art-river)" />
        <path d="M0 318C108 296 188 316 278 298S436 304 520 290V380H0Z" fill="var(--art-hill-1)" />
        <g className="art-cranes" fill="var(--art-crane)" stroke="var(--art-crane-edge)" strokeWidth=".8" strokeLinejoin="round">
            <g transform="translate(176 150) rotate(-8)"><path d="M0 0L20-13 11 3Z" /><path d="M0 0L-15-17-5 3Z" /><path d="M-14 4L22 1 5 8Z" /><path d="M22 1L29-5 26 3Z" /></g>
            <g transform="translate(236 104) scale(.62) rotate(6)"><path d="M0 0L20-13 11 3Z" /><path d="M0 0L-15-17-5 3Z" /><path d="M-14 4L22 1 5 8Z" /><path d="M22 1L29-5 26 3Z" /></g>
            <g transform="translate(118 206) scale(.48) rotate(-14)"><path d="M0 0L20-13 11 3Z" /><path d="M0 0L-15-17-5 3Z" /><path d="M-14 4L22 1 5 8Z" /><path d="M22 1L29-5 26 3Z" /></g>
        </g>
        <g className="art-slip">
            <line x1="448" y1="0" x2="448" y2="58" stroke="var(--art-line)" strokeWidth="1.2" />
            <rect x="430" y="58" width="36" height="112" rx="2" fill="var(--art-slip)" transform="rotate(4 448 58)" />
            <g transform="rotate(4 448 58)" fill="var(--art-ink)" fontFamily="Georgia, 'Noto Serif JP', serif" fontSize="24" textAnchor="middle">
                <text x="448" y="100">物</text><text x="448" y="134">語</text>
            </g>
        </g>
        <path d="M128 340C184 318 234 322 260 338 286 322 336 318 392 340L392 376C336 356 286 360 260 374 234 360 184 356 128 376Z" fill="var(--art-book)" />
        <path d="M138 336C188 318 234 322 258 334V368C234 356 188 354 138 370Z" fill="var(--art-page)" />
        <path d="M382 336C332 318 286 322 262 334V368C286 356 332 354 382 370Z" fill="var(--art-page)" />
        <g stroke="var(--art-page-line)" strokeWidth="1" fill="none" strokeLinecap="round">
            <path d="M156 342C190 331 222 332 244 340" /><path d="M156 350C190 339 222 340 244 348" /><path d="M156 358C190 347 222 348 244 356" />
            <path d="M276 340C298 332 330 331 364 342" /><path d="M276 348C298 340 330 339 364 350" /><path d="M276 356C298 348 330 347 364 358" />
        </g>
    </svg>;
}
