# FLOW-14: Ідемпотентні POST-и (повтори без дублювань)

#### Purpose / Context
Захист від повторних кліків/мережевих ретраїв.
* * *
#### Actors
Client, Gateway, Core, Redis.
* * *
#### Trigger
Будь-який «небезпечний» POST (create, upload, register)
* * *
#### Preconditions
Клієнт додає `Idempotency-Key`
* * *
#### Main Flow / Sequence
1. Core перевіряє Redis cache по ключу.
2. Якщо **hit** — повертає збережену відповідь 200/201.
3. Якщо **miss** — виконує операцію, зберігає відповідь у Redis (TTL 24h)
* * *
#### Errors / Retry
*   При падінні після коміту — відповідь буде відновлена під час повтору.
* * *
#### State & Data
Redis `idem:<key>`
* * *
#### Security / Compliance
*   Ключ — з достатньою ентропією (UUID/ULID)
#### Observability & SLO
Hit-rate ≥ 70% у пікових хвилях.