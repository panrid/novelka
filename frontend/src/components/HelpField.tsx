import { useId, type ReactNode } from 'react';
import { HelpTip } from './HelpTip';

/** Form field whose "?" sits next to the label text without becoming part of the input's accessible name. */
export function HelpField({ label, help, children }: { label: string; help: ReactNode; children: (id: string) => ReactNode }) {
    const id = useId();
    return <div className="help-field">
        <span className="help-field-label"><label htmlFor={id}>{label}</label><HelpTip label={label}>{help}</HelpTip></span>
        {children(id)}
    </div>;
}
