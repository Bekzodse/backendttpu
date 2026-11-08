package uz.ttpu.inventory.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uz.ttpu.inventory.common.api.GlobalExceptionHandler;
import uz.ttpu.inventory.common.domain.ErrorCode;
import uz.ttpu.inventory.common.domain.ProblemDetailFactory;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;

class Rfc7807ProblemDetailAndExceptionTest {

  @Test
  void allErrorCodesProvideUzbekRussianAndEnglishTranslationsAndProblemDetailProperties() {
    for (ErrorCode code : ErrorCode.values()) {
      assertThat(code.getMessageUz()).isNotBlank();
      assertThat(code.getMessageRu()).isNotBlank();
      assertThat(code.getMessageEn()).isNotBlank();

      assertThat(code.resolveMessage(Locale.forLanguageTag("uz-UZ")))
          .isEqualTo(code.getMessageUz());
      assertThat(code.resolveMessage(Locale.forLanguageTag("ru-RU")))
          .isEqualTo(code.getMessageRu());
      assertThat(code.resolveMessage(Locale.forLanguageTag("en-US")))
          .isEqualTo(code.getMessageEn());

      ProblemDetail pd =
          ProblemDetailFactory.create(
              code, null, Locale.forLanguageTag("ru"), URI.create("/api/v1/test"));
      assertThat(pd.getStatus()).isEqualTo(code.getStatus().value());
      assertThat(pd.getTitle()).isEqualTo(code.getTitle());
      assertThat(pd.getProperties()).containsEntry("errorCode", code.name());
      assertThat(pd.getProperties()).containsEntry("localizedMessage", code.getMessageRu());

      @SuppressWarnings("unchecked")
      Map<String, String> messages = (Map<String, String>) pd.getProperties().get("messages");
      assertThat(messages)
          .containsEntry("uz", code.getMessageUz())
          .containsEntry("ru", code.getMessageRu())
          .containsEntry("en", code.getMessageEn());
    }
  }

  @Test
  void globalExceptionHandlerMapsStandardExceptionsToRfc7807ProblemJson() {
    GlobalExceptionHandler handler = new GlobalExceptionHandler();
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/assets/123");
    req.addPreferredLocale(Locale.forLanguageTag("en-US"));

    ResponseEntity<ProblemDetail> notFound =
        handler.handleResourceNotFound(
            new ResourceNotFoundException("Item", UUID.randomUUID()), req);
    assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(notFound.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(notFound.getBody().getProperties())
        .containsEntry("errorCode", "ERR_RESOURCE_NOT_FOUND");

    ResponseEntity<ProblemDetail> badReq =
        handler.handleIllegalArgument(new IllegalArgumentException("Invalid parameter"), req);
    assertThat(badReq.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(badReq.getBody().getProperties()).containsEntry("errorCode", "ERR_BAD_REQUEST");

    ResponseEntity<ProblemDetail> conflict =
        handler.handleIllegalState(new IllegalStateException("State conflict"), req);
    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(conflict.getBody().getProperties()).containsEntry("errorCode", "ERR_STATE_CONFLICT");

    ResponseEntity<ProblemDetail> unauth =
        handler.handleBadCredentials(new BadCredentialsException("Invalid credentials"), req);
    assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(unauth.getBody().getProperties()).containsEntry("errorCode", "ERR_AUTH_FAILED");

    ResponseEntity<ProblemDetail> locked =
        handler.handleLockedOrDisabled(new LockedException("Account locked"), req);
    assertThat(locked.getStatusCode().value()).isEqualTo(423);
    assertThat(locked.getBody().getProperties()).containsEntry("errorCode", "ERR_ACCOUNT_LOCKED");

    ResponseEntity<ProblemDetail> forbidden =
        handler.handleAccessDenied(new AccessDeniedException("Access denied"), req);
    assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(forbidden.getBody().getProperties()).containsEntry("errorCode", "ERR_ACCESS_DENIED");

    ResponseEntity<ProblemDetail> noResource =
        handler.handleNoResourceFound(
            new NoResourceFoundException(
                org.springframework.http.HttpMethod.GET, "/api/v1/missing"),
            req);
    assertThat(noResource.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(noResource.getBody().getProperties())
        .containsEntry("errorCode", "ERR_RESOURCE_NOT_FOUND");

    ResponseEntity<ProblemDetail> methodNotAllowed =
        handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("POST"), req);
    assertThat(methodNotAllowed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(methodNotAllowed.getBody().getProperties())
        .containsEntry("errorCode", "ERR_BAD_REQUEST");

    ResponseEntity<ProblemDetail> mediaTypeNotSupported =
        handler.handleMediaTypeNotSupported(
            new HttpMediaTypeNotSupportedException("Unsupported media type"), req);
    assertThat(mediaTypeNotSupported.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mediaTypeNotSupported.getBody().getProperties())
        .containsEntry("errorCode", "ERR_BAD_REQUEST");
  }
}
