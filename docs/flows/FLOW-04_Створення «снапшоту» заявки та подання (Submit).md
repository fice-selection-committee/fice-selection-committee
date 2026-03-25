# FLOW-04: Створення «снапшоту» заявки та подання (Submit)

#### Purpose / Context
Зафіксувати незмінний стан для перевірки й аудиту.
* * *
#### Actors
Applicant, Core/Applications.
* * *
#### Trigger
`POST /applications/{id}/submit` з `Idempotency-Key`
* * *
#### Preconditions
Мінімальний набір документів + валідні підписи.
* * *
#### Main Flow / Sequence
1. Core робить **immutable snapshot** списку документів/метаданих (hash-маніфест)
2. Блокує редагування критичних полів; статус `SUBMITTED`; ставить у чергу.
3. Генерує подію `application.submitted` (audit, нотифікації)
* * *
#### Alternatives
*   A1: Дозавантаження некритичних файлів (не знімає блоки).
* * *
#### Errors / Retry
*   `409` при повторі (ідемпотентність)
*   `422` якщо некомплект.
* * *
#### State & Data
`applications(snapshot_hash, submitted_at)`, Redis черга.
* * *
#### Security / Compliance
*   Снапшот підписується службовим ключем (опційно)
#### Observability & SLO
p95 submit ≤ 300 мс; контроль черги призначення.