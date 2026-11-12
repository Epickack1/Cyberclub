# CyberClub — информационная система киберспортивного клуба (КР3)

Backend на Spring Boot + веб-интерфейс (HTML/CSS/JS) для бронирования игровых мест.
Продолжение КР1/КР2: та же предметная область (клиенты, игровые места, бронирования) и те же
бизнес-правила. Сервером пользуются сайт, JavaFX-клиент (КР2) и Android-приложение (КР4).

```
Сайт / JavaFX / Android / Swagger ──HTTP/JSON + JWT──> Controller → Service → Repository (JPA) → PostgreSQL
```

## Запуск на новом компьютере

Нужен только **Docker Desktop** (Java и PostgreSQL ставить не нужно — всё в контейнерах).

1. Распаковать архив, открыть папку `KR3-backend`.
2. Дважды щёлкнуть **`start.cmd`**.
   - при первом запуске создаётся `.env` со случайными паролями;
   - поднимаются PostgreSQL и backend (первый раз 5–10 минут: Maven скачивает зависимости, нужен интернет);
   - открывается сайт, в окне выводится email и временный пароль администратора.
3. Остановить — **`stop.cmd`** (данные в базе сохраняются).

То же вручную:

```bash
copy .env.example .env          # задать POSTGRES_PASSWORD, BOOTSTRAP_ADMIN_PASSWORD, JWT_SECRET_BASE64
docker compose --env-file .env up -d --wait
docker compose --env-file .env ps        # у db и backend должно быть healthy
docker compose --env-file .env logs -f backend
docker compose --env-file .env down      # без -v, иначе база удалится
```

| Что | Адрес |
|---|---|
| Сайт | http://localhost:8081/ |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| OpenAPI JSON | http://localhost:8081/v3/api-docs |
| Health | http://localhost:8081/actuator/health |
| PostgreSQL | `localhost:55433`, база `cyberclub`, пользователь `cyberclub_app` (пароль в `.env`) |

Порты меняются в `.env` (`APP_PORT`, `POSTGRES_PORT`). Файл `.env` с паролями в git и в архив не кладётся.

Тесты (нужен JDK 21): `mvnw.cmd test` — 31 тест на H2, Docker не нужен.

## Пользователи и роли

| Роль | Что может |
|---|---|
| `CLIENT` | регистрируется сам на сайте; «Мои брони»: бронировать место, видеть занятое время, отменять свою бронь |
| `VIEWER` | смотреть клиентов, места, брони, аналитику |
| `OPERATOR` | + создавать и менять клиентов и брони, заметки, массовые операции, импорт CSV, подтверждение оплаты |
| `ADMIN` | + справочник мест, окончательное удаление, пользователи системы, журнал аудита |

- **Первый администратор** создаётся из `.env` (`BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`),
  пароль временный — при первом входе нужно задать свой.
- **Клиент** регистрируется сам («Зарегистрироваться» на странице входа). Его брони всегда
  неоплаченные, оплату подтверждает сотрудник кнопкой «₽ Подтвердить оплату».
- Сотрудников заводит администратор («Пользователи»): сервер выдаёт временный пароль,
  до его смены доступен только `/api/auth/*` (иначе 403 `PASSWORD_CHANGE_REQUIRED`).

В одном браузере можно работать под разными аккаунтами в разных вкладках (Ctrl+T, не «дублировать вкладку»):
сессия хранится в `sessionStorage` вкладки.

Защита: BCrypt (cost 12); JWT 15 минут + refresh 14 дней с ротацией, в базе только SHA-256 refresh-токена;
повторное использование старого refresh закрывает все сессии пользователя; 5 неверных паролей подряд —
блокировка на 15 минут (423); одинаковое сообщение при неверном email и пароле; права перечитываются
из базы при каждом запросе; нельзя отключить себя или оставить систему без администратора.

## Что реализовано

| Требование | Где |
|---|---|
| Основная сущность «Бронирование»: UUID, номер, статус, стоимость, «оплачено», даты, `version`, архив | `domain/Booking`, миграции `V1`, `V5` |
| CRUD, архив вместо удаления, восстановление, окончательное удаление (ADMIN) | `BookingController`, `BookingService` |
| Поиск, фильтры, сортировка, серверная пагинация | `filter/BookingSpecifications`, `web/PageableFactory` |
| Справочник игровых мест | `StationController` |
| Вычисляемый показатель «риск неоплаты» (Нет/Низкий/Средний/Высокий) | `domain/RiskLevel.of`, пересчёт в `@PrePersist/@PreUpdate` |
| Теги (до 10, кириллица, фильтр по тегу) | `Booking.tags`, `service/Tags`, таблица `booking_tags` |
| Заметки к брони (автор, закрепление; править может автор или ADMIN) | `NoteController`, `NoteService` |
| Массовые операции (архив, статус, теги, оплата) с отчётом по каждой записи | `BulkBookingService` |
| Экспорт CSV и импорт с предпросмотром (без записи) и применением | `CsvBookingService` |
| Аналитика (по статусам, местам, риску, выручка) | `AnalyticsService` |
| Авторизация JWT, роли, администрирование пользователей, временные пароли | `security/*`, `AdminUserController` |
| Журнал аудита с correlation ID и IP | `AuditService`, `AuditController` |
| Регистрация клиентов и кабинет «Мои брони» | `RegistrationController`, `ClientSelfController` |

