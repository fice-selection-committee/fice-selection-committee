# FLOW-11: Формування наказів (enroll/expel)

#### Purpose / Context
Генерація тексту наказів, нумерація, підготовка PDF.
* * *
#### Actors
Secretary/Deputy, Core/Orders, Templates, MinIO.
* * *
#### Trigger
`POST /orders` (параметри: тип, дата, фільтри).
* * *
#### Preconditions
Кандидати з валідними статусами; реєстри/групи заповнені.
* * *
#### Main Flow / Sequence
1. Вибірка кандидатів за критеріями; застосування шаблону (uk/en)
2. Генерація номера/дати; рендер PDF; збереження в S3.
3. Повернення `{orderId, number, date, s3Key}`; статус `DRAFT/SIGNED`
* * *
#### Alternatives
*   A1: Комбіновані накази;
*   A2: Ревізія / версії.
* * *
#### Errors / Retry
*   Відсутні дані → `422`;
*   шаблон зламався → `500` + алерт.
* * *
#### State & Data
`orders`, S3 pdf.
* * *
#### Security / Compliance
*   Контроль підпису/погоджень поза системою (або окремий підмодуль КЕП)
#### Observability & SLO
p95 генерація ≤ 3 с/наказ.