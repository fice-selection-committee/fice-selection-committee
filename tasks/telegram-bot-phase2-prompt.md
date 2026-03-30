# Telegram Bot Redesign — Phases 2-4 Implementation Prompt

## Context

You are continuing the redesign of the Telegram bot service at:
`server/services/selection-committee-telegram-bot-service/`

**Phase 1 is COMPLETE.** The following was already implemented and is working (full build passes):

### Phase 1 Completed Work

1. **i18n Infrastructure**
   - `config/MessageSourceConfig.java` — Spring `ResourceBundleMessageSource`
   - `i18n/BotMessageResolver.java` — locale-aware message resolution (EN/UK)
   - `resources/messages/messages_en.properties` — 80+ English message keys
   - `resources/messages/messages_uk.properties` — full Ukrainian translation

2. **Database Layer**
   - `resources/db/migration/V1__create_bot_users.sql` — `telegram_bot.bot_users` table (own schema in shared PostgreSQL)
   - `domain/BotUser.java` — JPA entity (telegramUserId PK, chatId, languageCode, role, subscribed, createdAt, lastActiveAt)
   - `repository/BotUserRepository.java` — JPA repository with `findAllBySubscribedTrue()`
   - `service/BotUserService.java` — user resolution (auto-create on first interaction), language update, subscription toggle
   - Dependencies added to `build.gradle`: JPA, PostgreSQL, Flyway, Testcontainers PostgreSQL, H2
   - `application.yml` updated: datasource, JPA (validate + PostgreSQLDialect), Flyway (schema: telegram_bot)
   - `docker-compose.services.yml` updated: added PostgreSQL dependency for bot service

3. **Inline Keyboard Support**
   - `dto/BotResponse.java` — record with text + keyboard + editMessageId (replaces raw String returns)
   - `keyboard/InlineKeyboardBuilder.java` — fluent builder with `button()`, `row()`, `navRow()`, `build()`
   - `service/TelegramApiClient.java` — enhanced with `sendMessageWithKeyboard()`, `editMessage()`, `answerCallbackQuery()`, `sendResponse()`

4. **Callback Query System**
   - `service/handler/CallbackHandler.java` — interface: `supports(String module)`, `handle(callbackData, userId, chatId, messageId, locale)`
   - `service/CallbackQueryDispatcher.java` — routes by module prefix from callback data
   - `api/TelegramWebhookController.java` — handles both text messages and callback queries, resolves user locale via BotUserService

5. **Callback Handlers (Main Menu + Modules)**
   - `handler/MenuCallbackHandler.java` — main menu with 4 buttons (Flags, Health, Notifications, Settings)
   - `handler/FlagsCallbackHandler.java` — paginated flag list (PAGE_SIZE=5), flag detail view, toggle with inline keyboard confirmation
   - `handler/SettingsCallbackHandler.java` — language switching (EN/UK) and profile view
   - `handler/NotificationsCallbackHandler.java` — subscription toggle via inline keyboard
   - `handler/HealthCallbackHandler.java` — service health monitoring via actuator endpoints (hardcoded internal URLs)

6. **Refactored Existing Command Handlers** (all now return `BotResponse` and use i18n)
   - `handler/BotCommandHandler.java` — interface: `supports(String command)`, `handle(String[] args, Locale locale)` → returns `BotResponse`
   - `service/BotCommandDispatcher.java` — `dispatch(text, chatId, userId, locale)` → returns `BotResponse`. `/start` renders main menu via `MenuCallbackHandler`
   - All handlers: `ListFlagsHandler`, `ViewFlagHandler`, `SearchFlagsHandler`, `ToggleFlagHandler`, `SubscribeHandler`, `UnsubscribeHandler`
   - `service/FlagChangeNotificationListener.java` — i18n-aware, sends per user's language to DB-subscribed users + legacy file-based subscribers

7. **Tests** — 21 test files, all passing. JaCoCo 80% coverage verified. Spotless formatting clean.

### Callback Data Schema (already implemented)
```
menu:main                     → Main menu
flags:list:{page}             → Paginated flag list
flags:view:{key}              → Flag detail
flags:toggle:{key}            → Toggle confirmation prompt
flags:toggle_confirm:{key}    → Execute toggle
flags:toggle_cancel:{key}     → Cancel toggle
flags:noop                    → No-op (pagination label)
health:services               → Service health check
notif:list                    → Notification settings
notif:toggle                  → Toggle subscription
settings:main                 → Settings menu
settings:lang                 → Language menu
settings:lang_en              → Set English
settings:lang_uk              → Set Ukrainian
settings:profile              → User profile
nav:home                      → Go to main menu
```

