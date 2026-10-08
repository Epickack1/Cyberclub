package ru.mirea.cyberclub.ui.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Function;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import javafx.util.StringConverter;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Фабрики ячеек таблиц и списков. Ячейка хранит «сырое» значение (LocalDateTime, BigDecimal,
 * Duration), поэтому встроенная сортировка TableView сравнивает именно значения,
 * а форматирование для человека выполняется только при отрисовке.
 */
public final class Cells {

    private Cells() {
    }

    /** Ячейка, которая показывает значение через функцию форматирования. */
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> formatted(Function<T, String> format) {
        return column -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        };
    }

    public static <S> Callback<TableColumn<S, LocalDateTime>, TableCell<S, LocalDateTime>> dateTime() {
        return formatted(DateTimeUtil::format);
    }

    public static <S> Callback<TableColumn<S, LocalDate>, TableCell<S, LocalDate>> date() {
        return formatted(DateTimeUtil::format);
    }

    /** Суммы выравниваются по правому краю (CSS-класс numeric), чтобы разряды стояли друг под другом. */
    public static <S> Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> money() {
        Callback<TableColumn<S, BigDecimal>, TableCell<S, BigDecimal>> factory = formatted(DateTimeUtil::formatMoney);
        return column -> {
            TableCell<S, BigDecimal> cell = factory.call(column);
            cell.getStyleClass().add("numeric");
            return cell;
        };
    }

    public static <S> Callback<TableColumn<S, Duration>, TableCell<S, Duration>> duration() {
        return formatted(DateTimeUtil::formatDuration);
    }

    /** Статус в виде цветной «плашки»; цвет задаётся CSS-классом status-created, status-active и т.д. */
    public static <S> Callback<TableColumn<S, BookingStatus>, TableCell<S, BookingStatus>> statusBadge() {
        return column -> new TableCell<>() {
            private final Label badge = new Label();

            @Override
            protected void updateItem(BookingStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setGraphic(null);
                    return;
                }
                badge.setText(status.getTitle());
                badge.getStyleClass().setAll("status-badge", styleClass(status));
                setGraphic(badge);
            }
        };
    }

    public static String styleClass(BookingStatus status) {
        return "status-" + status.name().toLowerCase(Locale.ROOT);
    }

    // ----------------------------------------------------------- выпадающие списки

    public static String clientText(Client client) {
        return client == null ? "" : client.getNickname() + " — " + client.getFullName();
    }

    public static String stationText(Station station) {
        if (station == null) {
            return "";
        }
        return station.getName() + " · " + station.getType().getTitle() + " · "
                + DateTimeUtil.formatMoney(station.getHourlyRate()) + "/ч"
                + (station.isActive() ? "" : " (на обслуживании)");
    }

    /** Показывает объекты в ComboBox через функцию toText (поиск по первым буквам тоже работает). */
    public static <T> void display(ComboBox<T> comboBox, Function<T, String> toText) {
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(T item) {
                return item == null ? "" : toText.apply(item);
            }

            @Override
            public T fromString(String text) {
                return null;
            }
        });
    }

    /**
     * ComboBox фильтра с пунктом «Все …»: значение null означает «не фильтровать».
     * Первым элементом списка добавляется null, ячейки показывают его как allText.
     */
    public static <T> void withAllOption(ComboBox<T> comboBox, String allText, Function<T, String> toText) {
        comboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item == null ? allText : toText.apply(item));
            }
        });
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? allText : toText.apply(item));
            }
        });
    }
}
