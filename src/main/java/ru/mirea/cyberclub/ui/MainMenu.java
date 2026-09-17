package ru.mirea.cyberclub.ui;

import ru.mirea.cyberclub.service.StatisticsService;

/**
 * Главное меню системы. Подменю передаются готовыми объектами
 * (внедрение зависимостей через конструктор).
 */
public class MainMenu extends AbstractMenu {

    public static final String CLUB_NAME = "КИБЕРСПОРТИВНЫЙ КЛУБ \"CYBERCLUB\"";

    private final StatisticsService statisticsService;
    private final TablePrinter tables;

    public MainMenu(ConsoleIO io, ClientMenu clientMenu, StationMenu stationMenu, BookingMenu bookingMenu,
                    SearchMenu searchMenu, FilterMenu filterMenu, SortMenu sortMenu,
                    StatisticsService statisticsService, ExportMenu exportMenu, DbTablesMenu dbTablesMenu,
                    ThemeMenu themeMenu) {
        super(io, CLUB_NAME);
        this.statisticsService = statisticsService;
        this.tables = new TablePrinter(io);
        add("Клиенты", clientMenu::run);
        add("Игровые места", stationMenu::run);
        add("Бронирования", bookingMenu::run);
        add("Поиск бронирований", searchMenu::run);
        add("Фильтрация бронирований", filterMenu::run);
        add("Сортировка бронирований", sortMenu::run);
        add("Статистика", this::statistics);
        add("Экспорт данных", exportMenu::run);
        add("Вывести таблицы базы данных", dbTablesMenu::run);
        add("Тема оформления", themeMenu::run);
    }

    @Override
    protected String exitLabel() {
        return "Выход";
    }

    @Override
    protected void printTitle() {
        io.printBanner(CLUB_NAME);
    }

    private void statistics() {
        io.printHeader("--- Статистика системы ---");
        tables.printIndicators(statisticsService.toIndicators(statisticsService.collect()));
    }
}
