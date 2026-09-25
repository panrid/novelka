import type { ReactNode } from 'react';
import { Dialog, Heading, Modal, ModalOverlay } from 'react-aria-components';
import styles from './sheet.module.css';

/** A panel that slides up from the bottom (a dialog on wide screens); Esc or a tap outside closes it. */
export function Sheet({ open, onClose, title, children, tall = false }: {
    open: boolean; onClose: () => void; title: string; children: ReactNode; tall?: boolean;
}) {
    return (
        <ModalOverlay className={styles.overlay} isOpen={open} onOpenChange={(value) => !value && onClose()} isDismissable>
            <Modal className={styles.modal}>
                <Dialog className={`${styles.sheet} ${tall ? styles.tall : ''}`}>
                    <Heading slot="title" className={styles.sheetTitle}>{title}</Heading>
                    {children}
                </Dialog>
            </Modal>
        </ModalOverlay>
    );
}
