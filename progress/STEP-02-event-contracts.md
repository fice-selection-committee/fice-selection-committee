# STEP-02 — Event Contracts (sc-event-contracts → 1.3.3)

**Status**: ⏳ TODO
**Depends on**: STEP-01
**Blocks**: STEP-07, STEP-10

## Goal

Define the canonical wire format for the three CV events on **both** the Java side (shared library `sc-event-contracts`) and the Python side (Pydantic models in `cv_service/events/`). Update RabbitMQ topology to declare the proper exchange/queues/DLQ. Bump shared lib version 1.3.2 → 1.3.3 and republish.

## Files to Create

### Java side (`server/libs/sc-event-contracts/`)
```
src/main/java/edu/kpi/fice/sc/events/cv/
├── CvDocumentRequestedEvent.java
├── CvDocumentParsedEvent.java
└── CvDocumentFailedEvent.java
```
```
src/test/java/edu/kpi/fice/sc/events/cv/
└── CvEventSerializationTest.java
```

### Python side (`server/services/selection-committee-computer-vision/`)
```
src/cv_service/events/
├── __init__.py
├── requested.py     CvDocumentRequestedEvent (Pydantic)
├── parsed.py        CvDocumentParsedEvent
├── failed.py        CvDocumentFailedEvent
└── constants.py     CV_EVENTS_EXCHANGE, queue names, routing keys
```
```
tests/events/
├── __init__.py
├── samples/
│   ├── requested.json
│   ├── parsed.json
│   └── failed.json
└── test_contract_parity.py
```

## Files to Modify

- `server/libs/sc-event-contracts/src/main/java/edu/kpi/fice/sc/events/constants/EventConstants.java` — append:
  ```java
  public static final String CV_EVENTS_EXCHANGE = "cv.events";
  public static final String CV_REQUEST_QUEUE = "cv.document.requested";
  public static final String CV_RESULT_QUEUE = "cv.document.results";
  public static final String CV_DLQ = "cv.dlq";
  public static final String CV_REQUESTED_ROUTING_KEY = "cv.document.requested";
  public static final String CV_PARSED_ROUTING_KEY = "cv.document.parsed";
  public static final String CV_FAILED_ROUTING_KEY = "cv.document.failed";
  ```
- `server/version.properties` — `1.3.2` → `1.3.3`
- `server/gradle/libs.versions.toml` — bump `sc-libs` to `1.3.3`
- `infra/rabbitmq/definitions.json` — REPLACE the placeholder `cv.tasks` queue. New topology:
  - Topic exchange `cv.events` (durable)
  - Queue `cv.document.requested` bound to `cv.events` with routing key `cv.document.requested`; `x-dead-letter-exchange = cv.dlx`, `x-dead-letter-routing-key = cv.dead`
  - Queue `cv.document.results` bound to `cv.events` with routing key `cv.document.parsed.#` and `cv.document.failed.#`
  - Topic exchange `cv.dlx` (durable) with `cv.dlq` queue bound to `cv.dead`
  - Retry-delay queues `cv.retry.5s`, `cv.retry.30s`, `cv.retry.5m` with `x-message-ttl` and dead-letter back to `cv.events` (used by STEP-08)
- All consumer services (documents-service, others) — bump `sc-event-contracts` dep to `1.3.3` after publish

## Implementation Outline

### Java records (Java 21)
```java
public record CvDocumentRequestedEvent(
    @NotNull Long documentId,
    @NotBlank String s3Key,
    @NotBlank String documentType,   // matches DocumentType enum string
    @NotBlank String traceId
) {}

public record CvDocumentParsedEvent(
    @NotNull Long documentId,
    @NotNull Map<String,String> fields,
    double confidence,               // 0.0 — 1.0
    @NotBlank String traceId
) {}

public record CvDocumentFailedEvent(
    @NotNull Long documentId,
    @NotBlank String error,
    boolean retriable,
    @NotBlank String traceId
) {}
```

### Python mirrors (Pydantic v2)
```python
class CvDocumentRequestedEvent(BaseModel):
    documentId: int
    s3Key: str
    documentType: str
    traceId: str

class CvDocumentParsedEvent(BaseModel):
    documentId: int
    fields: dict[str, str]
    confidence: float
    traceId: str

class CvDocumentFailedEvent(BaseModel):
    documentId: int
    error: str
    retriable: bool
    traceId: str
```

Field names use **camelCase** (Java DTO style) — Pydantic config: `populate_by_name=True`, `alias_generator` not needed if we mirror exactly. JSON in/out is identical to Java Jackson output.

### Sample JSON (committed for parity tests)
```json
// requested.json
{"documentId":42,"s3Key":"documents/passports/abc123.png","documentType":"passport","traceId":"00-abc...01"}
```

### Build & publish
```bash
cd server
./gradlew :sc-event-contracts:clean :sc-event-contracts:test :sc-event-contracts:publishToMavenLocal
./gradlew checkScLibsVersion
```

## Tests (Acceptance Gates)

- [ ] `./gradlew :sc-event-contracts:test` — Jackson round-trip for all 3 records (object → JSON → object equality)
- [ ] `pytest tests/events/test_contract_parity.py` — for each sample JSON: Pydantic `model_validate(json)` succeeds; `model_dump_json()` produces stable output; field set matches `set(JavaRecord.RECORD_COMPONENTS)` (read from a checked-in `events/expected_fields.json`)
- [ ] **Topology test** (manual + scripted): bring up RabbitMQ container, apply `definitions.json`, run `rabbitmqadmin list exchanges` and `list queues` — assert `cv.events`, `cv.dlx`, `cv.document.requested`, `cv.document.results`, `cv.dlq`, `cv.retry.5s`, `cv.retry.30s`, `cv.retry.5m` all exist with expected bindings
- [ ] All existing `sc-event-contracts` tests still green (no regression)
- [ ] `./gradlew checkScLibsVersion` passes
- [ ] After bumping consumer services to 1.3.3, `./gradlew build` from each service builds cleanly

## Definition of Done

- [ ] Java records added with validation annotations
- [ ] EventConstants extended
- [ ] Python Pydantic models added
- [ ] Sample JSON fixtures committed and parity test green
- [ ] RabbitMQ `definitions.json` updated; old `cv.tasks` removed
- [ ] Library version bumped to 1.3.3 and `publishToMavenLocal` ran
- [ ] All consumer service `build.gradle` files updated to 1.3.3
- [ ] `progress/README.md` STEP-02 row marked ✅
