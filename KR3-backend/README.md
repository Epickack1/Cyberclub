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
| `config` | OpenAPI, часы приложения, параметры `app.*` |
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

Коды ответов: 400 — неверные данные или нарушено правило клуба, 404 — записи нет,
409 — конфликт версии, занятое место или повтор уникального поля.

## Что уже сделано и что осталось по КР3

Сделано (этап 1): основная сущность с UUID, номером, статусом, числовым показателем (стоимость),
логическим признаком (оплачено), датами, `version` и архивом; CRUD с архивом и восстановлением;
поиск, 9 фильтров, сортировка по 8 полям, серверная пагинация; справочник мест с включением
и отключением; аналитика; Bean Validation; единый JSON ошибок; correlation ID; Flyway; Swagger;
Docker Compose с healthcheck; тесты (контекст, бизнес-правила, фильтр).

Осталось (этап 2): авторизация (JWT, refresh-сессии, три роли, BCrypt, блокировка перебора паролей),
теги и вычисляемый показатель, заметки к брони, массовые операции, импорт/экспорт CSV,
администрирование пользователей, журнал аудита.
