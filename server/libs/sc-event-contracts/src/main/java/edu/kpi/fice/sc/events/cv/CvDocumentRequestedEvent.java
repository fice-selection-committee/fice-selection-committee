package edu.kpi.fice.sc.events.cv;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Published by documents-service onto {@code cv.events} with routing key {@code
 * cv.document.requested} after a document upload completes. Consumed by cv-service to trigger the
 * download → preprocess → OCR → field-extract pipeline.
 *
 * @param documentId persistence ID owned by documents-service
 * @param s3Key MinIO/S3 object key (relative, no leading slash)
 * @param documentType matches {@code DocumentType} enum string (passport, ipn, foreign_passport, …)
 * @param traceId W3C trace context for cross-service correlation
 */
public record CvDocumentRequestedEvent(
    @NotNull Long documentId,
    @NotBlank String s3Key,
    @NotBlank String documentType,
    @NotBlank String traceId) {}
