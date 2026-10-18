import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { TextInput } from './TextInput';

describe('a password field', () => {
    it('hides the password until the eye is pressed, and hides it again on the second press', async () => {
        render(<TextInput label="Пароль" type="password" autoComplete="current-password" defaultValue="секрет 42" />);
        const field = screen.getByLabelText('Пароль');
        expect(field).toHaveAttribute('type', 'password');
        expect(field).toHaveAttribute('autocomplete', 'current-password');

        await userEvent.click(screen.getByRole('button', { name: 'Показати пароль' }));
        expect(field).toHaveAttribute('type', 'text');
        expect(field).toHaveAttribute('autocomplete', 'current-password');
        expect(field).toHaveValue('секрет 42');
        expect(screen.getByRole('button', { name: 'Сховати пароль' })).toHaveAttribute('aria-pressed', 'true');

        await userEvent.click(screen.getByRole('button', { name: 'Сховати пароль' }));
        expect(field).toHaveAttribute('type', 'password');
    });

    it('works from the keyboard', async () => {
        render(<TextInput label="Пароль" type="password" />);
        await userEvent.tab();
        expect(screen.getByLabelText('Пароль')).toHaveFocus();
        await userEvent.tab();
        expect(screen.getByRole('button', { name: 'Показати пароль' })).toHaveFocus();
        await userEvent.keyboard('{Enter}');
        expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'text');
        await userEvent.keyboard(' ');
        expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'password');
    });

    it('hides the password again once the form is sent', async () => {
        render(
            <form onSubmit={(event) => event.preventDefault()}>
                <TextInput label="Пароль" type="password" />
            </form>,
        );
        await userEvent.click(screen.getByRole('button', { name: 'Показати пароль' }));
        expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'text');
        fireEvent.submit(screen.getByLabelText('Пароль').closest('form')!);
        expect(screen.getByLabelText('Пароль')).toHaveAttribute('type', 'password');
    });

    it('leaves other fields without the eye', () => {
        render(<TextInput label="Нік" />);
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });
});
