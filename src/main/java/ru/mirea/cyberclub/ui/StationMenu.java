package ru.mirea.cyberclub.ui;

import java.math.BigDecimal;
import java.util.List;

import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.service.StationService;

/**
 * Меню «Игровые места».
 */
public class StationMenu extends AbstractMenu {

    private final StationService stationService;
    private final TablePrinter tables;

    public StationMenu(ConsoleIO io, StationService stationService) {
        super(io, "Игровые места");
        this.stationService = stationService;
        this.tables = new TablePrinter(io);
        add("Список игровых мест", this::listAll);
        add("Добавить игровое место", this::create);
        add("Найти место по ID", this::findById);
        add("Изменить игровое место", this::update);
        add("Перевести на обслуживание / вернуть в работу", this::toggleActive);
        add("Удалить игровое место", this::delete);
        add("Места по типу", this::listByType);
    }

    private void listAll() {
        tables.printStations(stationService.getAll());
    }

    private void create() {
        io.println("Новое игровое место (пустая строка - отмена):");
        Station station = new Station();
        station.setName(io.readLine("Название (например PC-05):"));
        station.setType(io.readEnum("Тип места:", StationType.values(), StationType::getTitle));
        station.setHourlyRate(io.readMoney("Тариф, руб./час:"));
        station.setSpecs(io.readOptionalLine("Характеристики (необязательно):"));
        station.setActive(true);
        Station saved = stationService.create(station);
        io.printSuccess("Добавлено: " + saved.describe());
    }

    private void findById() {
        long id = io.readLong("Введите ID места:");
        tables.printStations(List.of(stationService.getById(id)));
    }

    private void update() {
        long id = io.readLong("Введите ID места:");
        Station station = stationService.getById(id);
        tables.printStations(List.of(station));
        io.println("Введите новые значения (пустая строка - оставить текущее):");

        String name = io.readOptionalLine("Название [" + station.getName() + "]:");
        if (name != null) {
            station.setName(name);
        }
        StationType type = io.readOptionalEnum("Тип [" + station.getType().getTitle() + "]:",
                StationType.values(), StationType::getTitle);
        if (type != null) {
            station.setType(type);
        }
        BigDecimal rate = io.readOptionalMoney("Тариф, руб./час [" + station.getHourlyRate() + "]:");
        if (rate != null) {
            station.setHourlyRate(rate);
        }
        String specs = io.readOptionalLine("Характеристики [" + (station.getSpecs() == null ? "" : station.getSpecs()) + "]:");
        if (specs != null) {
            station.setSpecs(specs);
        }
        Station saved = stationService.update(station);
        io.printSuccess("Обновлено: " + saved.describe());
    }

    private void toggleActive() {
        long id = io.readLong("Введите ID места:");
        Station station = stationService.getById(id);
        boolean newState = !station.isActive();
        String action = newState ? "вернуть в работу" : "перевести на обслуживание";
        if (!io.confirm("Место " + station.getName() + " сейчас " + (station.isActive() ? "активно" : "на обслуживании")
                + ". " + Character.toUpperCase(action.charAt(0)) + action.substring(1) + "?")) {
            io.println("Действие отменено.");
            return;
        }
        stationService.setActive(id, newState);
        io.printSuccess("Место " + station.getName() + (newState ? " снова доступно для бронирования." : " переведено на обслуживание."));
    }

    private void delete() {
        long id = io.readLong("Введите ID места:");
        Station station = stationService.getById(id);
        if (!io.confirm("Удалить место " + station.getName() + "?")) {
            io.println("Удаление отменено.");
            return;
        }
        stationService.delete(id);
        io.printSuccess("Место #" + id + " удалено.");
    }

    private void listByType() {
        StationType type = io.readEnum("Тип места:", StationType.values(), StationType::getTitle);
        tables.printStations(stationService.getByType(type));
    }
}
