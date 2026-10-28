# CyberClub — backend (КР3)

REST API киберспортивного клуба на Spring Boot. Продолжение КР1/КР2: та же предметная область
(клиенты, игровые места, бронирования) и те же бизнес-правила, но теперь данные отдаёт сервер,
которым пользуются JavaFX-клиент (КР2) и Android-приложение (КР4).

```
Клиент (JavaFX / Android / Swagger) ──HTTP/JSON──> Controller → Service → Repository (JPA) → PostgreSQL
```

## Запуск

Нужен только Docker Desktop: PostgreSQL и сам backend запускаются в контейнерах.

```bash
cd KR3-backend
copy .env.example .env          # один раз; в .env задать свой POSTGRES_PASSWORD
docker compose --env-file .env up -d --wait
```

Первый запуск долгий: Maven внутри контейнера скачивает зависимости. Состояние:
`docker compose --env-file .env ps` — у `db` и `backend` должно быть `healthy`.

Остановка без потери данных: `docker compose --env-file .env down` (без `-v`).

| Что | Адрес |
|---|---|
| Swagger UI | http://localhost:8081/swagger-ui.html |
| REST API | http://localhost:8081/api |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Health | http://localhost:8081/actuator/health |
| PostgreSQL | `jdbc:postgresql://localhost:55433/cyberclub`, пользователь `cyberclub_app` |

Порты 8081 и 55433 выбраны, чтобы не мешать примеру Student Hub (8080 и 55432).
Их можно поменять в `.env`.

## Авторизация

Весь `/api/**` закрыт: без входа сервер отвечает 401. Swagger UI и `/actuator/health` открыты.

**Первый администратор** создаётся при запуске, пока в базе нет пользователей: в `.env` задать
`BOOTSTRAP_ADMIN_EMAIL` и `BOOTSTRAP_ADMIN_PASSWORD` (от 8 символов, буквы и цифры, без имени из email).
Ключ подписи токенов — `JWT_SECRET_BASE64` (команда для генерации — в `.env.example`).

**Вход в Swagger UI:**
1. «Авторизация» → `POST /api/auth/login` → Try it out → email и пароль → Execute.
2. Скопировать `accessToken` из ответа.
3. Кнопка **Authorize** вверху справа → вставить токен (без слова `Bearer`) → Authorize → Close.
   Теперь все запросы из Swagger идут от имени этого пользователя (замки у методов закрыты).

**Регистрация пользователей** — администратор, раздел «Пользователи (администратор)»:
`POST /api/admin/users` с телом `{"email": "...", "displayName": "...", "password": "...", "roles": ["OPERATOR"]}`.

| Роль | Что может |
|---|---|
| `VIEWER` | смотреть клиентов, места, брони, аналитику |
| `OPERATOR` | + создавать и менять клиентов и брони (администратор зала) |
| `ADMIN` | + справочник мест, окончательное удаление брони, пользователи системы |

| Назначение | Метод и адрес |
|---|---|
| Вход: access JWT (15 мин) + refresh-токен (14 дн.) | `POST /api/auth/login` |
| Новая пара токенов (старый refresh больше не действует) | `POST /api/auth/refresh` |
| Выход | `POST /api/auth/logout` |
| Кто я / смена своего пароля | `GET /api/auth/me`, `POST /api/auth/password` |
| Пользователи: список, карточка, регистрация | `GET /api/admin/users`, `GET /api/admin/users/{id}`, `POST /api/admin/users` |
| Роли, включение/отключение, сброс пароля, снятие блокировки | `PUT .../{id}/roles`, `POST .../{id}/enabled`, `POST .../{id}/password`, `POST .../{id}/unlock` |

Защита: пароли — BCrypt (cost 12); в базе хранится SHA-256 refresh-токена, а не сам токен;
повторное использование уже обменянного refresh-токена закрывает все сессии пользователя;
после 5 неудачных попыток подряд вход блокируется на 15 минут (423); сообщение при неверном email
и неверном пароле одинаковое; права при каждом запросе перечитываются из базы — отключённый
пользователь теряет доступ сразу; нельзя отключить себя или оставить систему без администратора.

