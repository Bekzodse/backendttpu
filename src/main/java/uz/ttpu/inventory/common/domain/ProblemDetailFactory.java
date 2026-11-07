package uz.ttpu.inventory.common.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

public final class ProblemDetailFactory {

    private static final String PROBLEM_BASE_URI = "https://api.ttpu.uz/problems/";

    private ProblemDetailFactory() {
    }

    public static ProblemDetail create(
            ErrorCode errorCode,
            String detail,
            Locale locale,
            URI instance
    ) {
        String effectiveDetail = (detail != null && !detail.isBlank())
                ? detail
                : errorCode.resolveMessage(locale);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), effectiveDetail);
        problem.setType(URI.create(PROBLEM_BASE_URI + errorCode.name().toLowerCase(Locale.ROOT).replace('_', '-')));
        problem.setTitle(errorCode.getTitle());
        if (instance != null) {
            problem.setInstance(instance);
        }
        Map<String, String> messages = errorCode.toLocalizedMessagesMap();
        problem.setProperty("errorCode", errorCode.name());
        problem.setProperty("localizedMessage", errorCode.resolveMessage(locale));
        problem.setProperty("messages", messages);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }

    public static ResponseEntity<ProblemDetail> toResponseEntity(
            ErrorCode errorCode,
            String detail,
            Locale locale,
            URI instance
    ) {
        ProblemDetail problem = create(errorCode, detail, locale, instance);
        return ResponseEntity
                .status(errorCode.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
