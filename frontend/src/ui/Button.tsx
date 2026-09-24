import type { ReactNode } from 'react';
import { Button as AriaButton, type ButtonProps as AriaButtonProps } from 'react-aria-components';
import styles from './ui.module.css';

type Variant = 'primary' | 'secondary' | 'danger' | 'quiet';

export type ButtonProps = Omit<AriaButtonProps, 'className' | 'children'> & {
    variant?: Variant;
    wide?: boolean;
    /** Shows `pendingLabel` and blocks presses while a request runs. */
    pending?: boolean;
    pendingLabel?: string;
    children: ReactNode;
};

export function Button({ variant = 'primary', wide = false, pending = false, pendingLabel, children, ...props }: ButtonProps) {
    const className = [styles.button, styles[variant], wide ? styles.wide : ''].join(' ');
    return (
        <AriaButton {...props} className={className} isDisabled={props.isDisabled || pending}>
            {pending && pendingLabel ? pendingLabel : children}
        </AriaButton>
    );
}
