# STEP-07 — RabbitMQ Consumer & Publisher

**Status**: ⏳ TODO
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

Use testcontainers RabbitMQ; apply `definitions.json` from `infra/rabbitmq/` as part of fixture setup.

### `test_consumer.py`
- [ ] **Happy path**: publish a `CvDocumentRequestedEvent` to `cv.events:cv.document.requested`. Stub handler awaits a result. Assert: handler invoked exactly once with correct event; message acked (queue empty).
- [ ] **Malformed JSON**: publish raw `b"{not json"`. Assert: rejected (no requeue), lands in DLQ; handler NOT invoked.
- [ ] **Handler raises TransientError**: stub raises. Assert: nack without requeue → DLX routes to retry queue; original queue empty after delivery.
- [ ] **Idempotent redelivery**: publish same event twice (same `documentId+s3Key`). Assert: handler invoked once; second delivery acked-and-skipped.
- [ ] **Prefetch honored**: publish 16 messages with `prefetch=4` and a slow handler. Assert: at most 4 in-flight at any moment (use a counter under lock).

### `test_publisher.py`
- [ ] Publishing `CvDocumentParsedEvent` is observable on `cv.document.results` queue with correct routing key.
- [ ] `traceId` present both in body and in AMQP headers.
- [ ] Publisher confirms: connection forced closed mid-publish → exception raised (caller can retry).

### `test_idempotency.py`
- [ ] LRU eviction: insert `maxsize+1` keys → first key no longer "seen".
- [ ] Different keys = different events: `(42, "a")` and `(42, "b")` are not duplicates.

## Definition of Done

- [ ] Consumer + publisher + idempotency cache implemented
- [ ] Topology check fails fast on missing queues
- [ ] All 9 tests pass
- [ ] `ruff` + `mypy --strict` clean
- [ ] `progress/README.md` STEP-07 row marked ✅

## Notes

- **Connection robustness**: `aio_pika.connect_robust` auto-reconnects. Document that the consumer task must be supervised (FastAPI lifespan handles this in STEP-08).
- **Channel per consumer**: simpler model. If we hit throughput ceilings, revisit channel pooling.
- **`publisher_confirms`** is non-negotiable: silent message loss is worse than a slow publish.
