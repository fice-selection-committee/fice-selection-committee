package edu.kpi.fice.sc.events.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Published by cv-service onto {@code cv.events} with routing key {@code cv.document.failed} when
 * the pipeline cannot produce a parsed result. Consumed by documents-service to record the failure
 * and decide whether retry/manual review is appropriate.
 *
 * @param documentId mirrors the value from the original {@link CvDocumentRequestedEvent}
 * @param error short stable error code (e.g. {@code "ocr.timeout"}, {@code
 *     "preprocess.unsupported_format"})
 * @param retriable whether documents-service should re-publish via the retry-delay queues
 * @param traceId W3C trace context for cross-service correlation
 */
public record CvDocumentFailedEvent(
    @NotNull Long documentId,
    @NotBlank String error,
    boolean retriable,
    @NotBlank String traceId) {}
