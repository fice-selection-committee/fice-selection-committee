# FLOW-08: Архівація прийнятого пакету в S3 (і optional експорт у Drive)

#### Purpose / Context
Довгострокове зберігання та ручні процеси.
* * *
#### Actors
Operator (initiator), Core/Archive, MinIO, (optional) Drive-Adapter.
* * *
#### Trigger
Accept у Private ([https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-4175](https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-4175)) або нічний _batch_.
* * *
#### Preconditions
Усі обов’язкові файли + репорти ЕП присутні.
* * *
#### Main Flow / Sequence
1. Core формує структуру `year=/specialty=/app=/doc=/vN/…` та список вкладень.
2. Генерує PDF-комплект (титул, зміст, QR/штрих-код), кладе в S3.
3. Повертає посилання; (опц.) — дублює у Drive відповідну папку.
* * *
#### Alternatives
*   A1: Тільки електронний архів без PDF-зшивки.
* * *
#### Errors / Retry
*   Квота S3/Drive → алерт + повтор.
* * *
#### State & Data
`archive_records`, S3 ключі; (опц.) Drive IDs.
* * *
#### Security / Compliance
*   Versioning + lifecycle; доступ лише службам.
#### Observability & SLO
Час архівації ≤ 10 с/пакет у середньому.