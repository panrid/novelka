import { Label, Radio, RadioGroup } from 'react-aria-components';
import styles from './ui.module.css';

export function Segmented<T extends string>({
    label,
    value,
    options,
    onChange,
}: {
    label: string;
    value: T;
    options: readonly { value: T; label: string }[];
    onChange: (value: T) => void;
}) {
    return (
        <RadioGroup className={styles.field} value={value} onChange={(next) => onChange(next as T)} orientation="horizontal">
            <Label className={styles.label}>{label}</Label>
            <div className={styles.segmented}>
                {options.map((option) => (
                    <Radio key={option.value} value={option.value} className={styles.segment}>
                        {option.label}
                    </Radio>
                ))}
            </div>
        </RadioGroup>
    );
}
