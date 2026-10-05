package space.panrid.novelka.achievement;

/** Someone earned a badge; the title is in Ukrainian, as the profile shows it. */
public record AchievementEarned(long accountId, String nick, String code, String title) {
}
