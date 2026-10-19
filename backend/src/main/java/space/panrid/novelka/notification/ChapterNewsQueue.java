package space.panrid.novelka.notification;

/**
 * News of new chapters for subscribers (план 21.9): publishing a chapter queues one job, a
 * worker tells the subscribers batch by batch. In production a schedule runs it; tests call it.
 */
public interface ChapterNewsQueue {

    /** One batch of the oldest ready job; false when nothing is ready. */
    boolean runOne();

    /** Batches until nothing is ready. Returns how many ran. */
    default int drain() {
        int ran = 0;
        while (runOne()) {
            ran++;
        }
        return ran;
    }
}
