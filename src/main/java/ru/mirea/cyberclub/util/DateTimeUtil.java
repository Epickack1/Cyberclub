package ru.mirea.cyberclub.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Форматы дат, времени и денег, единые для консоли и экспорта.
 */
public final class DateTimeUtil {

    public static final String DATE_PATTERN = "dd.MM.yyyy";
    public static final String DATE_TIME_PATTERN = "dd.MM.yyyy HH:mm";

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern(DATE_PATTERN);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    private static final DecimalFormat MONEY;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        MONEY = new DecimalFormat("#,##0.00", symbols);
    }

    private DateTimeUtil() {
    }

    public static String format(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATE_TIME.format(dateTime);
    }

    /** Длительность вида «2 ч 30 мин». */
    public static String formatDuration(Duration duration) {
        long minutes = duration.toMinutes();
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours == 0) {
            return rest + " мин";
        }
        return rest == 0 ? hours + " ч" : hours + " ч " + rest + " мин";
    }

    public static String formatMinutes(double minutes) {
        return formatDuration(Duration.ofMinutes(Math.round(minutes)));
    }

    /** Сумма вида «1 200.00 ₽». */
    public static String formatMoney(BigDecimal amount) {
        return amount == null ? "" : MONEY.format(amount) + " руб.";
    }
}
