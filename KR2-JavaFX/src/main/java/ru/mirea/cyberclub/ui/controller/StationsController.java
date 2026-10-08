package ru.mirea.cyberclub.ui.controller;

import java.math.BigDecimal;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.ui.util.Cells;
import ru.mirea.cyberclub.ui.util.Dialogs;
import ru.mirea.cyberclub.ui.util.FormDialog;
import ru.mirea.cyberclub.ui.util.FxTasks;

/**
 * Вкладка «Игровые места» (stations-tab.fxml): CRUD мест и перевод на обслуживание.
 */
public class StationsController implements RefreshableTab {

    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");

    @FXML
    private TableView<Station> table;
    @FXML
    private TableColumn<Station, String> nameCol;
    @FXML
    private TableColumn<Station, StationType> typeCol;
    @FXML
    private TableColumn<Station, BigDecimal> rateCol;
    @FXML
    private TableColumn<Station, String> specsCol;
    @FXML
    private TableColumn<Station, String> stateCol;
    @FXML
    private ComboBox<StationType> typeFilter;
    @FXML
    private Button editButton;
    @FXML
    private Button toggleButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Label countLabel;

    private final AppContext context;
    private final ObservableList<Station> master = FXCollections.observableArrayList();
    private final FilteredList<Station> filtered = new FilteredList<>(master);

    public StationsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        column(nameCol, Station::getName);
        column(typeCol, Station::getType);
        column(rateCol, Station::getHourlyRate);
        column(specsCol, Station::getSpecs);
        column(stateCol, s -> s.isActive() ? "Работает" : "На обслуживании");
        rateCol.setCellFactory(Cells.money());

        SortedList<Station> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Игровые места не найдены"));
        table.setRowFactory(view -> {
            TableRow<Station> row = new TableRow<>() {
                @Override
                protected void updateItem(Station station, boolean empty) {
                    super.updateItem(station, empty);
                    pseudoClassStateChanged(INACTIVE, !empty && station != null && !station.isActive());
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    onEdit();
                }
            });
            return row;
        });

        typeFilter.getItems().add(null);
        typeFilter.getItems().addAll(StationType.values());
        Cells.withAllOption(typeFilter, "Все зоны", StationType::getTitle);
        typeFilter.valueProperty().addListener((obs, old, type) -> {
            filtered.setPredicate(station -> type == null || station.getType() == type);
            updateCount();
        });

        var selected = table.getSelectionModel().selectedItemProperty();
        editButton.disableProperty().bind(selected.isNull());
        toggleButton.disableProperty().bind(selected.isNull());
        deleteButton.disableProperty().bind(selected.isNull());
        selected.addListener((obs, old, station) -> toggleButton.setText(
                station != null && !station.isActive() ? "Вернуть в работу" : "На обслуживание"));
    }

    private static <T> void column(TableColumn<Station, T> column, Function<Station, T> getter) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
    }

    @Override
    @FXML
    public void refresh() {
        FxTasks.run(() -> context.stations().getAll(),
                stations -> {
                    master.setAll(stations);
                    updateCount();
                },
                error -> Dialogs.error(owner(), error));
    }

    @FXML
    private void onAdd() {
        try {
            AppContext.View<StationFormController> view = context.load("station-form.fxml");
            FormDialog.show(owner(), view).ifPresent(station -> {
                master.add(station);
                table.getSelectionModel().select(station);
                updateCount();
            });
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    @FXML
    private void onEdit() {
        Station station = table.getSelectionModel().getSelectedItem();
        if (station == null) {
            return;
        }
        try {
            AppContext.View<StationFormController> view = context.load("station-form.fxml");
            view.controller().edit(station);
            FormDialog.show(owner(), view).ifPresent(updated -> {
                master.set(master.indexOf(station), updated);
                table.getSelectionModel().select(updated);
            });
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    /** Место с историей нельзя удалить, но можно временно вывести из работы. */
    @FXML
    private void onToggleActive() {
        Station station = table.getSelectionModel().getSelectedItem();
        if (station == null) {
            return;
        }
        try {
            Station updated = context.stations().setActive(station.getId(), !station.isActive());
            master.set(master.indexOf(station), updated);
            table.getSelectionModel().select(updated);
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    @FXML
    private void onDelete() {
        Station station = table.getSelectionModel().getSelectedItem();
        if (station == null) {
            return;
        }
        if (!Dialogs.confirm(owner(), "Удалить игровое место " + station.getName() + "?",
                "Удалить можно только место, у которого нет ни одного бронирования.\n"
                        + "Если история есть — используйте «На обслуживание». Действие необратимо.")) {
            return;
        }
        try {
            context.stations().delete(station.getId());
            master.remove(station);
            updateCount();
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    private void updateCount() {
        long active = master.stream().filter(Station::isActive).count();
        countLabel.setText("Показано: " + filtered.size() + " из " + master.size() + " · работает: " + active);
    }

    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
