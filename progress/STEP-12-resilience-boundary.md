# STEP-12 — Resilience at the Boundary

**Status**: ⏳ TODO
**Depends on**: STEP-10, STEP-11
**Blocks**: STEP-13

## Goal

Ensure CV-Service downtime never breaks document upload. Documents-service must publish CV requests as best-effort with a circuit breaker, and the frontend must degrade gracefully (no error toasts, no blocked UI). No message loss when CV is restarted.

## Files to Create / Modify

### Documents-service (Java)
```
selection-committee-documents-service/src/main/java/edu/kpi/fice/documents_service/
├── config/
│   └── CvResilienceConfig.java                NEW — Resilience4j config for cvPublisher
└── service/cv/
    └── CvRequestPublisher.java                MODIFY — wrap in @CircuitBreaker(name="cvPublisher", fallbackMethod=...)
```

### Documents-service tests
```
src/integrationTest/java/.../cv/
├── CvPublisherCircuitBreakerTest.java         NEW
└── CvUploadResilienceTest.java                NEW
```

### Frontend
```
client/web/src/hooks/
└── use-document-ocr.ts                         MODIFY — handle UNAVAILABLE state after 60s
client/web/src/components/documents/
└── ocr-result-card.tsx                         MODIFY — UNAVAILABLE branch already wired in STEP-11; verify
```

### Frontend tests
```
client/web/tests/unit/
└── use-document-ocr-unavailable.test.ts       NEW

client/web/tests/e2e/
└── cv-ocr-fallback.spec.ts                     NEW — chaos test
```

## Implementation Outline

### `CvResilienceConfig.java`
```java
@Configuration
public class CvResilienceConfig {
    @Bean
    public CircuitBreakerConfig cvPublisherConfig() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .slowCallRateThreshold(50.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(2))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slidingWindow(SlidingWindowType.COUNT_BASED, 10, 10)
            .recordExceptions(AmqpException.class, ConnectException.class, TimeoutException.class)
            .build();
    }
    @Bean
    public CircuitBreakerRegistry cvCircuitBreakerRegistry(CircuitBreakerConfig cfg) {
        return CircuitBreakerRegistry.of(cfg);
    }
}
```

### `CvRequestPublisher.java` modifications
```java
@CircuitBreaker(name = "cvPublisher", fallbackMethod = "publishFallback")
public void publish(Document document) {
    rabbitTemplate.convertAndSend(...);
    ocrResultService.upsertPending(document.getId(), traceId);
}

@SuppressWarnings("unused")
public void publishFallback(Document document, Throwable t) {
    log.warn("CV publish skipped — circuit open: documentId={}", document.getId(), t);
    metrics.cvPublishSkipped().increment();
    // Do NOT throw — upload must succeed
}
```

### Frontend timeout in `use-document-ocr.ts`
```ts
const startedAt = useRef<number>();
useEffect(() => {
  if (data?.status === 'PENDING' && !startedAt.current) startedAt.current = Date.now();
}, [data]);

const isStuck = data?.status === 'PENDING'
             && startedAt.current != null
             && Date.now() - startedAt.current > 60_000;

return { ...query, status: isStuck ? 'UNAVAILABLE' : (data?.status ?? 'PENDING') };
```

## Tests (Acceptance Gates)

### `CvPublisherCircuitBreakerTest` (Testcontainers Postgres only — no RabbitMQ)
- [ ] Inject a stub `RabbitTemplate` that throws `AmqpException`. Call `publish()` 10 times → asserts circuit opens after 5 failures.
- [ ] After open: 6th call → fallback invoked, no exception propagated, metric `cv_publish_skipped_total` incremented.
- [ ] After `waitDurationInOpenState` → half-open → 3 successful calls close the breaker.

### `CvUploadResilienceTest` (Testcontainers Postgres + RabbitMQ; manually stop the RMQ container mid-test)
- [ ] Upload doc + confirm → CV publish succeeds.
- [ ] Stop RabbitMQ container → confirm next document → upload still succeeds (200 OK), `documents` row created with status UPLOADED, `ocr_results` row NOT created (because publish fell back).
- [ ] Restart RabbitMQ → next confirm publishes successfully again (breaker closes).

### `use-document-ocr-unavailable.test.ts` (Vitest fake timers)
- [ ] Advance clock 65s while data stays PENDING → hook returns `status='UNAVAILABLE'`.
- [ ] When data flips to PARSED before 60s → status reflects PARSED, no UNAVAILABLE transition.

### `cv-ocr-fallback.spec.ts` (Playwright)
- [ ] In docker-compose: `docker stop cv-service` mid-test.
- [ ] Upload a passport → the upload succeeds (no toast, no error).
- [ ] OCR card shows PENDING then transitions to UNAVAILABLE after 60s with muted "OCR temporarily unavailable" copy. **No error toast.**
- [ ] `docker start cv-service` → existing pending RabbitMQ message consumed → after the user re-renders the page, OCR card shows PARSED. **(No message loss.)**

### Chaos / queue durability
- [ ] After `docker stop cv-service`, `cv.document.requested` queue depth grows. After `docker start`, queue drains. Assert via RabbitMQ HTTP API.

## Definition of Done

- [ ] Circuit breaker config + fallback implemented
- [ ] Frontend handles 60s timeout gracefully
- [ ] All 4 backend integration tests green
- [ ] All 2 frontend tests green (1 unit + 1 E2E)
- [ ] Chaos test: zero message loss across CV restart
- [ ] `progress/README.md` STEP-12 row marked ✅

## Notes

- The breaker only wraps the **publish** call, not the listener. The listener is already protected by RabbitMQ's at-least-once delivery + DLQ.
- The 60s timeout is a UX decision: it's the moment the user sees "OCR unavailable" instead of an indefinite spinner. It does NOT affect the backend pipeline — the actual OCR can complete much later (queue may backfill once CV restarts).
- Consider pre-warming the breaker on application start (call once at startup); deferred — over-engineering for now.
