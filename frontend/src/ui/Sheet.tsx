import type { ReactNode } from 'react';
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components';
import styles from './sheet.module.css';

/**
 * A panel that slides up from the bottom (a dialog on wide screens); Esc or a tap outside closes it.
 * {@code peek}: half the screen over a page that stays undimmed, to see a change as it is made.
 */
export function Sheet({ open, onClose, title, children, tall = false, peek = false }: {
    open: boolean; onClose: () => void; title: string; children: ReactNode; tall?: boolean; peek?: boolean;
}) {
    return (
        <ModalOverlay className={`${styles.overlay} ${peek ? styles.peekOverlay : ''}`} isOpen={open} onOpenChange={(value) => !value && onClose()} isDismissable>
            <Modal className={styles.modal}>
                <Dialog className={`${styles.sheet} ${tall ? styles.tall : ''} ${peek ? styles.peek : ''}`}>
                    <Heading slot="title" className={styles.sheetTitle}>{title}</Heading>
                    {children}
                </Dialog>
            </Modal>
        </ModalOverlay>
    );
}
