# TaskLoop

Учебный REST-сервис: **Spring Boot 3.3** + **PostgreSQL 16** + **Flyway**. Требуется **JDK 17+** для сборки (проверено с 17). Цель — типовой «скелет» CRUD с заделом под эксплуатацию: **OpenAPI**, **Actuator + Prometheus**, **Docker**, **лимиты и метрики** для вызова локальной LM Studio.

## Быстрые ссылки (локально, порт по умолчанию 8080)

- UI: [http://localhost:8080/](http://localhost:8080/)
- OpenAPI (Swagger UI): [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Actuator health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- Метрики Prometheus: [http://localhost:8080/actuator/prometheus](http://localhost:8080/actuator/prometheus)

## Запуск

### Вариант 1: только PostgreSQL в Docker, приложение локально

1. `docker compose up -d db`
2. **`./mvnw spring-boot:run`** (macOS/Linux) или **`.\mvnw.cmd spring-boot:run`** в PowerShell из каталога проекта. В PowerShell нельзя писать просто `mvnw.cmd` — нужен префикс `.\` для запуска из текущей папки.

3. В браузере: [http://localhost:8080/](http://localhost:8080/) — **дерево досок и задач слева**, выбор доски, список с отступами по вложенности, добавление **корневой** или **подзадачи** (задача выбрана в дереве), «Совет» с серверным контекстом доски и цепочки родителей, «Доска из совета».

Скопируйте **`.env.example` → `.env`** в каталоге `taskloop-api` и заполните хотя бы `LMSTUDIO_MODEL` (остальное можно не трогать для значений по умолчанию). При старте приложения переменные из `.env` попадают в конфигурацию Spring; **реальные переменные окружения ОС имеют приоритет** над `.env`.

### Вариант 2: приложение + БД в Docker

Из каталога `taskloop-api`:

```bash
docker compose up -d --build
```

- Приложение слушает **8080** на хосте.
- Для LM Studio на хосте по умолчанию используется `LMSTUDIO_BASE_URL=http://host.docker.internal:1234/v1` (можно переопределить переменной окружения при `docker compose up`).

### Порт 8080 уже занят

Сообщение `Port 8080 was already in use` значит, что порт занят другим процессом (часто это не остановленный прошлый запуск приложения).

**Вариант A — освободить 8080 (PowerShell):**

```powershell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object OwningProcess
# по PID из вывода:
Stop-Process -Id <PID> -Force
```

Либо через `netstat -ano | findstr :8080` и `taskkill /PID <pid> /F`.

**Вариант B — запустить на другом порту** (по умолчанию в конфиге `8080`, можно задать `SERVER_PORT`):

```powershell
$env:SERVER_PORT=8081
.\mvnw.cmd spring-boot:run
```

После этого UI: http://localhost:8081/ — в примерах `curl` ниже замените `8080` на ваш порт.

## Доски (`boards`)

Базовый путь: **`/api/v1/boards`**.

| Метод | Описание |
|-------|----------|
| `GET /api/v1/boards` | Список досок (`id`, `title`, `createdAt`, `taskCount`). |
| `POST /api/v1/boards` | Создать доску: тело `{"title":"…"}`. |
| `PATCH /api/v1/boards/{id}` | Переименовать: `{"title":"…"}`. |
| `DELETE /api/v1/boards/{id}` | Удалить доску и все её задачи (**204**). Удаление **дефолтной** доски (`00000000-0000-4000-8000-000000000001`) — **409 Conflict**. |
| `GET /api/v1/boards/{boardId}/tasks/tree` | Дерево задач доски: корни и вложенные узлы с полями как у задачи + **`children`** (массив узлов). На каждом уровне сортировка по **`createdAt` от новых к старым**. **404**, если доски нет. |

При первом запуске миграции создаётся доска **«По умолчанию»** с фиксированным UUID выше.

Задачи поддерживают **иерархию**: колонка `parent_task_id` (FK на другую задачу той же доски, **ON DELETE CASCADE** — удаление родителя удаляет поддерево). Максимальная глубина вложенности: **16** уровней от корня.

## API версии и пагинация для задач

Базовый путь: **`/api/v1/tasks`**. Список задач **привязан к доске**: обязательный query-параметр **`boardId`** (UUID).

Список задач возвращает страницу:

`GET /api/v1/tasks?boardId=<UUID>&page=0&size=20&sort=createdAt,desc`

Без `boardId` API вернёт **400** с пояснением в `detail`.

Ответ страницы: `content`, `page`, `size`, `totalElements`, `totalPages`, `last`. Элементы содержат **`parentTaskId`** (`null` для корневой задачи).

Создание задачи:

`POST /api/v1/tasks` с `{"title":"…","boardId":"<UUID>","parentTaskId":"<UUID>"}` — поле **`parentTaskId`** опционально; при указании родитель должен быть на **той же доске**, иначе **400**.

Пример дерева:

```bash
curl -s "http://localhost:8080/api/v1/boards/$DEFAULT_BOARD/tasks/tree"
```

По задаче, для которой уже сохранён текст совета, можно создать **новую доску** и разложить совет на отдельные задачи (парсинг нумерованных строк и маркированных списков):

`POST /api/v1/tasks/<ID>/spawn-board-from-advice` с телом `{}` или `{"title":"Название новой доски"}` (поле опционально).

Ответ **201**: `board` (объект доски), `tasksCreated` (число созданных задач).

## Контракт с LM Studio (совет по задаче)

Интеграция — **OpenAI-compatible** `POST /chat/completions` относительно `lmstudio.base-url` (часто `http://localhost:1234/v1`).

- **Что уходит в модель:** системный промпт + пользовательское сообщение. Для **`POST /tasks/{id}/advice`** сервер передаёт **название доски**, **цепочку заголовков предков** (от корня к родителю текущей задачи) и **текущую задачу**; учитывайте **`lmstudio.max-advice-context-chars`** (по умолчанию 4000) — при переполнении сначала укорачивается контекст сверху по цепочке.
- **`max_tokens`:** `lmstudio.max-tokens` (по умолчанию **2048**; для «разговорчивых» моделей можно увеличить).
- **Таймауты:** `lmstudio.connect-timeout-seconds` (подключение) и `lmstudio.timeout-seconds` (чтение ответа).
- **Повторный запрос** `POST /api/v1/tasks/{id}/advice` **перезаписывает** сохранённый текст совета и время `adviceAt`.
- **Ошибки:** при недоступности LM Studio или сетевых сбоях API возвращает, например, **502 Bad Gateway**, **503 Service Unavailable**, **504 Gateway Timeout**; при не заданной модели — **400**. Тело ошибки — JSON с полем `detail` (как и у других ошибок API).
- **Rate limit:** только на `POST .../advice` — по умолчанию **30 запросов за 60 секунд** на IP (или первый адрес из `X-Forwarded-For`). При превышении — **429** и JSON с `detail`. Настройка: `advice-rate-limit.*` / переменные `ADVICE_RATE_LIMIT_*` в `.env`.

Метрики (Micrometer): `taskloop.advice.lmstudio` (таймер), `taskloop.advice.lmstudio.failures`, `taskloop.advice.completed`.

## Совет по задаче (LM Studio)

Текст задачи отправляется на **ваш локальный** сервер inference (OpenAI-совместимый API). Убедитесь, что LM Studio запущена, модель загружена и включён **Local Server** (по умолчанию часто `http://localhost:1234/v1`).

1. Укажите **имя модели** так же, как в LM Studio:
   - в **`.env`**: `LMSTUDIO_MODEL=...`, или
   - в `application.yml` (`lmstudio.model`), или
   - переменная окружения ОС перед запуском.

2. При ошибке **502/503/504** с текстом вроде «LM Studio недоступна» проверьте, что локальный API включён и порт совпадает с `lmstudio.base-url`.

3. Повторный запрос совета **перезаписывает** сохранённый текст в БД.

## Примеры

Дефолтная доска (подставляйте свой хост/порт при необходимости):

```bash
DEFAULT_BOARD=00000000-0000-4000-8000-000000000001
```

Список досок:

```bash
curl -s http://localhost:8080/api/v1/boards
```

Создать задачу на дефолтной доске:

```bash
curl -s -X POST http://localhost:8080/api/v1/tasks -H "Content-Type: application/json" \
  -d "{\"title\":\"Купить молоко\",\"boardId\":\"$DEFAULT_BOARD\"}"
```

Список задач на доске (первая страница):

```bash
curl -s "http://localhost:8080/api/v1/tasks?boardId=$DEFAULT_BOARD&page=0&size=20&sort=createdAt,desc"
```

Отметить выполненной (подставьте id из ответа):

```bash
curl -s -X PATCH http://localhost:8080/api/v1/tasks/<ID>/done -H "Content-Type: application/json" -d "{\"done\": true}"
```

Проверка 404 для несуществующего id (PowerShell):

```powershell
(Invoke-WebRequest -Uri "http://localhost:8080/api/v1/tasks/00000000-0000-0000-0000-000000000000/done" -Method PATCH -ContentType "application/json" -Body '{"done":true}' -SkipHttpErrorCheck).StatusCode
```

Ожидается `404`.

Запросить совет по задаче (подставьте id; нужна запущенная LM Studio и заданный `lmstudio.model`):

```bash
curl -s -X POST http://localhost:8080/api/v1/tasks/<ID>/advice -H "Content-Type: application/json" -d "{}"
```

Создать новую доску из текста совета (сначала должен быть непустой `advice` у задачи):

```bash
curl -s -X POST http://localhost:8080/api/v1/tasks/<ID>/spawn-board-from-advice \
  -H "Content-Type: application/json" -d "{}"
```

Заголовок опционально: `X-Correlation-Id` — пробрасывается в ответ и в логи (MDC).

## CI

Если CI настроен на уровень выше каталога приложения, workflow **`.github/workflows/ci.yml`** может запускать `./mvnw verify` в `taskloop-api` при изменениях в этом каталоге.

## Заметки

- `ddl-auto: validate` — схема только из Flyway.
- UI — статический `static/index.html`: слева дерево досок и задач, справа работа с выбранной доской; **`POST /api/v1/tasks/{id}/advice`** передаёт контекст на сервере; при наличии совета — «Доска из совета».
- Интеграция с LM Studio: пакет `com.example.tasklist.lmstudio`, настройки префикса `lmstudio.*` в `application.yml`.

## Отдельный репозиторий

Чтобы вынести только этот сервис в свой Git-репозиторий (например на GitHub): в каталоге `taskloop-api` выполните `git init`, добавьте remote (`git remote add origin <url>`), закоммитьте файлы и `git push`.
