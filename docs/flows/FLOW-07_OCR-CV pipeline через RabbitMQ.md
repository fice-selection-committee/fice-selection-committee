# FLOW-07: OCR/CV pipeline через RabbitMQ

#### Purpose / Context
Витяг реквізитів без блокування UI.
* * *
#### Actors
Core/OCR Orchestrator, CV-Service, RabbitMQ, MinIO.
* * *
#### Trigger
Новий документ типу паспорт/ІПН або вимога оператора.
* * *
#### Preconditions
Документ у S3; CV-модель активна.
* * *
#### Main Flow / Sequence
1. Core → `cv.events:` `cv.document.requested` `{s3Key, type, traceId}`
2. CV качає файл з S3, робить pre-proccessing та OCR.
3. CV → `cv.document.parsed` `{documentId, fields, confidence}`
4. Core зберігає `ocr_results`, пропонує автозаповнення, ставить прапор `auto-extracted`
* * *
#### Alternatives
*   A1: Низький `confidence` → route на ручну перевірку.
* * *
#### Errors / Retry
*   `cv.document.failed` з `retriable=true` → повтор 3 рази (5s/30s/5m), далі DLQ.
* * *
#### State & Data
`ocr_results.status`, DLQ записи, S3 оригінал.
* * *
#### Security / Compliance
*   Тільки лінки на S3; без персональних даних у payload черг.
#### Observability & SLO
Throughput ≥ 50 док/хв/instance; lag черги < 1 хв у піки.