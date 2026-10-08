package ru.mirea.cyberclub.ui.controller;

import java.math.BigDecimal;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.service.StationService;
import ru.mirea.cyberclub.ui.util.FormInput;

/**
 * Форма игрового места (station-form.fxml): добавление и редактирование.
 */
public class StationFormController extends FormController<Station> {

    private static final BigDecimal MIN_RATE = new BigDecimal("1");
    private static final BigDecimal MAX_RATE = new BigDecimal("100000");

    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<StationType> typeCombo;
    @FXML
    private TextField rateField;
    @FXML
    private TextArea specsArea;
    @FXML
    private CheckBox activeCheck;

    private final StationService stationService;
    private Station editing;

    public StationFormController(AppContext context) {
        this.stationService = context.stations();
    }

    @FXML
    private void initialize() {
        typeCombo.setItems(FXCollections.observableArrayList(StationType.values()));
        activeCheck.setSelected(true);
        registerField("name", nameField);
        registerField("type", typeCombo);
        registerField("hourlyRate", rateField);
        registerField("specs", specsArea);
    }

    public void edit(Station station) {
        this.editing = station;
        nameField.setText(station.getName());
        typeCombo.setValue(station.getType());
        rateField.setText(station.getHourlyRate().toPlainString());
        specsArea.setText(station.getSpecs());
        activeCheck.setSelected(station.isActive());
    }

    @Override
    public String title() {
        return editing == null ? "Новое игровое место" : "Изменение места " + editing.getName();
    }

    @Override
    protected Station submit() {
        Station station = new Station(
                FormInput.text(nameField, "name", "Название", true, 20),
                FormInput.selected(typeCombo, "type", "Тип"),
                FormInput.decimal(rateField, "hourlyRate", "Тариф, руб./ч", MIN_RATE, MAX_RATE),
                FormInput.text(specsArea, "specs", "Характеристики", false, 255),
                activeCheck.isSelected());
        if (editing == null) {
            return stationService.create(station);
        }
        station.setId(editing.getId());
        return stationService.update(station);
    }
}
