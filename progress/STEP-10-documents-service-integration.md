# STEP-10 — Documents-Service Integration (Java)

**Status**: ⏳ TODO
**Depends on**: STEP-02 (event contracts), STEP-08 (CV pipeline working)
**Blocks**: STEP-11

## Goal

Wire the Java side: documents-service publishes `CvDocumentRequestedEvent` after a document is confirmed-uploaded, consumes the parsed/failed result events, persists results in a new `ocr_results` table, and exposes `GET /api/v1/documents/{id}/ocr` for the frontend.

## Files to Create

```
server/services/selection-committee-documents-service/src/main/java/edu/kpi/fice/documents_service/
├── service/cv/
│   ├── CvRequestPublisher.java
│   ├── CvResultListener.java
│   └── OcrResultService.java
├── repository/
│   └── OcrResultRepository.java
├── domain/
│   └── OcrResult.java                  (JPA entity)
├── dto/
│   └── OcrResultDto.java
└── controller/
    └── OcrController.java              (or extend DocumentController)
```

```
src/main/resources/db/migration/
└── V2__ocr_results.sql
```

```
src/test/java/edu/kpi/fice/documents_service/service/cv/
├── CvRequestPublisherTest.java
└── CvResultListenerTest.java

src/integrationTest/java/edu/kpi/fice/documents_service/cv/
├── CvIntegrationTest.java                  (Testcontainers Postgres + RabbitMQ)
└── contract/OcrControllerContractTest.java
```

## Files to Modify

- `selection-committee-documents-service/build.gradle` — bump `sc-event-contracts` to `1.3.3` (post STEP-02)
- `service/DocumentService.java` — at end of `confirm(Long id)` method (after status flips to `UPLOADED`), if `documentType ∈ {PASSPORT, IPN, FOREIGN_PASSPORT}` → call `cvRequestPublisher.publish(document)`
- `controller/DocumentController.java` — add endpoint `GET /api/v1/documents/{id}/ocr` (or place in dedicated `OcrController`)

## Implementation Outline

### V2 migration
```sql
CREATE TABLE documents_service.ocr_results (
    id              BIGSERIAL PRIMARY KEY,
    document_id     BIGINT       NOT NULL UNIQUE REFERENCES documents_service.documents(id) ON DELETE CASCADE,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING','PARSED','FAILED')),
    fields          JSONB,
    confidence      NUMERIC(4,3),
    error_reason    TEXT,
    retriable       BOOLEAN,
    trace_id        VARCHAR(64),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ocr_results_status ON documents_service.ocr_results(status);
```

### `CvRequestPublisher`
```java
@Service
@RequiredArgsConstructor
public class CvRequestPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final TracingContext tracing;       // existing helper

    public void publish(Document document) {
        var event = new CvDocumentRequestedEvent(
            document.getId(),
            document.getLink(),
            document.getType().name().toLowerCase(),
            tracing.currentTraceId()
        );
        rabbitTemplate.convertAndSend(
            EventConstants.CV_EVENTS_EXCHANGE,
            EventConstants.CV_REQUESTED_ROUTING_KEY,
            event
        );
        // Insert PENDING row immediately so frontend polling has something to display
        ocrResultService.upsertPending(document.getId(), event.traceId());
    }
}
```

### `CvResultListener`
```java
@Component
@RequiredArgsConstructor
public class CvResultListener {
    private final OcrResultService ocrService;
    private final ApplicationEventPublisher auditPublisher;

    @RabbitListener(queues = EventConstants.CV_RESULT_QUEUE,
                    containerFactory = "rabbitListenerContainerFactory")
    public void onResult(Message message,
                         @Header(name="amqp_receivedRoutingKey") String routingKey) {
        if (routingKey.startsWith("cv.document.parsed")) {
            var event = deserialize(message, CvDocumentParsedEvent.class);
            ocrService.markParsed(event);
        } else if (routingKey.startsWith("cv.document.failed")) {
            var event = deserialize(message, CvDocumentFailedEvent.class);
            ocrService.markFailed(event);
            auditPublisher.publishEvent(new AuditEventDto("cv.document.failed", ...));
        }
    }
}
```

### `OcrResultService.markParsed/markFailed` — UPSERT semantics

### `OcrController`
```java
@RestController
@RequestMapping("/api/v1/documents/{id}/ocr")
@RequiredArgsConstructor
public class OcrController {
    private final OcrResultService ocrService;
    private final DocumentService documentService;

    @GetMapping
    @PreAuthorize("@documentSecurity.canRead(#id, authentication)")
    public ResponseEntity<OcrResultDto> get(@PathVariable Long id) {
        documentService.requireReadable(id);
        return ocrService.findByDocumentId(id)
            .map(r -> ResponseEntity.ok(OcrResultDto.from(r)))
            .orElse(ResponseEntity.notFound().build());
    }
}
```

## Tests (Acceptance Gates)

### Unit tests
- [ ] `CvRequestPublisherTest` — given a document of type PASSPORT → asserts `rabbitTemplate.convertAndSend` called with correct exchange/routing/payload + `ocrResultService.upsertPending` called.
- [ ] `CvResultListenerTest` — for parsed routing key → calls `markParsed`. For failed routing key → calls `markFailed` + publishes audit event. For unknown routing key → no-op + log.

### Integration test (`CvIntegrationTest` — Testcontainers Postgres + RabbitMQ)
- [ ] **Publish-on-confirm**: upload a document → call `confirm(id)` → assert message lands on `cv.document.requested` queue with correct payload and `ocr_results` row has `status=PENDING`.
- [ ] **Consume parsed**: publish a mock `cv.document.parsed` event for an existing document → assert `ocr_results` row updated with `status=PARSED`, `fields` JSONB matches, `confidence` numeric correct.
- [ ] **Consume failed (retriable=false)**: → row updated to `FAILED` with `error_reason`, `retriable=false`. Audit event published to `audit.events` exchange.
- [ ] **Idempotent listener**: deliver same parsed event twice → only one final state, no duplicate audit.
- [ ] **Non-CV-eligible doc type**: confirm a `OTHER`-type document → no message sent to CV exchange (assert queue is empty).

### Contract test (`OcrControllerContractTest`)
- [ ] `GET /api/v1/documents/{id}/ocr` for an existing PARSED record → 200 + JSON body matches schema.
- [ ] For PENDING → 200 + body with `status=PENDING`, `fields=null`.
- [ ] For unknown id → 404.
- [ ] Without auth → 401.
- [ ] As a different applicant → 403.

### Regression
- [ ] All existing documents-service tests still green (run `./gradlew :selection-committee-documents-service:test :selection-committee-documents-service:integrationTest`).

## Definition of Done

- [ ] V2 Flyway migration applied cleanly on a fresh DB and on an upgraded DB
- [ ] Publisher + listener + controller wired
- [ ] All unit + integration + contract tests pass
- [ ] No regression in existing documents-service tests
- [ ] `progress/README.md` STEP-10 row marked ✅

## Notes

- **Why the listener lives in documents-service** (not admission-service): documents-service owns the `documents` table; the OCR result is a per-document attribute. Per the bounded-context rule, the OCR data belongs here.
- **`document_id UNIQUE`**: enforced at DB level so we always UPSERT the latest result. CV-Service's idempotency key + this constraint together guarantee no duplicate rows.
- **Audit event** on failure is per FLOW-15 audit-trail requirement — every CV failure is a flagged event.
- **Don't add a Feign client to CV-Service**: CV is event-only by design (per the master plan's architectural boundary).
