export function describeTaskFailure(message: string) {
    if (message === 'Dictionary tool limit exceeded' || message.startsWith('ШІ перевищив ліміт пошуків у словнику')
        || message.startsWith('ШІ використав забагато пошуків у словнику')) return {
        title: 'Попередній запуск зупинився на ліміті пошуків',
        detail: 'Раніше запит понад 6 пошуків у словнику зупиняв етап. Тепер після ліміту ШІ завершує відповідь із наявним контекстом. Натисніть «Відновити» в картці завдання й підтвердьте бюджет. Доповнювати словник лише через цю помилку не потрібно.',
    };
    if (message === 'Tool round limit exceeded') return {
        title: 'ШІ не завершив уточнення словника',
        detail: 'Доповніть словник потрібними даними та відновіть переклад за ID job. Уже завершені частини не буде втрачено.',
    };
    if (message.includes('OpenRouter HTTP 401')) return {
        title: 'OpenRouter не прийняв API-ключ',
        detail: 'Перевірте OPENROUTER_API_KEY на сервері, після чого створіть нове завдання або відновіть цей job.',
    };
    if (message.includes('OpenRouter HTTP 403')) return {
        title: 'OpenRouter заборонив запит',
        detail: 'Перевірте доступ API-ключа і моделі в кабінеті OpenRouter. Результат цього запиту може бути невідомим, тому спершу перегляньте витрати.',
    };
    if (message.includes('OpenRouter HTTP 429')) return {
        title: 'OpenRouter тимчасово обмежив запити',
        detail: 'Зачекайте, поки ліміт провайдера оновиться, а потім відновіть переклад за ID job.',
    };
    if (message.includes('Uncertain previous request')) return {
        title: 'Результат попереднього запиту невідомий',
        detail: 'Він уже міг коштувати грошей. Перегляньте витрати, а потім відновіть job і позначте дозвіл на повтор uncertain-запиту лише за потреби.',
    };
    if (message.startsWith('Dictionary changed') || message.startsWith('Словник змінився після цього перекладу')) return {
        title: 'Потрібна нова ревізія перекладу',
        detail: 'Словник змінився після перекладу цієї глави, тому попереднє завдання не відновлюється. У майстерні відкрийте «Додаткові операції» → «Повторно вичитати одну главу», виберіть цю главу й задайте бюджет. Попередній текст лишається доступним читачам.',
    };
    return { title: 'Деталі', detail: message };
}
