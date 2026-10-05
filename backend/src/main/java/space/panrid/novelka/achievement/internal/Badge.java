package space.panrid.novelka.achievement.internal;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * One badge: earned when a count reaches its goal. The list order is the order on the profile.
 */
record Badge(String code, String title, String description, ToIntFunction<Achievements.Stats> count, int goal) {

    static final List<Badge> ALL = List.of(
            new Badge("reader_10", "Перші сторінки", "Прочитати 10 глав", Achievements.Stats::read, 10),
            new Badge("reader_100", "Книголюб", "Прочитати 100 глав", Achievements.Stats::read, 100),
            new Badge("reader_1000", "Ненаситний читач", "Прочитати 1 000 глав", Achievements.Stats::read, 1_000),
            new Badge("comment_1", "Перше слово", "Залишити коментар", Achievements.Stats::comments, 1),
            new Badge("comment_50", "Душа обговорень", "Залишити 50 коментарів", Achievements.Stats::comments, 50),
            new Badge("suggestion_1", "Пильне око", "Правку прийняла команда перекладу", Achievements.Stats::accepted, 1),
            new Badge("suggestion_25", "Коректор", "25 прийнятих правок", Achievements.Stats::accepted, 25),
            new Badge("critic_5", "Критик", "Оцінити 5 перекладів", Achievements.Stats::ratings, 5),
            new Badge("voter_10", "Голос читачів", "Проголосувати за 10 новел у «Що перекласти»", Achievements.Stats::votes, 10),
            new Badge("scout", "Шукач скарбів", "Запропонована вами новела знайшла перекладача", Achievements.Stats::scouted, 1),
            new Badge("translator_1", "Перекладач", "Опублікувати першу главу свого перекладу", Achievements.Stats::translated, 1),
            new Badge("translator_100", "Сто глав", "Опублікувати 100 глав перекладу", Achievements.Stats::translated, 100));

    boolean earned(Achievements.Stats stats) {
        return count.applyAsInt(stats) >= goal;
    }
}
