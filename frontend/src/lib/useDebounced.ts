import { useEffect, useState } from 'react';

/** The value once it stopped changing for {@code delay} ms: typing «30» asks once, not for «3» first. */
export function useDebounced<T>(value: T, delay = 500): T {
    const [settled, setSettled] = useState(value);
    useEffect(() => {
        const timer = setTimeout(() => setSettled(value), delay);
        return () => clearTimeout(timer);
    }, [value, delay]);
    return settled;
}
