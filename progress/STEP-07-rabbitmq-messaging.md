# STEP-07 — RabbitMQ Consumer & Publisher

**Status**: ✅ DONE — merged at cv polyrepo `92e8a97` (PR #6) + monorepo tracker tick
**Depends on**: STEP-02, STEP-03
**Blocks**: STEP-08

## Goal

Wire CV-Service to RabbitMQ as both consumer (of `cv.document.requested`) and publisher (of `cv.document.parsed` / `cv.document.failed`). Manual acks, idempotency dedup, prefetch tuning to support the 50-docs/min/instance SLO.

## Files to Create

```
server/services/selection-committee-computer-vision/src/cv_service/messaging/
├── __init__.py
├── consumer.py             aio-pika consumer; manual ack; prefetch
├── publisher.py            aio-pika publisher; emits parsed/failed
├── idempotency.py          LRU-based dedup (in-memory; Redis-pluggable later)
├── topology.py             defensive declare on startup
└── types.py                MessageContext (delivery_tag, headers, traceId)
```

```
tests/messaging/
├── __init__.py
├── conftest.py             testcontainers RabbitMQ + topology fixture
├── test_consumer.py
├── test_publisher.py
└── test_idempotency.py
```

`pyproject.toml`: add `aio-pika ^9.4`. Optional `cachetools` for LRU.

## Implementation Outline

### `idempotency.py`
```python
class IdempotencyCache:
    def __init__(self, maxsize: int = 10_000):
        self._seen = LRUCache(maxsize=maxsize)
    def key(self, event: CvDocumentRequestedEvent) -> str:
        return f"{event.documentId}:{event.s3Key}"
    def seen(self, event) -> bool:
        k = self.key(event)
        if k in self._seen: return True
        self._seen[k] = True
        return False
```
Note: in-memory cache is **per-instance**. For multi-instance dedup, swap for Redis in a later step. For now, document the limitation.

### `consumer.py` `CvConsumer`
```python
class CvConsumer:
    def __init__(self, settings: Settings, handler: Callable[[CvDocumentRequestedEvent, MessageContext], Awaitable[None]]):
        self._url = settings.rabbitmq_url
        self._prefetch = settings.rabbit_prefetch
        self._handler = handler
        self._idempotency = IdempotencyCache()

    async def run(self) -> None:
        async with await aio_pika.connect_robust(self._url) as conn:
            ch = await conn.channel(publisher_confirms=True)
            await ch.set_qos(prefetch_count=self._prefetch)
            queue = await ch.get_queue(CV_REQUEST_QUEUE)  # passive: must be declared by definitions.json
            async with queue.iterator() as messages:
                async for msg in messages:
                    await self._dispatch(msg)

    async def _dispatch(self, msg):
        try:
            event = CvDocumentRequestedEvent.model_validate_json(msg.body)
            if self._idempotency.seen(event):
                await msg.ack()
                logger.info("idempotent_skip", documentId=event.documentId)
                return
            ctx = MessageContext(delivery_tag=msg.delivery_tag, headers=msg.headers, traceId=event.traceId)
            await self._handler(event, ctx)
            await msg.ack()
        except ValidationError as e:
            await msg.reject(requeue=False)   # malformed → DLQ via x-dead-letter wiring
        except TransientError:
            await msg.nack(requeue=False)     # routed via dead-letter to retry queues
        except Exception:
            logger.exception("unexpected_handler_error")
            await msg.nack(requeue=False)
```

### `publisher.py` `CvPublisher`
- Publishes to `cv.events` exchange with routing key `cv.document.parsed` or `cv.document.failed`.
- Uses `publisher_confirms=True` so failed publishes raise.
- `traceId` carried in event body AND in `headers["traceparent"]` for OTel propagation.

### `topology.py`
- On startup, **passively** check that `CV_REQUEST_QUEUE`, `CV_RESULT_QUEUE`, `cv.events`, and DLQ exist. If not → log error and fail startup. Do NOT redeclare (that's `definitions.json`'s job).

## Tests (Acceptance Gates)

Use testcontainers RabbitMQ; the test fixture re-declares the cv.* topology fresh on every test (mirrors `infra/rabbitmq/definitions.example.json` exactly so a `nack(requeue=False)` exercises the same DLX → cv.dlq routing the live broker uses).

### `test_consumer.py`
- [x] **Happy path**: publish a `CvDocumentRequestedEvent` to `cv.events:cv.document.requested`. Stub handler awaits a result. Assert: handler invoked exactly once with correct event; message acked (queue empty).
- [x] **Malformed JSON**: publish raw `b"{not json"`. Assert: rejected (no requeue), lands in DLQ; handler NOT invoked.
- [x] **Handler raises TransientError**: stub raises. Assert: nack without requeue → message dead-letters to `cv.dlq` via `cv.dlx`; original queue empty after delivery.
- [x] **Idempotent redelivery**: publish same event twice (same `documentId+s3Key`). Assert: handler invoked once; second delivery acked-and-skipped.
- [x] **Prefetch honored**: publish 16 messages with `prefetch=4` and a slow handler. Assert: at most 4 in-flight at any moment (use a counter under lock); also assert peak ≥ 2 to prove real concurrency was exercised (a sequential-dispatch consumer would trivially pass `≤ 4`).

### `test_publisher.py`
- [x] Publishing `CvDocumentParsedEvent` is observable on `cv.document.results` queue with correct routing key.
- [x] Publishing `CvDocumentFailedEvent` uses the `cv.document.failed` routing key.
- [x] `traceId` present both in body, in AMQP `traceparent` header, and in `correlation_id`.
- [x] Publish-after-close raises a specific `MessagingError` (`pytest.raises(MessagingError, match="publisher is closed")`) — the broader "connection forced closed mid-publish" gate without racing the broker.

### `test_idempotency.py`
- [x] LRU eviction: insert `maxsize+1` keys → first key no longer "seen".
- [x] Different keys = different events: `(42, "a")` and `(42, "b")` are not duplicates.
- [x] First-seen / second-seen invariant on a single key.
- [x] Key format is `documentId:s3Key` only (different `documentType` / `traceId` are still duplicates).
- [x] `maxsize=0` / negative → `ValueError`.

## Definition of Done

- [x] Consumer + publisher + idempotency cache implemented
- [x] Topology check fails fast on missing queues
- [x] All acceptance-gate tests pass — 17/17 in `tests/messaging/`
- [x] `ruff` + `ruff format --check` + `mypy --strict` clean
- [x] `progress/README.md` STEP-07 row marked ✅

## Regressions Caught

- **`testcontainers.rabbitmq` import-time DeprecationWarning** — testcontainers 4.13 still ships a `@wait_container_is_ready` decorator that emits a `DeprecationWarning` the moment we import `testcontainers.rabbitmq`. Combined with cv-service's `filterwarnings = ["error", …]` in `pyproject.toml`, conftest collection blew up with `DeprecationWarning: The @wait_container_is_ready decorator is deprecated …`. Fix: additive entry `"ignore::DeprecationWarning:testcontainers.*"` so a real cv_service warning still fails the suite. Pattern matches the existing paddleocr / paddle / google.protobuf / astor quarantine entries.
- **Sequential-dispatch trivially passes the prefetch gate.** First draft awaited each delivery in the iterator (`async for msg in messages: await self._dispatch(msg)`) — that gives `peak in-flight = 1`, which trivially satisfies "at most 4" without exercising prefetch. Fix: spawn one `asyncio.Task` per delivery. The test was tightened to also assert `peak ≥ 2` so the gate cannot regress to a sequential implementation in the future.
- **`contextlib.suppress(Exception)` swallowed a sentinel `AssertionError`.** First draft of `test_publish_after_close_raises` used `with contextlib.suppress(Exception): … raise AssertionError("must raise")` — the suppress catches `AssertionError` (it inherits from `Exception`), so the test passed regardless of behaviour. Fix: `with pytest.raises(MessagingError, match="publisher is closed"):` — asserts the specific exception we raise.
- **mypy strict on `connect_robust` return.** Mypy treated the type of `await aio_pika.connect_robust(...)` as `AbstractConnection` (the wider abstract base) when used inside `async with await connect_robust(...) as conn:` and refused to assign it to `AbstractRobustConnection`. Fix: bind the awaited result to a local first (`connection = await aio_pika.connect_robust(...)`), then `async with connection as conn:`. The local keeps the inferred robust type for the field assignment.
- **`ASYNC109`: function-level `timeout` parameter.** ruff's async correctness rule flags `async def f(*, timeout: float = 10.0)` and recommends `asyncio.timeout(...)`. Renamed the kw to `timeout_seconds` on `CvConsumer.wait_ready` and the `_wait_for` helper, and switched the internal wait to `async with asyncio.timeout(...): await event.wait()`.

## Notes

- **Connection robustness**: `aio_pika.connect_robust` auto-reconnects. The consumer task must be supervised — FastAPI lifespan handles this in STEP-08.
- **Channel per consumer**: simpler model. If we hit throughput ceilings, revisit channel pooling.
- **`publisher_confirms`** is non-negotiable: silent message loss is worse than a slow publish.
- **Topology is owned by the broker**, not by cv-service. `verify_topology` is a passive declare only; redeclaring would risk a slow drift between the Java services' view of cv.events / cv.dlx and ours.
- **Dedup is per-instance.** STEP-10 (documents-service `ocr_results` upsert) handles persistence-side idempotency across replicas.
