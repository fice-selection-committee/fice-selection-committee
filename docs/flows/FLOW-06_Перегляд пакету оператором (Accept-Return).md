# FLOW-06: Перегляд пакету оператором (Accept/Return)

#### Purpose / Context
Перевірити повноту/валідність і прийняти або повернути.
* * *
#### Actors
Operator, Core/Documents/Checks, Applicant (recipient)
* * *
#### Trigger
Дія в UI комісії.
* * *
#### Preconditions
Заявка `ASSIGNED`; є репорти ЕП; OCR-дані (якщо доступні)
* * *
#### Main Flow / Sequence
1. Оператор переглядає чек-лист; проставляє коментарі/теги.
2. Рішення: **Accept** → `ACCEPTED` або **Return** → `REWORK_REQUIRED`
3. При Return: генеруються нотифікації з переліком помилок і дедлайном.
* * *
#### Alternatives
*   A1: Часткове прийняття із дозавантаженням.
* * *
#### Errors / Retry
*   Збої сховища → retry;
*   конфлікт версій → повтор з оновленим снапшотом.
* * *
#### State & Data
`applications.status`, `review_comments`, `notifications`
* * *
#### Security / Compliance
*   RBAC: тільки роль Operator/Secretary; повний audit.
#### Observability & SLO
TTA (time-to-accept) медіана; частка повернень.