# FLOW-10: Розподіл на групи

#### Purpose / Context
Автоматичне призначення груп за правилами.
* * *
#### Actors
Responsible for Grouping, Core/Grouping, PostgreSQL.
* * *
#### Trigger
`POST /grouping/run?specialty=…` або CRON по хвилям.
* * *
#### Preconditions
Прийняті кандидати; відомі пріоритети/бал.
* * *
#### Main Flow / Sequence
1. Отримати пул кандидатів; нормалізувати бали/пріоритети/форми.
2. Алгоритм заповнює групи; формує **протокол** (включаючи причини рішень)
3. Запис у `group_assignments`; статус оновлюється в `applications`
* * *
#### Alternatives
*   A1: Відсутня пріоритетка → default-правило/випадково;
*   A2: Ручне коригування з причиною.
* * *
#### Errors / Retry
*   Переповнення груп → помилка правил; перезапуск після правок.
* * *
#### State & Data
`group_assignments`, протокол (S3)
* * *
#### Security / Compliance
*   Логи всіх ручних втручань.
#### Observability & SLO
p95 run ≤ 5 хв на хвилю ~N кандидатів.