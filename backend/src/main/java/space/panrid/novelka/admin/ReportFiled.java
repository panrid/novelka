package space.panrid.novelka.admin;

/**
 * Someone reported a thing. {@code what} says it for people: «Переклад «Маг води», глава 12».
 * The site's moderators, administrators and owner hear about it.
 */
public record ReportFiled(long reporterId, String target, long targetId, String what, String reason) {
}
