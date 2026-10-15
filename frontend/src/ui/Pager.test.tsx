import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Pager } from './Pager';

describe('the pages of a long list', () => {
    it('shows the pages around the current one and jumps to any of them', async () => {
        const onPage = vi.fn();
        render(<Pager page={20} total={830} size={20} onPage={onPage} range={(page) => `глави ${page * 20 - 19}–${page * 20}`} />);
        const numbers = screen.getAllByRole('button', { name: /^Сторінка/ }).map((button) => button.textContent);
        expect(numbers).toEqual(['1', '19', '20', '21', '42']);
        expect(screen.getByRole('button', { name: 'Сторінка 20, глави 381–400' })).toHaveAttribute('aria-current', 'page');
        await userEvent.click(screen.getByRole('button', { name: /^Сторінка 42/ }));
        expect(onPage).toHaveBeenLastCalledWith(42);
        await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Перейти до сторінки' }), 'сторінка 7 з 42 · глави 121–140');
        expect(onPage).toHaveBeenLastCalledWith(7);
    });

    it('shows every page when there are few', () => {
        render(<Pager page={1} total={60} size={20} onPage={() => {}} />);
        expect(screen.getAllByRole('button', { name: /^Сторінка/ })).toHaveLength(3);
        expect(screen.queryByRole('combobox')).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Попередня сторінка' })).toBeDisabled();
    });
});
