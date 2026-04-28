package edu.kpi.fice.sc.events.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Published by cv-service onto {@code cv.events} with routing key {@code cv.document.parsed} after
 * a successful OCR + field-extract run. Consumed by documents-service to persist the OCR result and
 * surface it to the frontend for auto-fill.
 *
 * @param documentId mirrors the value from the original {@link CvDocumentRequestedEvent}
 * @param fields extractor-named values, e.g. {@code {"surname":"Шевченко","ipn":"1234567890"}}
 * @param confidence aggregate confidence score in {@code [0.0, 1.0]}
 * @param traceId W3C trace context for cross-service correlation
 */
public record CvDocumentParsedEvent(
    @NotNull Long documentId,
    @NotNull Map<String, String> fields,
    double confidence,
    @NotBlank String traceId) {}
