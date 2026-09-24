package space.panrid.novelka.platform.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns failures into {@link ProblemDetail} with a human message in {@code detail}.
 * Spring's own errors (404, 405, bad JSON…) get Ukrainian texts too; stack traces and
 * internal exception messages never reach the browser.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler
    ProblemDetail userFacing(UserFacingException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(error.status(), error.getMessage());
        if (error.reason() != null) {
            problem.setProperty("reason", error.reason());
        }
        return problem;
    }

    @ExceptionHandler
    ProblemDetail unexpected(Exception error) {
        log.error("Unhandled API error", error);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, messageFor(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** Bean Validation on request bodies: show the first broken rule's own message. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = error.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .findFirst()
                .orElse(messageFor(status));
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
        problem.setDetail(messageFor(status));
        return super.handleExceptionInternal(error, problem, headers, status, request);
    }

    static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Запит має помилку. Оновіть сторінку й спробуйте ще раз.";
            case 401 -> "Увійдіть, щоб продовжити.";
            case 403 -> "Це вам недоступно.";
            case 404 -> "Такої сторінки немає.";
            case 405 -> "Така дія тут недоступна.";
            case 413 -> "Файл завеликий.";
            case 415 -> "Такий формат не підтримується.";
            case 429 -> "Забагато спроб. Зачекайте трохи й спробуйте знову.";
            default -> "Щось пішло не так. Спробуйте ще раз трохи пізніше.";
        };
    }
}
