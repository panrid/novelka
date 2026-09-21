export function ActionNotice({ error, message }: { error: string; message: string }) {
    return <>{error && <p className="form-error" role="alert">{error}</p>}{message && <p className="form-success" role="status">{message}</p>}</>;
}