## Структура (как требует методичка КР3)

| Пакет | Назначение |
|---|---|
| `controller` | REST-контроллеры |
| `service` | бизнес-логика и транзакции; `BookingRules` — правила бронирования |
| `repository` | Spring Data JPA |
| `domain` | JPA-сущности и enum |
| `dto` | входные и выходные модели API (сущности наружу не отдаются) |
| `filter` | безопасный поиск и фильтры через Criteria API |
| `error` | единая обработка ошибок, JSON `ApiError` |
| `config` | OpenAPI (кнопка Authorize), правила доступа `SecurityConfig`, первый администратор, часы приложения, параметры `app.*` |
| `security` | JWT: выпуск и проверка токенов, фильтр `Authorization: Bearer`, политика паролей |
| `web` | пагинация с белым списком сортировки, correlation ID |
| `db/migration` | миграции Flyway: схема и тестовые данные |

## REST API

| Назначение | Метод и адрес |
|---|---|
| Список броней: поиск, фильтры, сортировка, страницы | `GET /api/bookings` |
| Карточка по UUID / по номеру | `GET /api/bookings/{id}`, `GET /api/bookings/number/{number}` |
| Создание | `POST /api/bookings` |
| Изменение (с `version`) | `PUT /api/bookings/{id}` |
| Смена статуса | `POST /api/bookings/{id}/status` |
| Архив вместо удаления | `DELETE /api/bookings/{id}` |
| Восстановление | `POST /api/bookings/{id}/restore` |
| Окончательное удаление | `DELETE /api/bookings/{id}/permanent` |
| Клиенты | `GET/POST /api/clients`, `GET/PUT/DELETE /api/clients/{id}` |
| Игровые места (справочник) | `GET/POST /api/stations`, `GET/PUT /api/stations/{id}`, `POST /api/stations/{id}/active` |
| Аналитика | `GET /api/analytics/summary` |

Параметры `GET /api/bookings`: `q` (номер `#12`, ник, ФИО, место, комментарий), `stationId`, `stationType`,
`clientId`, `status`, `paid`, `archive=ACTIVE|ARCHIVED|ALL`, `from`, `to` (даты), `priceOp=EQ|GT|GTE|LT|LTE`
вместе с `price`, `page`, `size` (до 100), `sort=number|start|price|status|client|station|created|updated,asc|desc`.

Коды ответов: 400 — неверные данные или нарушено правило клуба, 401 — нужен вход,
403 — не хватает прав роли, 404 — записи нет, 409 — конфликт версии, занятое место или повтор
уникального поля, 423 — вход временно заблокирован после перебора паролей.

## Что уже сделано и что осталось по КР3

Сделано (этап 1): основная сущность с UUID, номером, статусом, числовым показателем (стоимость),
логическим признаком (оплачено), датами, `version` и архивом; CRUD с архивом и восстановлением;
поиск, 9 фильтров, сортировка по 8 полям, серверная пагинация; справочник мест с включением
и отключением; аналитика; Bean Validation; единый JSON ошибок; correlation ID; Flyway; Swagger;
Docker Compose с healthcheck; тесты (контекст, бизнес-правила, фильтр).

Сделано (этап 2, часть 1): авторизация — JWT, refresh-сессии с ротацией, три роли, BCrypt, блокировка
перебора паролей, кнопка Authorize в Swagger, первый администратор из `.env`, администрирование
пользователей; миграция `V3__users_and_auth.sql`; тест `AuthFlowTest` (10 сценариев).

Осталось (этап 2): теги и вычисляемый показатель, заметки к брони, массовые операции,
импорт/экспорт CSV, журнал аудита; клиенты JavaFX (КР2) и Android (КР4) — вход и передача токена.
