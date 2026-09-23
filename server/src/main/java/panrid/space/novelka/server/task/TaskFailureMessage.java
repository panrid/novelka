package panrid.space.novelka.server.task;

public final class TaskFailureMessage {
    private TaskFailureMessage() {
    }

    public static String describe(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) return "Операцію перервано. Перевірте стан job і журнал сервера.";
        if (message.equals("Dictionary tool limit exceeded")) {
            return "ШІ використав забагато пошуків у словнику: попередній запуск зупинився на ліміті 6. "
                    + "Тепер після ліміту модель завершує відповідь із наявним контекстом. "
                    + "Натисніть «Відновити» в черзі та підтвердьте бюджет; відновіть збережений прогрес без обов’язкових змін словника.";
        }
        if (message.equals("Tool round limit exceeded")) {
            return "ШІ не завершив відповідь після кількох уточнень словника. "
                    + "Доповніть словник потрібними даними та відновіть переклад за ID job.";
        }
        return error instanceof IllegalArgumentException || error instanceof IllegalStateException
                ? message : "Операцію перервано. Перевірте стан job і журнал сервера.";
    }
}
