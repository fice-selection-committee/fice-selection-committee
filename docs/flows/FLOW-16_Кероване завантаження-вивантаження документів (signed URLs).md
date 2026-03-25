# FLOW-16: Кероване завантаження/вивантаження документів (signed URLs)

#### Purpose / Context
Безпечний доступ до файлів без «витоку» прямих ключів.
* * *
#### Actors
Client, Core, MinIO.
* * *
#### Trigger
`GET /documents/{id}/download`
* * *
#### Preconditions
Авторизація перевірена; роль має право на перегляд.
* * *
#### Main Flow / Sequence
1. Core перевіряє RBAC; генерує **pre-signed URL** з TTL (наприклад 5 хв)
2. UI завантажує файл напряму з S3 за URL.
* * *
#### Alternatives
*   A1: Проксі-завантаження через Core для додаткового маскування.
* * *
#### Errors / Retry
*   403 при спробі іншої особи;
*   410 якщо URL протерміновано.
* * *
#### State & Data
Немає змін стану; лог доступу до документів.
* * *
#### Security / Compliance
*   Короткі TTL; один URL — один файл; IP-прив’язка (опц.)
#### Observability & SLO
Середній час відповіді на видачу URL ≤ 100 мс.