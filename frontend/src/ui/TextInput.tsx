import { FieldError, Input, Label, Text, TextArea, TextField, type TextFieldProps } from 'react-aria-components';
import styles from './ui.module.css';

export type TextInputProps = Omit<TextFieldProps, 'className' | 'children'> & {
    label: string;
    hint?: string;
    /** A message from the server about this field. */
    error?: string | undefined;
    multiline?: boolean;
    placeholder?: string;
};

export function TextInput({ label, hint, error, multiline = false, placeholder, ...props }: TextInputProps) {
    return (
        <TextField {...props} className={styles.field} isInvalid={Boolean(error) || Boolean(props.isInvalid)}>
            <Label className={styles.label}>{label}</Label>
            {multiline ? (
                <TextArea className={styles.input} {...(placeholder ? { placeholder } : {})} />
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
