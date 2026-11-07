package uz.ttpu.inventory.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import uz.ttpu.inventory.common.domain.ClearanceChecklistBlockedException;
import uz.ttpu.inventory.common.domain.DataWipeRequiredException;
import uz.ttpu.inventory.common.domain.DeviceRevokedException;
import uz.ttpu.inventory.common.domain.ErrorCode;
import uz.ttpu.inventory.common.domain.ProblemDetailFactory;
import uz.ttpu.inventory.common.domain.ResourceNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleResourceNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_RESOURCE_NOT_FOUND,
        ex.getMessage(),
        request.getLocale(),
        resolveInstance(request));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationErrors(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetailFactory.create(
            ErrorCode.ERR_VALIDATION_FAILED,
            "Request validation failed",
            request.getLocale(),
            resolveInstance(request));
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    problem.setProperty("errors", fieldErrors);
    return ResponseEntity.status(ErrorCode.ERR_VALIDATION_FAILED.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_BAD_REQUEST, ex.getMessage(), request.getLocale(), resolveInstance(request));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ProblemDetail> handleIllegalState(
      IllegalStateException ex, HttpServletRequest request) {
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_STATE_CONFLICT,
        ex.getMessage(),
        request.getLocale(),
        resolveInstance(request));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ProblemDetail> handleBadCredentials(
      BadCredentialsException ex, HttpServletRequest request) {
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_AUTH_FAILED, ex.getMessage(), request.getLocale(), resolveInstance(request));
  }

  @ExceptionHandler({LockedException.class, DisabledException.class})
  public ResponseEntity<ProblemDetail> handleLockedOrDisabled(
      RuntimeException ex, HttpServletRequest request) {
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_ACCOUNT_LOCKED,
        ex.getMessage(),
        request.getLocale(),
        resolveInstance(request));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_ACCESS_DENIED,
        ex.getMessage(),
        request.getLocale(),
        resolveInstance(request));
  }

  @ExceptionHandler(ClearanceChecklistBlockedException.class)
  public ResponseEntity<ProblemDetail> handleClearanceChecklistBlocked(
      ClearanceChecklistBlockedException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetailFactory.create(
            ErrorCode.ERR_CLEARANCE_CHECKLIST_BLOCKED,
            ex.getMessage(),
            request.getLocale(),
            resolveInstance(request));
    problem.setProperty("checklistNumber", ex.getChecklistNumber());
    problem.setProperty("remainingItemsCount", ex.getRemainingItemsCount());
    problem.setProperty("unreturnedItems", ex.getUnreturnedItemSummaries());
    return ResponseEntity.status(ErrorCode.ERR_CLEARANCE_CHECKLIST_BLOCKED.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(DataWipeRequiredException.class)
  public ResponseEntity<ProblemDetail> handleDataWipeRequired(
      DataWipeRequiredException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetailFactory.create(
            ErrorCode.ERR_DATA_WIPE_REQUIRED,
            ex.getMessage(),
            request.getLocale(),
            resolveInstance(request));
    problem.setProperty("itemId", ex.getItemId());
    problem.setProperty("inventoryNumber", ex.getInventoryNumber());
    problem.setProperty("categoryName", ex.getCategoryName());
    return ResponseEntity.status(ErrorCode.ERR_DATA_WIPE_REQUIRED.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(DeviceRevokedException.class)
  public ResponseEntity<ProblemDetail> handleDeviceRevoked(
      DeviceRevokedException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ProblemDetailFactory.create(
            ErrorCode.ERR_DEVICE_REVOKED,
            ex.getMessage(),
            request.getLocale(),
            resolveInstance(request));
    problem.setProperty("deviceId", ex.getDeviceId());
    return ResponseEntity.status(ErrorCode.ERR_DEVICE_REVOKED.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGenericException(
      Exception ex, HttpServletRequest request) {
    return ProblemDetailFactory.toResponseEntity(
        ErrorCode.ERR_INTERNAL_SERVER_ERROR,
        "An unexpected internal error occurred.",
        request.getLocale(),
        resolveInstance(request));
  }

  private URI resolveInstance(HttpServletRequest request) {
    if (request == null || request.getRequestURI() == null || request.getRequestURI().isBlank()) {
      return null;
    }
    return URI.create(request.getRequestURI());
  }
}
