package edu.kpi.fice.sc.common.exception;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import edu.kpi.fice.sc.common.dto.ErrorResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
public abstract class BaseGlobalExceptionHandler {

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {

    String message = "Invalid JSON format";
    if (ex.getRootCause() instanceof JsonParseException) {
      message = "Malformed JSON: " + ex.getRootCause().getMessage();
      log.warn("JSON parse error at [{}]: {}", request.getRequestURI(), message);
    } else if (ex.getRootCause() instanceof InvalidFormatException ife) {
      String fieldName = ife.getPath().getLast().getFieldName();
      message = "Invalid format for field '" + fieldName + "'";
      log.warn("Invalid format for field '{}' at [{}]", fieldName, request.getRequestURI());
    } else {
      log.warn("Unreadable HTTP message at [{}]: {}", request.getRequestURI(), ex.getMessage());
    }

    return ResponseEntity.badRequest()
        .body(new ErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI()));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleResourceNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {

    log.warn("Resource not found at [{}]: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());

    return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> handleValidationException(
      ValidationException ex, HttpServletRequest request) {

    log.info("Custom validation failed at [{}]: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());

    errorResponse.setFieldErrors(ex.getErrors());

    return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {

    log.warn("Method not supported: {} for {}", ex.getMethod(), request.getRequestURI());

    String supportedMethods = String.join(", ", Objects.requireNonNull(ex.getSupportedMethods()));

    String message =
        String.format(
            "HTTP method '%s' is not supported for this endpoint. Supported methods: %s",
            ex.getMethod(), supportedMethods);

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, message, request.getRequestURI());

    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {

    log.warn("Media type not supported: {} at [{}]", ex.getContentType(), request.getRequestURI());

    String message =
        "Content type '"
            + ex.getContentType()
            + "' is not supported. Please use 'application/json'";

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, message, request.getRequestURI());

    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorResponse);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ErrorResponse> handleMissingHeaderException(
      MissingRequestHeaderException ex, HttpServletRequest request) {

    log.warn("Missing header '{}' at [{}]", ex.getHeaderName(), request.getRequestURI());

    String message = String.format("Required header '%s' is missing", ex.getHeaderName());

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());

    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameterException(
      MissingServletRequestParameterException ex, HttpServletRequest request) {

    log.warn("Missing parameter '{}' at [{}]", ex.getParameterName(), request.getRequestURI());

    String message = String.format("Required parameter '%s' is missing", ex.getParameterName());

    Map<String, String> fieldErrors = Map.of(ex.getParameterName(), message);

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    errorResponse.setFieldErrors(fieldErrors);

    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {

    log.info("Validation failed at [{}]: {}", request.getRequestURI(), ex.getMessage());

    BindingResult bindingResult = ex.getBindingResult();

    Map<String, String> fieldErrors =
        bindingResult.getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    error ->
                        Optional.ofNullable(error.getDefaultMessage())
                            .orElse("Validation failed for field '" + error.getField() + "'"),
                    (existing, replacement) -> existing + "; " + replacement));

    List<String> globalErrors =
        bindingResult.getGlobalErrors().stream()
            .map(ObjectError::getDefaultMessage)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (fieldErrors.isEmpty() && globalErrors.isEmpty()) {
      List<ObjectError> allErrors = bindingResult.getAllErrors();
      if (!allErrors.isEmpty()) {
        globalErrors =
            allErrors.stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
      }
    }

    String errorMessage =
        !globalErrors.isEmpty() ? String.join("; ", globalErrors) : "Validation failed";

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.BAD_REQUEST, errorMessage, request.getRequestURI());

    if (!fieldErrors.isEmpty()) errorResponse.setFieldErrors(fieldErrors);
    if (!globalErrors.isEmpty()) errorResponse.setGlobalErrors(globalErrors);

    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatchException(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {

    log.warn(
        "Type mismatch at [{}]: param '{}', expected type '{}', value '{}'",
        request.getRequestURI(),
        ex.getName(),
        ex.getRequiredType(),
        ex.getValue());

    String message = String.format("%s must be a valid %s", ex.getName(), ex.getRequiredType());

    Map<String, String> fieldErrors = Map.of(ex.getName(), message);

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.BAD_REQUEST, message, request.getRequestURI());

    errorResponse.setFieldErrors(fieldErrors);

    return ResponseEntity.badRequest().body(errorResponse);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
      EntityNotFoundException ex, HttpServletRequest request) {

    log.warn("Entity not found at [{}]: {}", request.getRequestURI(), ex.getMessage());

    ErrorResponse errorResponse =
        new ErrorResponse(
            HttpStatus.NOT_FOUND, "The requested entity was not found", request.getRequestURI());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
      DataIntegrityViolationException ex, HttpServletRequest request) {

    String rootMessage = ex.getMostSpecificCause().getMessage();
    log.error("Data integrity violation at [{}]: {}", request.getRequestURI(), rootMessage);

    String message = "A database constraint was violated";

    if (rootMessage.toLowerCase().contains("duplicate key")) {
      message = "Duplicate value violates unique constraint";
    } else if (rootMessage.toLowerCase().contains("null value")) {
      message = "Null value violates not-null constraint";
    } else if (rootMessage.toLowerCase().contains("foreign key")) {
      message = "Foreign key constraint violation";
    }

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse(HttpStatus.CONFLICT, message, request.getRequestURI()));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleMaxUploadSizeException(
      MaxUploadSizeExceededException ex, HttpServletRequest request) {

    log.warn("Upload too large at [{}]: {}", request.getRequestURI(), ex.getMessage());

    String message =
        String.format("File size exceeds the maximum allowed limit - %s", ex.getMessage());

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, message, request.getRequestURI());

    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFoundException(
      NoHandlerFoundException ex, HttpServletRequest request) {

    log.warn("No handler found for [{} {}]", ex.getHttpMethod(), ex.getRequestURL());

    String message =
        String.format("Endpoint not found: %s %s", ex.getHttpMethod(), ex.getRequestURL());

    ErrorResponse errorResponse =
        new ErrorResponse(HttpStatus.NOT_FOUND, message, request.getRequestURI());

    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      org.springframework.security.access.AccessDeniedException ex, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorResponse(HttpStatus.FORBIDDEN, "Access denied", request.getRequestURI()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGenericException(
      Exception ex, HttpServletRequest request) {

    log.error("Unhandled exception at [{}]: {}", request.getRequestURI(), ex.getMessage(), ex);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred",
                request.getRequestURI()));
  }
}
