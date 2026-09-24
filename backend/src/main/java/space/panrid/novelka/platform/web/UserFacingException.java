package space.panrid.novelka.platform.web;

import org.springframework.http.HttpStatus;

/**
 * An error whose message is written for the reader in Ukrainian and is safe to show as is.
 * Anything else that escapes a controller is reported with a generic message.
 */
public class UserFacingException extends RuntimeException {

    private final HttpStatus status;

    public UserFacingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
