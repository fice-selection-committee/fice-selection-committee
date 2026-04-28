# STEP-02 — Event Contracts (sc-event-contracts → 1.3.3)

**Status**: ✅ DONE (2026-04-28)
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

- [x] `./gradlew :libs:sc-event-contracts:test` — Jackson round-trip for all 3 records (5 tests, all green)
- [x] `pytest tests/events/test_contract_parity.py` — Pydantic decodes canonical Java JSON, field-set parity, camelCase enforcement, Unicode preservation, blank-string rejection (7 tests, all green)
- [x] **Topology test**: started RabbitMQ container, applied `definitions.json`, listed exchanges/queues/bindings — `cv.events` + `cv.dlx` exchanges present, `cv.document.requested`/`cv.document.results`/`cv.dlq`/`cv.retry.{5s,30s,5m}` queues present, all bindings (`cv.events → cv.document.requested` on `cv.document.requested`, `cv.events → cv.document.results` on both `cv.document.parsed.#` and `cv.document.failed.#`, `cv.dlx → cv.dlq` on `cv.dead`) verified
- [x] All existing `sc-event-contracts` tests still green (no regression — only new test class added)
- [ ] `./gradlew checkScLibsVersion` — task does not exist on `main` yet (planned for a later step); manually verified all 7 consumer `libs.versions.toml` files updated
- [x] documents-service `compileJava` succeeds against sc-libs 1.3.3 (verified in this session)

## Definition of Done

- [x] Java records added with validation annotations (`@NotNull`, `@NotBlank`)
- [x] EventConstants extended (CV_EVENTS_EXCHANGE, CV_DLX, queues, retry queues, routing keys)
- [x] Python Pydantic models added (camelCase, `extra="forbid"`, `frozen=True`)
- [x] Sample JSON fixtures committed and parity test green
- [x] RabbitMQ `definitions.json` updated; placeholder `cv.tasks` queue removed; full DLX + retry topology added
- [x] Library version bumped to 1.3.3 and `publishToMavenLocal` ran
- [x] All 7 consumer service `gradle/libs.versions.toml` files updated to 1.3.3 (identity, admission, documents, environment, notifications, gateway, telegram-bot)
- [x] `progress/README.md` STEP-02 row marked ✅

## Regressions Caught

1. **`sc-event-contracts` had no `src/test/` directory and no test deps.** The library only declared `api libs.jackson.databind`. Added `testImplementation libs.spring.boot.starter.test`, `testImplementation libs.assertj.core`, `testRuntimeOnly libs.junit.platform.launcher` and a `useJUnitPlatform()` block to `build.gradle`. Future shared-lib changes can now extend the existing test source set.
2. **Validation annotations needed `jakarta.validation-api`.** The library carried zero validation annotations on existing event DTOs. Added `jakarta-validation-api` to `libs.versions.toml` (Spring-Boot-BOM-managed, no version pin) and exposed it as `api` from sc-event-contracts so consumers see the annotations transitively.
3. **Spotless reformatted records & test on first `:check` run.** Google Java Format wraps long Javadoc and tightens whitespace. Ran `spotlessApply` once to lock the canonical form before commit.
4. **Polyrepo discovery — `infra/` is its own repo.** The mission's polyrepo split table did not list `infra/` as a polyrepo for STEP-02, but the `.gitignore` excludes `infra/*` (with narrow exceptions). The `infra/rabbitmq/definitions.json` change therefore lands in the **selection-committee-infra** polyrepo, not the monorepo. Recorded so future steps with infra changes do not assume the monorepo is the destination.
5. **No `checkScLibsVersion` Gradle task on `main`.** Pre-flight notes warned this. Verified each consumer's per-service `libs.versions.toml` manually (`grep 'sc-libs = "1.3'` after edit); CI drift detection deferred to whenever that task lands.
6. **RabbitMQ `rabbitmqadmin` requires explicit `--username=`/`--password=` flags.** Passing `-u`/`-p` short flags interpreted them as the `node` arg (URL form) and produced a confusing "Action … not understood" error. Documented for future infra topology validation steps.
