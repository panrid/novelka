package space.panrid.novelka.admin;

/**
 * Someone asked for «Автопереклад із сайту» (етап 19). The site owner hears about it and answers in
 * «Користувачі й ролі».
 */
public record AutotranslateRequested(long requesterId, String requesterNick) {
}
