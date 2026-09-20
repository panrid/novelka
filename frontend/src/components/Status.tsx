export function Loading() {
    return <div className="status-panel" role="status"><span className="loading-dot" />Завантажуємо історію…</div>;
}

export function ErrorState({ message, retry }: { message: string; retry: () => void }) {
    return <div className="status-panel" role="alert">
        <h2>Не вдалося відкрити сторінку</h2>
        <p>{message}</p>
        <button className="button" onClick={retry}>Спробувати ще раз</button>
        <a className="text-link" href="#/">До каталогу</a>
    </div>;
}