Плюс: Flyway V1–V5 (`ddl-auto: validate`), единый JSON ошибок `ApiError`, Bean Validation,
оптимистичная блокировка (409), Docker Compose с healthcheck, Swagger с кнопкой Authorize, тесты.

## REST API

| Назначение | Метод и адрес |
|---|---|
| Вход / обновление токенов / выход | `POST /api/auth/login`, `/refresh`, `/logout` |
| Регистрация клиента (сразу вход) | `POST /api/auth/register` |
| Кто я / смена пароля | `GET /api/auth/me`, `POST /api/auth/password` |
| Брони: список с фильтрами | `GET /api/bookings` |
| Карточка / по номеру | `GET /api/bookings/{id}`, `GET /api/bookings/number/{number}` |
| Создание / изменение (с `version`) / статус | `POST /api/bookings`, `PUT /api/bookings/{id}`, `POST /api/bookings/{id}/status` |
| Архив / восстановление / окончательное удаление | `DELETE /api/bookings/{id}`, `POST .../{id}/restore`, `DELETE .../{id}/permanent` |
| Подтверждение оплаты | `POST /api/bookings/{id}/confirm-payment` |
| Массовые операции | `POST /api/bookings/bulk` |
| CSV | `GET /api/bookings/export`, `POST /api/bookings/import/preview`, `POST /api/bookings/import/apply` |
| Заметки | `GET/POST /api/bookings/{id}/notes`, `PUT/DELETE /api/bookings/{id}/notes/{noteId}` |
| Клиенты | `GET/POST /api/clients`, `GET/PUT/DELETE /api/clients/{id}` |
| Игровые места | `GET/POST /api/stations`, `GET/PUT /api/stations/{id}`, `POST /api/stations/{id}/active` |
| Аналитика | `GET /api/analytics/summary` |
| Кабинет клиента | `GET /api/me/client`, `GET/POST /api/me/bookings`, `POST /api/me/bookings/{id}/cancel`, `GET /api/me/stations`, `GET /api/me/busy` |
| Пользователи (ADMIN) | `GET/POST /api/admin/users`, `GET/PUT /api/admin/users/{id}`, `PUT .../{id}/roles`, `POST .../{id}/enabled`, `.../reset-password`, `.../password`, `.../unlock` |
| Журнал аудита (ADMIN) | `GET /api/admin/audit` |

Параметры `GET /api/bookings`: `q` (номер `#12`, ник, ФИО, место, комментарий), `stationId`, `stationType`,
`clientId`, `status`, `paid`, `riskLevel`, `tag`, `archive=ACTIVE|ARCHIVED|ALL`, `from`, `to`,
`priceOp=EQ|GT|GTE|LT|LTE` + `price`, `page`, `size` (до 100),
`sort=number|start|price|status|client|station|created|updated,asc|desc`.

Коды ответов: 400 — неверные данные или нарушено правило клуба, 401 — нужен вход, 403 — нет прав
(или нужно сменить временный пароль), 404 — нет записи, 409 — конфликт версии / занятое место /
повтор уникального поля, 423 — вход заблокирован после перебора паролей.

## Сценарий показа на защите

1. `start.cmd` → `docker compose --env-file .env ps`: оба контейнера `healthy`. Swagger открыт.
2. Вход администратором → сайт требует сменить временный пароль → сменить.
3. «Брони»: поиск по нику, фильтры по статусу, тегу и риску, сортировка, страницы.
4. Карточка брони: изменить, добавить теги, заметку, сменить статус; показать колонку «Риск».
5. Выделить несколько броней → массовое действие → отчёт (успешные и ошибки по каждой).
6. Экспорт CSV → поправить строку → импорт: предпросмотр (ничего не записано) → применить.
7. Архивировать и восстановить бронь; «Аналитика»; «Журнал аудита» — видны все действия.
8. «Пользователи»: создать оператора → выдан временный пароль → войти им в другой вкладке.
9. В новой вкладке: «Зарегистрироваться» → клиент бронирует место (оплата «нет»)
   → администратор нажимает «₽ Подтвердить оплату» → у клиента бронь оплачена, риск «Нет».
10. Swagger: `POST /api/auth/login` → Authorize → любой запрос; без токена — 401.
11. `mvnw.cmd test` — все тесты зелёные.

## Структура

| Пакет | Назначение |
|---|---|
| `controller` | REST-контроллеры |
| `service` | бизнес-логика и транзакции; `BookingRules` — правила бронирования |
| `repository` | Spring Data JPA |
| `domain` | JPA-сущности и enum (`RiskLevel` — вычисляемый показатель) |
| `dto` | входные и выходные модели API (сущности наружу не отдаются) |
| `filter` | безопасный поиск и фильтры через Criteria API |
| `error` | единая обработка ошибок, JSON `ApiError` |
| `config` | OpenAPI, правила доступа `SecurityConfig`, первый администратор, параметры `app.*` |
| `security` | JWT, фильтры `Authorization: Bearer` и обязательной смены пароля, политика паролей |
| `web` | пагинация с белым списком сортировки, correlation ID |
| `resources/static` | веб-интерфейс: `index.html`, `styles.css`, `app.js` |
| `resources/db/migration` | миграции Flyway V1–V5 |
