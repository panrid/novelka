import { useEffect, useRef, useState } from 'react';
import { Button, FieldError, Input, Label, Text, TextArea, TextField, type TextFieldProps } from 'react-aria-components';
import { Eye, EyeOff } from './icons';
import styles from './ui.module.css';

export type TextInputProps = Omit<TextFieldProps, 'className' | 'children'> & {
    label: string;
    hint?: string | undefined;
    /** A message from the server about this field. */
    error?: string | undefined;
    multiline?: boolean;
    placeholder?: string;
};

export function TextInput({ label, hint, error, multiline = false, placeholder, ...props }: TextInputProps) {
    const password = props.type === 'password' && !multiline;
    const [shown, setShown] = useState(false);
    const input = useRef<HTMLInputElement>(null);

    // A sent form hides the password again, whatever happens next.
    useEffect(() => {
        const form = password ? input.current?.form : null;
        if (!form) return;
        const hide = () => setShown(false);
        form.addEventListener('submit', hide);
        return () => form.removeEventListener('submit', hide);
    }, [password]);

    return (
        <TextField {...props} {...(password && shown ? { type: 'text' } : {})} className={styles.field}
            isInvalid={Boolean(error) || Boolean(props.isInvalid)}>
            <Label className={styles.label}>{label}</Label>
            {multiline ? (
                <TextArea className={styles.input} {...(placeholder ? { placeholder } : {})} />
            ) : password ? (
                <div className={styles.passwordBox}>
                    <Input ref={input} className={styles.input} {...(placeholder ? { placeholder } : {})} />
                    {/* Keeps focus (and the phone's keyboard) in the field when tapped. */}
                    <Button className={styles.reveal} preventFocusOnPress aria-pressed={shown}
                        aria-label={shown ? 'Сховати пароль' : 'Показати пароль'} onPress={() => setShown((s) => !s)}>
                        {shown ? <EyeOff size={20} /> : <Eye size={20} />}
                    </Button>
                </div>
            ) : (
                <Input className={styles.input} {...(placeholder ? { placeholder } : {})} />
            )}
            {hint && !error && (
                <Text slot="description" className={styles.hint}>
                    {hint}
                </Text>
            )}
            <FieldError className={styles.fieldError}>{error}</FieldError>
        </TextField>
    );
}
