package edu.kpi.fice.sc.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.http.HttpStatus;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
  private LocalDateTime timestamp;
  private int status;
  private String error;
  private String message;
  private String path;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, String> fieldErrors;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private List<String> globalErrors;

  public ErrorResponse(HttpStatus status, String message, String path) {
    this.timestamp = LocalDateTime.now();
    this.status = status.value();
    this.error = status.getReasonPhrase();
    this.message = message;
    this.path = path;
  }

  public void setFieldErrors(Map<String, String> fieldErrors) {
    this.fieldErrors = fieldErrors.isEmpty() ? null : fieldErrors;
  }

  public void setGlobalErrors(List<String> globalErrors) {
    this.globalErrors = globalErrors.isEmpty() ? null : globalErrors;
  }
}