### Architecture Facts
- Service port: 8087
- Spring Boot 3.5.6, Java 21, Spring Cloud 2025.0.0
- Feign client to environment-service for feature flags
- RabbitMQ for audit event notifications
- Shared PostgreSQL database, own schema `telegram_bot`
- Docker network `sc-net`, container name `sc-telegram-bot`
- Google Java Format via Spotless, Error Prone, Lombok, JaCoCo 80% min
- Each service is a standalone Gradle project with own `settings.gradle` (Groovy)
- Shared libs at `server/libs/` — must run `./gradlew publishToMavenLocal` from `server/` before building if libs changed

---

## Task: Implement Phases 2-4

Read `CLAUDE.md` and all referenced docs before starting. Follow TDD protocol from `docs/claude/testing.md`.

### Phase 2: Core UX Improvements (Priority: HIGH)

#### 2.1 Navigation State Manager
Create `service/NavigationStateManager.java`:
- Per-chat breadcrumb stack (last 10 screens) using `ConcurrentHashMap<Long, Deque<String>>`
- `push(chatId, callbackData)`, `pop(chatId)` (for Back), `reset(chatId)` (for Home)
- Wire into callback handlers so "Back" button actually goes to previous screen instead of hardcoded targets
- Currently Back buttons have hardcoded targets (e.g., `flags:list:0` → `menu:main`). Replace with dynamic `nav:back` where appropriate

#### 2.2 Fix FlagsCallbackHandler Toggle Button Label
In `FlagsCallbackHandler.handleView()`, the Toggle button has a broken `.replace()` call:
```java
.button("\uD83D\uDD04 " + msg.msg(locale, "flags.toggle.btn.confirm")
    .replace(msg.msg(locale, "flags.toggle.btn.confirm"), "Toggle"), ...)
```
This always shows "Toggle" instead of the localized text. Fix: add a new i18n key `flags.view.btn.toggle` with EN="Toggle" / UK="Перемкнути" and use it directly.

#### 2.3 Deep Link Support
Handle Telegram deep links in `/start` command:
- Parse `?start=flag_<key>` → show flag detail directly
- In `BotCommandDispatcher`, when text is `/start flag_dark-mode`, extract key and render flag detail view

#### 2.4 Command Menu Registration
Create a `@PostConstruct` method or `ApplicationReadyEvent` listener that calls Telegram's `setMyCommands` API to register the slash commands in Telegram's menu:
```json
[
  {"command": "start", "description": "Main menu"},
  {"command": "flags", "description": "List feature flags"},
  {"command": "flag", "description": "View flag details"},
  {"command": "search", "description": "Search flags"},
  {"command": "toggle", "description": "Toggle a flag"},
  {"command": "settings", "description": "Bot settings"},
  {"command": "help", "description": "Show help"}
]
```

#### 2.5 NotificationsCallbackHandler Enhancement
Current `handleList` shows a static button. Enhance to show current subscription status:
- If subscribed: show "🔔 Subscribed ✓" with an unsubscribe button
- If not subscribed: show "🔕 Not subscribed" with a subscribe button
- Requires reading user subscription state from `BotUserService`

### Phase 3: Security & Operations (Priority: HIGH)

#### 3.1 RBAC Implementation
Create `security/BotAuthorizationService.java`:
- `canToggleFlags(userId)` — requires role `operator` or `admin`
- `canViewAudit(userId)` — requires role `operator` or `admin`
- Wire into `FlagsCallbackHandler.handleTogglePrompt()` and `ToggleFlagHandler.handle()` — return unauthorized message if not allowed
- Add i18n key: `flags.toggle.unauthorized` (already exists in message files)

