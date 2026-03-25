# Phase 2.2 — sc-event-contracts Shared Library

**Priority:** P1 — Fixes critical DTO drift bug
**Status:** Pending
**Blocked by:** `02-buildsrc-convention-plugins.md`

## Goal

Create shared event contracts to prevent publisher/consumer DTO drift.

## Critical Bug Being Fixed

`NotificationEventDto` in identity-service (publisher) uses:
```java
String templateType;
List<String> channels;
```

While notifications-service (consumer) expects:
```java
TemplateType templateType;  // enum
List<ChannelType> channels; // enum
```

This causes silent deserialization failures or data mismatches at runtime.

## Package Structure

```
edu.kpi.fice.sc.events/
├── dto/
│   ├── NotificationEventDto.java
│   ├── ApplicationEventDto.java
│   └── AuditEventDto.java
├── enums/
│   ├── ChannelType.java
│   └── TemplateType.java
└── constants/
    └── EventConstants.java  (exchange names, queue names, routing keys)
```

## EventConstants Content

Centralize from RabbitConfig (notifications), RabbitMQConfig (identity), RabbitAmqpConfig (environment):
- Exchange names: `events`, `audit.events`, `identity.events`, `notifications.dlx`
- Queue names: `notifications.email`, `notifications.telegram`, `cv.tasks`, `admission.events`, `identity.queue`, `audit.queue`
- Routing key patterns: `admission.application.*`, `identity.audit.*`

## Build File

- Apply `sc.library-conventions`
- Dependencies: Jackson annotations, Jakarta validation annotations only (NO Spring)

## Service Updates

1. **identity-service**: Delete local NotificationEventDto, import from sc-event-contracts, use TemplateType/ChannelType enums
2. **admission-service**: Delete local ApplicationEventDto, import from sc-event-contracts
3. **notifications-service**: Delete local DTOs/enums, import from sc-event-contracts
4. **environment-service**: Delete local AuditEventDto, import from sc-event-contracts

## Verification
- `./gradlew :libs:sc-event-contracts:build` succeeds
- All publisher/consumer pairs use the same DTO classes
- RabbitMQ message serialization/deserialization works end-to-end
