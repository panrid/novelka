package space.panrid.novelka.achievement;

/**
 * Someone earned a badge; the title and what it is for are in Ukrainian, as the profile shows them.
 * The badge's picture is {@code /badges/<code>.svg} (and a .jpg card for Telegram).
 */
public record AchievementEarned(long accountId, String nick, String code, String title, String description) {
}