#### 3.2 Rate Limiting
Create `security/RateLimitService.java`:
- Per-chat: 5 requests/second (config already exists at `telegram.rate-limit.max-requests-per-second`)
- Per-user write: 1 toggle per minute
- Use `ConcurrentHashMap` with timestamps
- Wire into `TelegramWebhookController` — reject excess requests silently (return 200 OK but don't process)

#### 3.3 Micrometer Metrics
Add counters to track bot usage:
- `bot.commands.total` (tags: command, status=success|error)
- `bot.callbacks.total` (tags: action)
- `bot.users.active` gauge
- Wire `MeterRegistry` into `BotCommandDispatcher` and `CallbackQueryDispatcher`
- Dependency `micrometer-core` is already available via `sc-observability-starter`

#### 3.4 Audit Logging
Create structured audit log entries for write operations:
- When a flag is toggled: log userId, flagKey, oldState, newState, timestamp
- When subscription changes: log userId, action
- Use SLF4J structured logging (MDC or JSON markers)
- Consider emitting to RabbitMQ `bot.audit.events` exchange for persistence

#### 3.5 Circuit Breaker for Feign Client
Add Resilience4j circuit breaker around `EnvironmentServiceClient` calls:
- Add `resilience4j-spring-boot3` dependency (check `libs.versions.toml` first)
- Configure in `application.yml`: sliding window 10, failure rate 50%, wait 30s
- Fallback: return cached last-known flag list for reads, return error message for writes

### Phase 4: Polish & Reliability (Priority: MEDIUM)

#### 4.1 Migrate NotificationChatRegistry to DB
Replace file-based `NotificationChatRegistry` (uses `data/subscriptions.json`) with DB-backed subscriptions:
- `BotUser.subscribed` field already exists in the DB
- `FlagChangeNotificationListener` already queries `botUserService.getSubscribedUsers()`
- Remove the legacy file-based `NotificationChatRegistry` if all consumers are migrated
- Or keep it as a fallback for chats without a registered user (group chats)
- Update `SubscribeHandler` and `UnsubscribeHandler` to use `BotUserService.toggleSubscription()` instead of `NotificationChatRegistry`

#### 4.2 Custom Telegram Health Indicator
Create `health/TelegramHealthIndicator.java` implementing `HealthIndicator`:
- Call `https://api.telegram.org/bot{token}/getMe` to verify token is valid
- Return UP/DOWN based on response
- Wire into Spring Actuator health endpoint

#### 4.3 HealthCallbackHandler Improvements
Current health handler has hardcoded service URLs. Improve:
- Make service URLs configurable via `application.yml` properties
- Add timeout (2 seconds) per health check
- Show response time for each service
- Add "last checked" timestamp

#### 4.4 Error Recovery in TelegramApiClient
- Add retry logic for Telegram API 429 (rate limit) responses: exponential backoff, 3 attempts
- Add specific error logging for common failure modes (network timeout, 403 forbidden, etc.)

#### 4.5 Message Length Safety
Telegram has a 4096 character limit per message. Add safety:
- In `TelegramApiClient.sendMessage()`, check length before sending
- If over limit, truncate with "... (truncated)" suffix
- Or split into multiple messages

---

## Implementation Rules

1. **Read `CLAUDE.md` and `docs/claude/` before starting** — mandatory
2. **TDD**: Write failing test first, then implement, then verify
3. **Build chain**: Run `./gradlew publishToMavenLocal` from `server/` if libs changed
4. **Formatting**: Run `./gradlew spotlessApply` before committing
5. **Coverage**: Maintain 80% JaCoCo minimum per file
6. **Full build**: `./gradlew build` from service directory must pass (includes spotless + tests + coverage)
7. **i18n**: ALL user-facing strings must go through `BotMessageResolver`, no hardcoded text
8. **Backward compatible**: Slash commands must continue working alongside inline keyboards

## Build & Test Commands
```bash
# From server/ (only if shared libs changed):
./gradlew publishToMavenLocal

# From server/services/selection-committee-telegram-bot-service/:
./gradlew build                    # Full build (spotless + compile + test + coverage)
./gradlew test                     # Unit tests only
./gradlew spotlessApply            # Auto-format
./gradlew test --tests "*.ClassName"  # Single test class
```

## Execution Order
Implement in this order:
1. Phase 2.2 (quick fix)
2. Phase 2.5 (enhance notifications)
3. Phase 2.4 (command registration)
4. Phase 3.1 (RBAC — security first)
5. Phase 3.2 (rate limiting)
6. Phase 3.3 (metrics)
7. Phase 4.1 (migrate subscriptions to DB)
8. Phase 2.1 (navigation state)
9. Phase 2.3 (deep links)
10. Phase 3.4 (audit logging)
11. Phase 3.5 (circuit breaker)
12. Phase 4.2-4.5 (polish)

Implement step by step. After each step, run `./gradlew build` to verify. Mark progress.
