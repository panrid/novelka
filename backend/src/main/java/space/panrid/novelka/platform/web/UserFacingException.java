package space.panrid.novelka.platform.web;

import org.springframework.http.HttpStatus;

/**
 * An error whose message is written for the reader in Ukrainian and is safe to show as is.
 * Anything else that escapes a controller is reported with a generic message.
 *
 * <p>{@code reason} is an optional stable code the web app can react to (for example,
 * offer to resend a confirmation email); it is never shown to people.
 */
public class UserFacingException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;

    public UserFacingException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public UserFacingException(HttpStatus status, String message, String reason) {
        super(message);
        this.status = status;
        this.reason = reason;
    }

    public HttpStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }

    public static UserFacingException badRequest(String message) {
        return new UserFacingException(HttpStatus.BAD_REQUEST, message);
    }

    public static UserFacingException conflict(String message) {
        return new UserFacingException(HttpStatus.CONFLICT, message);
    }

    public static UserFacingException notFound(String message) {
        return new UserFacingException(HttpStatus.NOT_FOUND, message);
    }

    /** Another site we depend on (Syosetu, the AI provider) failed. */
    public static UserFacingException badGateway(String message) {
        return new UserFacingException(HttpStatus.BAD_GATEWAY, message);
    }

    public static UserFacingException tooManyRequests() {
        return new UserFacingException(HttpStatus.TOO_MANY_REQUESTS,
                "Забагато спроб. Зачекайте трохи й спробуйте знову.");
    }
}
