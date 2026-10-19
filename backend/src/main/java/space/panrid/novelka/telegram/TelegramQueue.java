package space.panrid.novelka.telegram;

/**
 * Telegram messages about new chapters waiting to go (план 21.9). In production a schedule
 * sends them at Telegram's pace; tests call it.
 */
public interface TelegramQueue {

    /** Sends what is ready now, until nothing is. Returns how many messages were tried. */
    int drain();
}
