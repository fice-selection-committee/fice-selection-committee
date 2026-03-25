package edu.kpi.fice.sc.common.exception;

import java.util.Map;
import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
  private final Map<String, String> errors;

  public ValidationException(String message, Map<String, String> errors) {
    super(message);
    this.errors = errors;
  }
}
