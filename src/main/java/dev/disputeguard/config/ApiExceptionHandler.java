package dev.disputeguard.config;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/** Turns common failures into clear JSON errors instead of a bare 500. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", String.valueOf(e.getMessage())));
    }

    /** Anything unexpected: log the stack trace and return the cause, so the client sees what broke. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> unexpected(Exception e) {
        // Spring's own errors (404 from ResponseStatusException, 400 from validation, ...) keep their status.
        if (e instanceof org.springframework.web.ErrorResponse er) {
            return ResponseEntity.status(er.getStatusCode()).body(Map.of(
                    "error", String.valueOf(er.getBody().getDetail())));
        }
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Unhandled error", e);
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()),
                "details", root.getClass().getName() + ": " + String.valueOf(root.getMessage())));
    }

    /** A PayPal (or LLM) API call failed: pass its status and body through so the cause is visible. */
    @ExceptionHandler(RestClientResponseException.class)
    ResponseEntity<Map<String, String>> upstream(RestClientResponseException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                "error", "Upstream API returned " + e.getStatusCode().value(),
                "details", e.getResponseBodyAsString()));
    }
}
