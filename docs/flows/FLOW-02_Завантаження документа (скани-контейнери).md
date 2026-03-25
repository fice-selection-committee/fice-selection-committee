# FLOW-02: Завантаження документа (скани/контейнери)

#### Purpose / Context
Прийом файлів з антивірусом та валідацією параметрів.
* * *
#### Actors
Applicant, Gateway, Core/Documents, AV-scanner, MinIO.
* * *
#### Trigger
`POST /documents/{applicationId}/upload` (multipart) з `Idempotency-Key`
* * *
#### Preconditions
Кампанія відкрита; квоти користувача в нормі.
* * *
#### Main Flow / Sequence
1. Gateway перевіряє розмір/тип; Core створює `document` зі статусом `UPLOADING`
2. Струмінне сканування AV; валідовано mime/розмір; підрахунок `sha256`
3. PUT у S3 (`docs-bucket`) з метаданими (тип, sha256, source)
4. Core фіксує `UPLOADED`, повертає `{documentId, s3Key}`
* * *
#### Alternatives
*   A1: Пакетне завантаження (кілька частин); A2: Папка → багато документів.
* * *
#### Errors / Retry
*   422 `FILE_INFECTED`
*   413 `TOO_LARGE`
*   мережеві — повтор до 3 разів.
* * *
#### State & Data
`documents`, S3-об’єкт v1, Redis `doc:processing:<id>` (24h)
* * *
#### Security / Compliance
*   Тільки TLS;
*   перевірка MIME vs магія;
*   приватні ACL.
#### Observability & SLO
p95 upload-ACK ≤ 2 c (без урахування розміру); метрики AV-спрацювань.