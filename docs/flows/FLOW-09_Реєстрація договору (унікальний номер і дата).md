# FLOW-09: Реєстрація договору (унікальний номер і дата)

#### Purpose / Context
Офіційний реєстр договорів про навчання/оплату.
* * *
#### Actors
Responsible for Contracts, Core/Registry, PostgreSQL.
* * *
#### Trigger
`POST /contracts/{applicationId}/register` з `Idempotency-Key`.
* * *
#### Preconditions
Пакет прийнятий; шаблон нумерації активний.
* * *
#### Main Flow / Sequence
1. Транзакція: `SELECT ... FOR UPDATE` з таблиці `contract_sequences`
2. Інкремент номера; форматування (наприклад «11б»), перевірка унікальності.
3. Запис у `contracts(number, registered_at)`; повернення відповіді.
* * *
#### Alternatives
*   A1: Ручна корекція номера з audit-trail.
* * *
#### Errors / Retry
*   `409` при гонках — повтор транзакції.
* * *
#### State & Data
`contracts`, `contract_sequences`
* * *
#### Security / Compliance
*   Доступ лише ролі «відп. за договори»
#### Observability & SLO
Реєстрація ≤ 300 мс; 0 колізій у звичайному режимі.