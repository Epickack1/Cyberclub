# CyberClub — информационная система киберспортивного клуба

Контрольная работа №1 по дисциплине «Разработка информационных систем на Java».
Консольное приложение на **Java 21 + JDBC + PostgreSQL**, реализующее учёт клиентов,
игровых мест и **бронирований игровых мест** (основная сущность варианта
«Киберспортивный клуб»). Проект сквозной: эта же модель и база данных используются
в КР2 (JavaFX), КР3 (Spring + REST API) и КР4 (Android).

| | |
|---|---|
| **Группа** | _ЭФБО-15-24_ |
| **Вариант** | Киберспортивный клуб — бронирование игрового места |
| **Команда** | 1. Двуреченский Всеволод Владимирович · 2.  · 3. _ФИО_ · 4. _ФИО_ |

## Содержание

1. [Возможности системы](#возможности-системы)
2. [Архитектура и структура проекта](#архитектура-и-структура-проекта)
3. [Модель данных](#модель-данных)
4. [Бизнес-правила](#бизнес-правила)
5. [Инструкция по запуску](#инструкция-по-запуску)
6. [Экспорт данных](#экспорт-данных)
7. [Темы оформления](#темы-оформления)
8. [Тесты](#тесты)
9. [Что сдаётся на защиту](#что-сдаётся-на-защиту)
10. [Распределение ролей на защите](#распределение-ролей-на-защите)

## Возможности системы

```
================================================
        КИБЕРСПОРТИВНЫЙ КЛУБ "CYBERCLUB"
================================================
1. Клиенты                      CRUD, поиск по нику/ФИО
2. Игровые места                CRUD, перевод на обслуживание, список по типу
3. Бронирования                 создать / все / по ID / изменить / сменить статус / удалить / предстоящие
4. Поиск бронирований           по нику клиента, названию места, дате, комментарию
5. Фильтрация бронирований      по статусу, типу места, периоду, клиенту, диапазону стоимости
6. Сортировка бронирований      по времени начала, стоимости, статусу, нику клиента, длительности
7. Статистика                   13 показателей (клиенты, места, брони по статусам, выручка, лидеры...)
8. Экспорт данных               Excel (.xlsx, Apache POI) и CSV
9. Вывести таблицы базы данных  структура (DatabaseMetaData) и содержимое таблиц
10. Тема оформления             светлая / тёмная / без цвета
0. Выход
```

После каждой операции программа возвращается в текущее меню. Любой некорректный ввод
(текст вместо числа, неверная дата, несуществующий ID, нарушение бизнес-правила,
ошибка БД) выводится сообщением `Ошибка: ...` и не завершает программу.

## Архитектура и структура проекта

Четыре слоя, связанные через интерфейсы:

```
Console UI (ui/)  ->  Service (service/)  ->  Repository / JDBC (repository/jdbc/)  ->  PostgreSQL
   меню, ввод         бизнес-правила          PreparedStatement, ResultSet             таблицы, ограничения
```

```
cyberclub/
├── pom.xml                      Maven: зависимости (postgresql, poi-ooxml, jansi, junit) и сборка fat-jar
├── mvnw.cmd / mvnw              Maven Wrapper — Maven устанавливать не нужно
├── run.cmd / run.sh             запуск одной командой (собирает jar при первом запуске)
├── db/
│   ├── 01_schema.sql            схема БД: таблицы, PK/FK/UNIQUE/NOT NULL/CHECK/EXCLUDE, индексы
│   ├── 02_seed.sql              тестовые данные: 6 клиентов, 8 мест, 15 бронирований
│   └── init.ps1                 создание базы и загрузка скриптов через psql
├── docker-compose.yml           PostgreSQL в Docker (альтернатива локальной установке)
├── docs/
│   ├── er-diagram.md / .svg     ER-диаграмма базы данных
│   ├── ЗАЩИТА.md                ответы на вопросы защиты со ссылками на код
│   └── export/cyberclub_export.xlsx   образец экспортированного Excel-файла
└── src/
    ├── main/java/ru/mirea/cyberclub/
    │   ├── Main.java                    точка входа: кодировка консоли, ANSI, проверка БД
    │   ├── model/                       Client, Station, Booking (наследуют BaseEntity),
    │   │                                enum StationType, enum BookingStatus, record Statistics
    │   ├── repository/                  интерфейсы CrudRepository<T,ID>, ClientRepository,
    │   │   │                            StationRepository, BookingRepository, StatisticsRepository, SchemaInspector
    │   │   └── jdbc/                    JDBC-реализации + AbstractJdbcRepository + SqlErrorTranslator
    │   ├── service/                     ClientService, StationService, BookingService (бизнес-правила),
    │   │                                StatisticsService, enum BookingSortField
    │   ├── exception/                   AppException и наследники: EntityNotFoundException, ValidationException,
    │   │                                BusinessRuleException, DataAccessException, ExportException, InputCancelledException
    │   ├── export/                      интерфейс Exporter, ExcelExporter, CsvExporter, ExportData
    │   ├── ui/                          ConsoleIO (безопасный ввод), TablePrinter, AbstractMenu и меню,
    │   │                                ConsoleTheme (enum тем), ThemePreferences, ConsoleApp (сборка слоёв)
    │   └── util/                        AppConfig, DatabaseManager, DateTimeUtil
    ├── main/resources/db.properties     параметры подключения к БД
    └── test/java/...                    JUnit 5: тесты бизнес-правил на in-memory репозиториях
```

## Модель данных

```
clients (1) ──< bookings >── (1) stations
```

| Таблица | Назначение | Ключевые ограничения |
|---|---|---|
| `clients` | клиенты клуба | `nickname`, `phone`, `email` — UNIQUE; CHECK на формат ника, телефона, email, дату рождения |
| `stations` | игровые места (ПК, VIP, буткемп, консоль) | `name` UNIQUE; `type` CHECK IN (STANDARD, VIP, BOOTCAMP, CONSOLE); `hourly_rate > 0` |
| `bookings` | **бронирования** — основная сущность | FK `client_id -> clients.id`, FK `station_id -> stations.id` (ON DELETE RESTRICT); `status` CHECK IN (CREATED, CONFIRMED, ACTIVE, COMPLETED, CANCELLED); `end_time > start_time`; EXCLUDE — неотменённые брони одного места не пересекаются |

Перечисления предметной области:

* `StationType` — `STANDARD`, `VIP`, `BOOTCAMP`, `CONSOLE` (VIP и буткемп — только для 18+);
* `BookingStatus` — `CREATED -> CONFIRMED -> ACTIVE -> COMPLETED`, из `CREATED`/`CONFIRMED` возможна отмена `CANCELLED`; переходы описаны в самом enum (`canTransitionTo`).

ER-диаграмма: [docs/er-diagram.svg](docs/er-diagram.svg) (исходник в Mermaid — [docs/er-diagram.md](docs/er-diagram.md)).

## Бизнес-правила

Реализованы в слое `service` (не в интерфейсе) и продублированы ограничениями БД там, где это возможно.

| № | Правило | Где реализовано |
|---|---|---|
| 1 | Клиент и игровое место в брони должны существовать | `BookingService.requireClient/requireStation` → `EntityNotFoundException` |
| 2 | Нельзя бронировать место на обслуживании (`active = false`) | `BookingService.checkStationActive` |
| 3 | Интервал корректен: конец позже начала, длительность 30 мин – 12 ч, новая бронь не в прошлом | `BookingService.validateInterval`, CHECK `chk_bookings_time` |
| 4 | Брони одного места не пересекаются (отменённые не учитываются, смежные интервалы допустимы) | `BookingService.checkOverlap` + `BookingRepository.findOverlapping`, EXCLUDE `excl_bookings_overlap` |
| 5 | Смена статуса только по схеме переходов | `BookingStatus.canTransitionTo`, `BookingService.changeStatus` |
| 6 | Менять место/время можно только у CREATED/CONFIRMED; удалять нельзя ACTIVE | `BookingService.getEditable`, `BookingService.delete` |
| 7 | Ночное время (22:00–06:00) и зоны VIP/буткемп — только клиентам 18+ | `BookingService.checkAgeRestrictions`, `touchesNight` |
| 8 | Клиент: ник обязателен и уникален (3–20 симв., латиница/цифры/_), телефон `+7XXXXXXXXXX`, корректный email, возраст ≥ 12; нельзя удалить клиента с незавершёнными бронями | `ClientService.validate/checkUnique/delete` |
| 9 | Стоимость не вводится вручную: часы (округление вверх до 30 мин) × тариф места | `BookingService.calculatePrice` |
| 10 | Место с историей бронирований нельзя удалить — только перевести на обслуживание | `StationService.delete`, FK `ON DELETE RESTRICT` |

## Инструкция по запуску

### Требования

* **JDK 21** (проверка: `java -version`; переменная `JAVA_HOME` должна указывать на JDK 21);
* **PostgreSQL 14+** (локально или в Docker);
* интернет при первой сборке — Maven Wrapper скачает Maven и библиотеки (~35 МБ);
* Maven и IDE устанавливать не обязательно.

### Шаг 1. База данных

**Вариант А — локальный PostgreSQL (Windows).** Запустите службу PostgreSQL (`services.msc`
или от администратора `net start postgresql-x64-18`) и создайте базу:

```bash
powershell -ExecutionPolicy Bypass -File db\init.ps1
```

Скрипт создаёт базу `cyberclub`, выполняет `01_schema.sql` и `02_seed.sql`. Если путь к PostgreSQL
или пользователь отличаются: `-PgBin "C:\Program Files\PostgreSQL\16\bin" -User postgres`.
При запросе пароля вводится пароль пользователя `postgres`, заданный при установке.

Вручную то же самое: `psql -U postgres -c "CREATE DATABASE cyberclub"`, затем
`psql -U postgres -d cyberclub -f db/01_schema.sql` и `... -f db/02_seed.sql`.

**Вариант Б — Docker.**

```bash
docker compose up -d
```

Контейнер сам применит оба SQL-скрипта. Пароль пользователя `postgres` в контейнере — `postgres`.

### Шаг 2. Параметры подключения

По умолчанию: `jdbc:postgresql://localhost:5432/cyberclub`, пользователь `postgres`, пустой пароль
(файл `src/main/resources/db.properties`). Если у вас другой пароль или порт — задайте переменные
окружения, пересобирать проект не нужно:

```bash
set CYBERCLUB_DB_PASSWORD=postgres
```

(также `CYBERCLUB_DB_URL`, `CYBERCLUB_DB_USER`; в PowerShell — `$env:CYBERCLUB_DB_PASSWORD="postgres"`).

### Шаг 3. Запуск

```bash
run.cmd
```

Скрипт при первом запуске собирает `target/cyberclub.jar` (Maven Wrapper), переключает консоль
в UTF-8 и запускает приложение. На Linux/macOS — `./run.sh`.

Эквивалент вручную:

```bash
mvnw.cmd -q -DskipTests package
```

```bash
java -Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8 -jar target\cyberclub.jar
```

Ключ `--no-color` (или переменная `NO_COLOR=1`) отключает цветовые темы. Рекомендуемые терминалы —
Windows Terminal или встроенный терминал VS Code; в классическом `cmd.exe` цвета включаются
автоматически через библиотеку Jansi.

**Из IDE:** откройте папку проекта как Maven-проект (IntelliJ IDEA / VS Code с Extension Pack for Java)
и запустите класс `ru.mirea.cyberclub.Main`. В IntelliJ в конфигурации запуска добавьте
VM options `-Dstdout.encoding=UTF-8 -Dstdin.encoding=UTF-8`.

### Проверка

```bash
mvnw.cmd test
```

Запускает 28 модульных тестов бизнес-правил (база данных для них не нужна).

## Экспорт данных

Пункт меню «8. Экспорт данных» создаёт в папке `export/` (рядом с jar):

* `cyberclub_export_<дата_время>.xlsx` — листы «Клиенты», «Игровые места», «Бронирования», «Статистика»
  (Apache POI: жирные заголовки, автофильтр, закреплённая строка, числовые и датовые форматы);
* `cyberclub_csv_<дата_время>/` — четыре CSV-файла (разделитель `;`, UTF-8 с BOM — открываются в Excel).

Образец: [docs/export/cyberclub_export.xlsx](docs/export/cyberclub_export.xlsx).

## Темы оформления

Фирменная палитра клуба; в КР2 (JavaFX) используются те же цвета.

| Тема | Фон | Текст |
|---|---|---|
| Светлая | оранжевый `#FF8C00` (255, 140, 0) | фиолетовый `#4B0082` (75, 0, 130) |
| Тёмная (по умолчанию) | чёрный `#000000` | светло-голубой `#87CEFA` (135, 206, 250) |

Тема переключается в меню «10. Тема оформления» и запоминается в `%USERPROFILE%\.cyberclub.properties`.
Реализация — enum `ui/ConsoleTheme` с 24-битными ANSI-кодами.

## Тесты

`src/test/java` — JUnit 5. Репозитории заменены in-memory реализациями тех же интерфейсов
(`InMemoryClientRepository`, `InMemoryStationRepository`, `InMemoryBookingRepository`), время
зафиксировано через `java.time.Clock`. Покрыты: схема переходов статусов, все бизнес-правила
бронирования, расчёт стоимости, валидация клиентов и мест, сортировка и фильтры на Stream API.

## Что сдаётся на защиту

| Требование | Где |
|---|---|
| Исходный код Java-проекта | `src/main/java` |
| Файл `pom.xml` | `pom.xml` |
| SQL-скрипт создания базы данных | `db/01_schema.sql`, `db/02_seed.sql` |
| ER-диаграмма базы данных | `docs/er-diagram.svg`, `docs/er-diagram.md` |
| Экспортированный Excel-файл | `docs/export/cyberclub_export.xlsx` |
| Инструкция по запуску | этот README, раздел «Инструкция по запуску» |
| Рабочее консольное приложение | `run.cmd` → `target/cyberclub.jar` |
| Ответы на вопросы защиты | `docs/ЗАЩИТА.md` |

## Распределение ролей на защите

Каждый участник должен понимать проект целиком, но за пояснение «своего» слоя отвечает:

| Участник | Слой | Что рассказывает |
|---|---|---|
| 1 | Модель и БД | `model/`, `db/*.sql`, ER-диаграмма: сущности, enum, связи таблиц, ограничения PK/FK/UNIQUE/CHECK/EXCLUDE |
| 2 | Доступ к данным | `util/DatabaseManager`, `repository/`: интерфейсы, JDBC, Connection/PreparedStatement/ResultSet, try-with-resources, Statement vs PreparedStatement, перевод SQL-ошибок |
| 3 | Бизнес-логика | `service/`, `exception/`, тесты: бизнес-правила, собственные исключения, коллекции и Stream API (сортировка, фильтры, статистика) |
| 4 | Интерфейс и экспорт | `ui/`, `export/`, `Main`: меню, обработка некорректного ввода, полиморфизм (AbstractMenu, Exporter), Apache POI, темы, инструкция по запуску |
