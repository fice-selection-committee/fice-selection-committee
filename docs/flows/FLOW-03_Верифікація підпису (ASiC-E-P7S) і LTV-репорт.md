# FLOW-03: Верифікація підпису (ASiC-E/P7S) і LTV-репорт

#### Purpose / Context
Юридична придатність віддалено поданих документів.
* * *
#### Actors
Applicant/Operator, Core/Signature, Crypto-движок, MinIO.
* * *
#### Trigger
`POST /signatures/verify/{documentId}` або авто-хуком після Private ([https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-4095](https://app.clickup.com/90151679901/docs/2kyqaxwx-755/2kyqaxwx-4095))
* * *
#### Preconditions
Документ у S3; доступні OCSP/CRL/TSA.
* * *
#### Main Flow / Sequence
1. Core отримує контент/маніфест із S3; перевіряє структурний формат.
2. Перевірка ланцюга сертифікатів, OCSP/CRL; штамп часу (TSA)
3. Формує **validation report** (JSON + PDF), кладе в S3 (той самий префікс)
4. Оновлює `signature_reports` та `documents.signed=true/status=SIGNED|INVALID`
* * *
#### Alternatives
*   A1: Мультипідпис (послідовна перевірка); A2: Повтор при оновленні CRL.
* * *
#### Errors / Retry
*   422 `SIGNATURE_INVALID`
*   413 `OCSP_TIMEOUT` (retry/backoff)
* * *
#### State & Data
`signature_reports`, S3 `/report.json|.pdf`
* * *
#### Security / Compliance
*   Логи криптооперацій; захист від підміни ЦСК.
#### Observability & SLO
p95 verify ≤ 1.5 c/файл; частка `INVALID` у денному розрізі.