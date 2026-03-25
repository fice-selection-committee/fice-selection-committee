# FLOW-05: Автоматичне призначення оператору

#### Purpose / Context
Рівномірна обробка заяв під час хвиль.
* * *
#### Actors
Core/Assignment, Operators, Redis.
* * *
#### Trigger
Подія `application.submitted` або CRON.
* * *
#### Preconditions
Є активні оператори; політика (round-robin/weight)
* * *
#### Main Flow / Sequence
1. Core бере найближчу «вільну» заявку; обчислює виконавця.
2. Пише `assignee`, стартує SLA-таймер; статус `ASSIGNED`
3. Генерує нотифікацію оператору.
* * *
#### Alternatives
*   A1: Перепризначення при idle/overload;
*   A2: Пріоритет пільговиків.
* * *
#### Errors / Retry
*   Якщо ніхто не доступний → статус `QUEUED`;
*   повтор через CRON.
* * *
#### State & Data
`applications.assignee`, `assignment_events`
* * *
#### Security / Compliance
*   Аудит усіх перепризначень (хто/чому)
#### Observability & SLO
p95 time-to-assign ≤ 60 с; баланс навантаження між операторами.