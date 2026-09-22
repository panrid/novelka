package panrid.space.novelka.server.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskFailureMessageTest {
    @Test
    void explainsLegacyDictionaryLimit() {
        String message = TaskFailureMessage.describe(new IllegalStateException("Dictionary tool limit exceeded"));
        assertTrue(message.contains("6"));
        assertTrue(message.contains("словнику"));
        assertTrue(message.contains("відновіть"));
    }
}
