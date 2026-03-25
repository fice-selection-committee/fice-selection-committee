# Phase 4 — Event Architecture Improvements

**Priority:** P2 — Reliability
**Status:** Pending
**Blocked by:** `05-sc-event-contracts-library.md`

## Goal

Centralize RabbitMQ topology, standardize DLQ setup, and ensure all services use shared event contracts.

## Deliverables

### 4.1 — Centralize RabbitMQ Topology Constants

Move all exchange/queue/routing-key constants to `EventConstants` in sc-event-contracts.

**Current locations:**
- `RabbitConfig` in notifications-service — defines 4 exchanges, all queues/bindings
- `RabbitMQConfig` in identity-service — defines audit + identity exchanges
- `RabbitAmqpConfig` in environment-service — defines audit exchange

**After:** Each service's RabbitMQ config references `EventConstants.*` instead of local string literals.

### 4.2 — Unify Event DTOs

Ensure all services import DTOs from sc-event-contracts:
- identity-service publishers use `TemplateType`/`ChannelType` enums (not raw Strings)
- admission-service publishers use shared `ApplicationEventDto`
- All consumers reference the same class

### 4.3 — DLQ Standardization

**Problem:** Identity-service audit queue created without DLQ bindings:
```java
new Queue(AUDIT_QUEUE)  // no dead-letter args
```

**Fix:** Create shared `QueueFactory` or utility in sc-event-contracts:
```java
public static Queue createQueueWithDlq(String queueName, String dlxExchange) {
    return QueueBuilder.durable(queueName)
        .withArgument("x-dead-letter-exchange", dlxExchange)
        .withArgument("x-dead-letter-routing-key", queueName + ".dlq")
        .build();
}
```

Update identity-service to use DLQ-enabled queue creation.

### 4.4 — Outbox Pattern Generalization (OPTIONAL — defer if complex)

Extract outbox from identity-service to sc-common:
- `OutboxEvent` JPA entity
- `OutboxEventRepository`
- `OutboxPublisher` (scheduled, @Retryable)
- `@ConditionalOnProperty("sc.outbox.enabled")`

## Verification
- RabbitMQ queues have consistent DLQ bindings
- No duplicate exchange/queue constant definitions across services
- Message publish → consume works end-to-end for all event types
