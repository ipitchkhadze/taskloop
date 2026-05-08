# TaskLoop

<div align="center">

**Доски, дерево задач и ИИ-советы локально — без облачных API-ключей**

[Spring Boot 3.3](https://spring.io/projects/spring-boot) · Java 17 · PostgreSQL 16 · Flyway · OpenAPI · Prometheus

</div>

---

## Зачем это нужно

**TaskLoop** — это бэкенд + веб-UI для личного или учебного управления задачами с упором на **структуру** (доски + **дерево подзадач**) и на **осмысленные советы от локальной LLM** через [LM Studio](https://lmstudio.ai/) (OpenAI-compatible API). Вы держите данные у себя (PostgreSQL), не платите за облачные вызовы моделей и полностью контролируете контекст, который видит модель.

| Для кого | Что получаете |
|----------|----------------|
| **Разработчик / учёба** | Готовый «продуктовый» каркас: REST API `/api/v1`, миграции, валидация, обработка ошибок, лимиты на дорогие вызовы, метрики. |
| **Пользователь UI** | Одна страница в браузере: дерево досок и задач, отметка выполнения, запрос совета, разбор ответа модели в **новую доску** с подзадачами. |
| **Оператор локальной модели** | Настраиваемые таймауты, лимиты токенов и размера контекста; rate limit только на `POST .../advice`; Prometheus-метрики по вызовам LM Studio. |

---

## Возможности

### Доски и задачи

- Несколько **досок** с созданием, переименованием и удалением (с защитой системной доски «По умолчанию»).
- **Иерархия задач** до **16 уровней** вложенности: корневая задача и подзадачи на одной доске; целостность `parent` ↔ `board` проверяется на сервере.
- **Два представления**: плоский **пагинированный** список по доске и **дерево** (`GET .../boards/{id}/tasks/tree`) для UI и интеграций.
- Поля задачи: заголовок, `done`, время создания, опционально текст **совета** и время получения (`advice` / `advice_at`), порядок строк при разборе совета (`line_order`).

### ИИ: совет и «доска из совета»

- **`POST /api/v1/tasks/{id}/advice`** — запрос к LM Studio: сервер сам собирает **контекст** — название доски, **цепочку заголовков предков** от корня до текущей задачи и саму задачу. Переполнение контекста обрабатывается настройкой `max-advice-context-chars` (сначала укорачивается «верх» цепочки).
- **`POST .../spawn-board-from-advice`** — из уже сохранённого текста совета создаётся **новая доска** и набор задач: парсинг нумерованных пунктов и маркированных списков (см. `AdviceLineParser`), порядок сохраняется.

### Надёжность и наблюдаемость

- **Rate limiting** только на эндпоинт совета (по умолчанию 30 запросов / 60 с на IP или `X-Forwarded-For`).
- **Micrometer / Prometheus**: латентность и ошибки вызовов LM Studio, счётчик успешных советов (`taskloop.advice.*`).
- **Correlation ID**: заголовок `X-Correlation-Id` пробрасывается в ответ и в логи (MDC).
- **Actuator**: health (в т.ч. liveness/readiness), метрики Prometheus.
- **OpenAPI 3** + Swagger UI для интерактивных запросов.

### Доставка

- **Docker Compose**: только БД или приложение + БД; для LM Studio на хосте — `host.docker.internal`.
- **GitHub Actions** (в монорепозитории): `mvn verify` при изменениях в `taskloop-api/`.

---

## Стек

| Слой | Технологии |
|------|------------|
| Runtime | Java 17, Spring Boot 3.3 |
| Данные | Spring Data JPA, PostgreSQL 16, Flyway |
| API | REST `/api/v1`, springdoc-openapi, Bean Validation |
| LLM | HTTP-клиент к OpenAI-compatible API (LM Studio) |
| Защита от злоупотреблений | bucket4j на `/tasks/*/advice` |
| UI | Статическая страница `static/index.html` (без отдельного фронтенд-сборщика) |

---

## Быстрые ссылки (localhost, порт по умолчанию **8080**)

| Что | URL |
|-----|-----|
| Веб-интерфейс | [http://localhost:8080/](http://localhost:8080/) |
| Swagger UI | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| OpenAPI JSON | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |
| Health | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| Prometheus | [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus) |

---

## Запуск

### Предварительно

- **JDK 17+**
- **PostgreSQL** (или только Docker для БД)

Скопируйте **`.env.example` → `.env`** в каталоге `taskloop-api`. Минимум для советов: задайте **`LMSTUDIO_MODEL`** так же, как идентификатор модели в LM Studio. Переменные окружения ОС имеют приоритет над `.env`.

### Вариант A: только PostgreSQL в Docker, приложение локально

```bash
docker compose up -d db
```

```bash
# macOS / Linux
./mvnw spring-boot:run

# Windows PowerShell (нужен префикс .\)
.\mvnw.cmd spring-boot:run
```

Откройте UI: [http://localhost:8080/](http://localhost:8080/) — слева дерево досок и задач, справа работа с выбранной доской, кнопки **Совет** и **Доска из совета** при наличии текста совета.

### Вариант B: приложение и БД в Docker

```bash
docker compose up -d --build
```

Приложение на порту **8080**. Для LM Studio на машине-хосте по умолчанию удобно:  
`LMSTUDIO_BASE_URL=http://host.docker.internal:1234/v1`.

### Порт занят

Освободить **8080** или задать другой порт:

```powershell
$env:SERVER_PORT=8081
.\mvnw.cmd spring-boot:run
```

Во всех примерах ниже замените `8080` на ваш порт.

---

## REST API (кратко)

Префикс версии: **`/api/v1`**. Ошибки — JSON с полем `detail` (и типичные коды HTTP).

### Доски

| Метод | Путь | Описание |
|--------|------|----------|
| `GET` | `/boards` | Список досок: `id`, `title`, `createdAt`, `taskCount` |
| `POST` | `/boards` | Создать: `{"title":"..."}` → **201** |
| `PATCH` | `/boards/{id}` | Переименовать: `{"title":"..."}` |
| `DELETE` | `/boards/{id}` | Удалить доску и все задачи (**204**). Системная доска по умолчанию — **409** |
| `GET` | `/boards/{boardId}/tasks/tree` | Дерево задач: узлы как у задачи + **`children`**, сортировка по уровням по **`createdAt`** (новые выше). Нет доски — **404** |

При первом применении миграций создаётся доска **«По умолчанию»** с фиксированным UUID `00000000-0000-4000-8000-000000000001`.

### Задачи

| Метод | Путь | Описание |
|--------|------|----------|
| `GET` | `/tasks?boardId=<UUID>&page=&size=&sort=` | Плоский список с пагинацией; **`boardId` обязателен**, иначе **400** |
| `POST` | `/tasks` | Создать: `title`, `boardId`, опционально `parentTaskId` (родитель на той же доске) |
| `PATCH` | `/tasks/{id}/done` | Тело: `{"done": true}` или `{"done": false}` |
| `POST` | `/tasks/{id}/advice` | Запрос совета к LM Studio; ответ сохраняется в задаче (**повтор перезаписывает** `advice` / `adviceAt`) |
| `POST` | `/tasks/{id}/spawn-board-from-advice` | Новая доска из текста совета; тело `{}` или `{"title":"..."}` → **201** с `board` и `tasksCreated` |

Иерархия: FK `parent_task_id`, **ON DELETE CASCADE** (удаление родителя удаляет поддерево).

---

## LM Studio и конфигурация

Интеграция — **`POST /chat/completions`** относительно `lmstudio.base-url` (часто `http://localhost:1234/v1`).

1. Запустите LM Studio, загрузите модель, включите **Local Server**.
2. Укажите модель: **`LMSTUDIO_MODEL`** в `.env`, или `lmstudio.model` в `application.yml`, или переменная окружения.

Поведение и ограничения:

- **`max_tokens`** — `lmstudio.max-tokens` (по умолчанию 2048).
- **Таймауты** — `connect-timeout-seconds`, `timeout-seconds`.
- **Размеры** — ограничения длины заголовка задачи, ответа модели и контекста совета (`max-advice-context-chars`, по умолчанию 4000 символов).
- **Ошибки сети / LM Studio** — **502**, **503**, **504**; модель не задана — **400**.
- **Rate limit** на `POST .../advice` — по умолчанию **30** запросов за **60** секунд на клиента; превышение — **429**.

### Переменные окружения (основные)

| Переменная | Назначение |
|------------|------------|
| `SERVER_PORT` | Порт HTTP (по умолчанию 8080) |
| `SPRING_DATASOURCE_*` | JDBC URL, пользователь, пароль |
| `LMSTUDIO_BASE_URL` | База OpenAI-compatible API |
| `LMSTUDIO_MODEL` | Идентификатор модели в LM Studio (**важно для совета**) |
| `LMSTUDIO_API_KEY` | Если локальный сервер требует ключ |
| `LMSTUDIO_*_TIMEOUT_*`, `LMSTUDIO_MAX_*` | Таймауты и лимиты размеров |
| `LMSTUDIO_MAX_ADVICE_CONTEXT_CHARS` | Лимит символов контекста в промпте |
| `ADVICE_RATE_LIMIT_ENABLED` | Включить/выключить лимит на совет |
| `ADVICE_RATE_LIMIT_REQUESTS` | Запросов за окно |
| `ADVICE_RATE_LIMIT_WINDOW_SECONDS` | Размер окна в секундах |

Полный список см. в **`.env.example`** и в `application.yml`.

### Метрики (Prometheus)

| Имя | Смысл |
|-----|--------|
| `taskloop.advice.lmstudio` | Таймер латентности вызова LM Studio |
| `taskloop.advice.lmstudio.failures` | Счётчик ошибок |
| `taskloop.advice.completed` | Успешные завершения совета |

---

## Примеры `curl`

Подставьте хост/порт при необходимости.

```bash
export DEFAULT_BOARD=00000000-0000-4000-8000-000000000001

curl -s http://localhost:8080/api/v1/boards

curl -s -X POST http://localhost:8080/api/v1/tasks \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Купить молоко\",\"boardId\":\"$DEFAULT_BOARD\"}"

curl -s "http://localhost:8080/api/v1/tasks?boardId=$DEFAULT_BOARD&page=0&size=20&sort=createdAt,desc"

curl -s "http://localhost:8080/api/v1/boards/$DEFAULT_BOARD/tasks/tree"

curl -s -X PATCH http://localhost:8080/api/v1/tasks/<TASK_ID>/done \
  -H "Content-Type: application/json" -d "{\"done\": true}"

curl -s -X POST http://localhost:8080/api/v1/tasks/<TASK_ID>/advice \
  -H "Content-Type: application/json" -d "{}"

curl -s -X POST http://localhost:8080/api/v1/tasks/<TASK_ID>/spawn-board-from-advice \
  -H "Content-Type: application/json" -d "{}"
```

Опционально для трассировки в логах:

```http
X-Correlation-Id: <ваш-id>
```

Проверка **404** (PowerShell):

```powershell
(Invoke-WebRequest -Uri "http://localhost:8080/api/v1/tasks/00000000-0000-0000-0000-000000000000/done" `
  -Method PATCH -ContentType "application/json" -Body '{"done":true}' -SkipHttpErrorCheck).StatusCode
```

---

## Заметки для разработчиков

- Схема БД только из **Flyway** (`ddl-auto: validate`).
- Код LM Studio: пакет `com.example.tasklist.lmstudio`; настройки — префикс `lmstudio.*`.
- Веб-UI — один файл **`static/index.html`**: дерево, выбор доски, работа с задачами и вызовы API советов.

---

## CI и отдельный репозиторий

В монорепозитории workflow **`.github/workflows/ci.yml`** запускает `./mvnw verify` в каталоге **`taskloop-api`** при изменениях в нём.

Чтобы вынести только этот сервис на GitHub: в `taskloop-api` выполните `git init`, `git remote add origin <url>`, закоммитьте и `git push`.

---

<div align="center">

**TaskLoop** — структурируйте задачи, подключайте локальную модель, сохраняйте контроль над данными и контекстом.

</div>
