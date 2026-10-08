package ru.mirea.cyberclub.ui.controller;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.model.Statistics;
import ru.mirea.cyberclub.service.StatisticsService;
import ru.mirea.cyberclub.ui.util.Dialogs;
import ru.mirea.cyberclub.ui.util.FxTasks;

/**
 * Вкладка «Статистика» (statistics-tab.fxml).
 * Общие показатели считаются агрегирующими SQL-запросами (COUNT, SUM, AVG, GROUP BY),
 * показатели текущей выборки — в Java через Stream API по строкам таблицы бронирований.
 */
public class StatisticsController implements RefreshableTab {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @FXML
    private FlowPane clubCards;
    @FXML
    private FlowPane selectionCards;
    @FXML
    private PieChart statusChart;
    @FXML
    private BarChart<String, Number> revenueChart;
    @FXML
    private Label updatedLabel;

    private final StatisticsService statisticsService;
    private final AppContext context;
    private Supplier<List<Booking>> selection = List::of;

    public StatisticsController(AppContext context) {
        this.context = context;
        this.statisticsService = context.statistics();
    }

    /** Откуда брать «текущую выборку» — строки таблицы на вкладке бронирований. */
    public void setSelectionSupplier(Supplier<List<Booking>> selection) {
        this.selection = Objects.requireNonNull(selection);
    }

    @Override
    @FXML
    public void refresh() {
        updatedLabel.setText("Загрузка…");
        renderCards(selectionCards, statisticsService.forBookings(selection.get()));
        FxTasks.run(() -> new Data(statisticsService.collect(), context.bookings().getAll()),
                data -> {
                    renderCards(clubCards, statisticsService.toIndicators(data.stats()));
                    renderStatusChart(data.stats());
                    renderRevenueChart(statisticsService.revenueByStationType(data.bookings()));
                    updatedLabel.setText("Обновлено в " + LocalTime.now().format(TIME));
                },
                error -> {
                    updatedLabel.setText("Не удалось загрузить статистику");
                    Dialogs.error(clubCards.getScene() == null ? null : clubCards.getScene().getWindow(), error);
                });
    }

    private record Data(Statistics stats, List<Booking> bookings) {
    }

    /** Каждый показатель — карточка: крупное значение и подпись. */
    private static void renderCards(FlowPane pane, List<StatisticsService.Indicator> indicators) {
        pane.getChildren().clear();
        for (StatisticsService.Indicator indicator : indicators) {
            Label value = new Label(indicator.value());
            value.getStyleClass().add("metric-value");
            value.setWrapText(true);
            Label name = new Label(indicator.name());
            name.getStyleClass().add("metric-name");
            name.setWrapText(true);
            VBox card = new VBox(4, value, name);
            card.getStyleClass().add("metric-card");
            pane.getChildren().add(card);
        }
    }

    private void renderStatusChart(Statistics stats) {
        var data = FXCollections.<PieChart.Data>observableArrayList();
        for (BookingStatus status : BookingStatus.values()) {
            long count = stats.countByStatus(status);
            if (count > 0) {
                data.add(new PieChart.Data(status.getTitle() + " (" + count + ")", count));
            }
        }
        statusChart.setData(data);
    }

    private void renderRevenueChart(Map<StationType, BigDecimal> revenue) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Сумма броней, руб. (без отменённых)");
        revenue.forEach((type, sum) -> series.getData().add(new XYChart.Data<>(type.getTitle(), sum)));
        revenueChart.getData().setAll(List.of(series));
    }
}
