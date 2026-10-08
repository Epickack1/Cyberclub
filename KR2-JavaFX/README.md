# CyberClub — JavaFX (КР2)

Настольное приложение для киберспортивного клуба: учёт **клиентов**, **игровых мест** и **бронирований**.
Продолжение КР1 (консольное приложение [Epickack1/Cyberclub](https://github.com/Epickack1/Cyberclub)):
та же предметная область, та же база PostgreSQL `cyberclub`, те же бизнес-правила — но вместо консольного
меню графический интерфейс на JavaFX (FXML + CSS) с отдельным JDBC-подключением к базе.

```
FXML (View) → Controller → Service (бизнес-правила) → Repository / DAO (SQL через JDBC) → PostgreSQL
```

## Что нужно

- JDK 21 (Maven ставить не обязательно — в проекте есть Maven Wrapper `mvnw.cmd`);
- PostgreSQL с базой `cyberclub` из КР1;
- переменная окружения `CYBERCLUB_DB_PASSWORD` — пароль пользователя `postgres` (в коде и в репозитории пароля нет).

## Запуск

1. База данных (если её ещё нет или нужно вернуть тестовые данные):

   ```bash
   powershell -ExecutionPolicy Bypass -File db\init.ps1
   ```

   Скрипт создаёт базу `cyberclub`, таблицы (`db/01_schema.sql`) и тестовые данные (`db/02_seed.sql`):
   6 клиентов, 8 мест, 15 бронирований во всех статусах.

2. Пароль к базе (один раз на сеанс терминала, если он не задан в системе):

   ```bash
   set CYBERCLUB_DB_PASSWORD=ваш_пароль
   ```

3. Приложение:

   ```bash
   run.cmd
   ```

   или `mvnw.cmd javafx:run`. В IntelliJ IDEA: открыть папку как Maven-проект и запустить класс
   `ru.mirea.cyberclub.Launcher`.

Параметры подключения лежат в `src/main/resources/database.properties`; любой можно переопределить
переменными `CYBERCLUB_DB_URL`, `CYBERCLUB_DB_USER`, `CYBERCLUB_DB_PASSWORD`.

Тесты: `mvnw.cmd test` (38 тестов: бизнес-правила КР1, фильтр бронирований, экспорт, статистика).

## Что умеет

| Требование КР2 | Где реализовано |
|---|---|
| Главное окно со всеми функциями | `main-view.fxml` + `MainController`: меню, 4 вкладки, строка состояния |
| TableView основной сущности | вкладка «Бронирования», `bookings-tab.fxml` / `BookingsController` |
| ObservableList → FilteredList → SortedList | `BookingsController`, `ClientsController`, `StationsController` |
| Добавление / редактирование / удаление | диалоги `booking-form.fxml`, `client-form.fxml`, `station-form.fxml`; удаление — с подтверждением |
| Связанные сущности | клиент и место выбираются в ComboBox; карточка брони показывает клиента и место; на вкладке «Клиенты» — брони клиента |
| Поиск по нескольким полям | № брони (`#12`), ник, ФИО клиента, место, комментарий — `service/BookingFilter` |
| Фильтры (5 шт.) | статус, зона, клиент, период «с — по» |
| Сортировка | клик по заголовку любого столбца или список «Сортировка» (5 вариантов) + направление |
| Валидация | `ui/util/FormInput` (заполненность, числа, даты, время, длина) + сервисы КР1 (форматы, уникальность, возраст, пересечения, статусы) |
| Понятные ошибки | сообщение под формой + подсветка неверного поля; `Dialogs` — без stack trace |
| Статистика (≥ 5 показателей) | вкладка «Статистика»: 9 показателей клуба (SQL) + 7 по текущей выборке (Stream API) + 2 диаграммы |
| Экспорт .xlsx / .csv | кнопка «Экспорт…» / Ctrl+E: текущая выборка, путь через FileChooser |
| FXML и CSS | `src/main/resources/ru/mirea/cyberclub/ui/view/*.fxml`, `ui/css/app.css` |

## Структура

```
src/main/java/ru/mirea/cyberclub/
├── CyberClubApp.java, Launcher.java   запуск JavaFX, проверка подключения к БД
├── AppContext.java                    сборка DAO и сервисов, загрузка FXML, фабрика контроллеров
├── db/                                DatabaseConfig (database.properties + env), DatabaseManager (JDBC)
├── model/                             Client, Station, Booking, статусы и типы (из КР1)
├── repository/ + repository/jdbc/     интерфейсы DAO и их JDBC-реализации, весь SQL здесь (из КР1)
├── service/                           бизнес-правила (из КР1) + BookingFilter, ExportService
├── export/                            CsvExporter, ExcelExporter
├── exception/                         ValidationException (с ключом поля), BusinessRuleException, ...
├── ui/controller/                     контроллеры FXML: главное окно, вкладки, формы
└── ui/util/                           диалоги, фоновые задачи, разбор ввода, ячейки таблиц
src/main/resources/
├── database.properties
└── ru/mirea/cyberclub/ui/view/*.fxml, ui/css/app.css
db/                                    SQL-скрипты базы из КР1 и init.ps1
docs/ЗАЩИТА_КР2.md                     ответы на вопросы защиты и сценарий демонстрации
```

## Готовая система из архива (Docker)

По заданию КР2 нужно хотя бы раз развернуть пример из архива `student-hub-suite`
(PostgreSQL + Spring Boot в Docker, затем их JavaFX-клиент). Порядок — в `docs/ЗАЩИТА_КР2.md`, раздел «Docker».
