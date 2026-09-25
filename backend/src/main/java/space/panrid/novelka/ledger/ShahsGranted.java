package space.panrid.novelka.ledger;

/** The site owner gave someone шаги. */
public record ShahsGranted(long accountId, int shah, String note, long grantedBy) {
}
