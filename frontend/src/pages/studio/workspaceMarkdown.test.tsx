import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { WorkspaceMarkdown } from './WorkspaceMarkdown';

describe('workspace Markdown', () => {
    it('renders formatting and navigable wiki links without interpreting HTML', async () => {
        const open = vi.fn();
        render(<WorkspaceMarkdown text={'# Канон\n\n**Правило** та [[План|До плану]]\n<script>alert(1)</script>\n[Небезпека](javascript:alert(1))'} open={open} image={() => undefined} />);
        expect(screen.getByRole('heading', { name: 'Канон' })).toBeInTheDocument();
        expect(screen.getByText('Правило').tagName).toBe('STRONG');
        await userEvent.click(screen.getByRole('button', { name: 'До плану' }));
        expect(open).toHaveBeenCalledWith('План');
        expect(document.querySelector('script')).toBeNull();
        expect(document.querySelector('a[href^="javascript:"]')).toBeNull();
    });
    it('renders an embedded attachment through its protected URL and leaves code inert', () => {
        const open = vi.fn();
        render(<WorkspaceMarkdown text={'![[map.png]]\n```\n[[Не переходити]]\n```'} open={open} image={() => '/media/protected.png'} />);
        expect(screen.getByRole('img', { name: 'map.png' })).toHaveAttribute('src', '/media/protected.png');
        expect(screen.queryByRole('button')).toBeNull();
    });
});
