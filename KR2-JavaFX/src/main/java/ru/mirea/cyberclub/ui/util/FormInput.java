package ru.mirea.cyberclub.ui.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextInputControl;
import javafx.util.StringConverter;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Чтение значений из элементов формы с проверкой «на уровне ввода»:
 * заполнено ли поле и можно ли превратить текст в число, дату или время.
 * Правила предметной области (формат телефона, уникальность, возраст и т.д.)
 * проверяют сервисы. Любая ошибка — {@link ValidationException} с ключом поля,
 * чтобы форма подсветила нужный элемент.
 */
public final class FormInput {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private FormInput() {
    }

    /** Единый формат дат дд.мм.гггг для DatePicker независимо от языка системы. */
    public static void setupDatePicker(DatePicker picker) {
        picker.setPromptText("дд.мм.гггг");
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : DATE.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return LocalDate.parse(text.trim(), DATE);
                } catch (DateTimeParseException e) {
                    return null; // некорректный ввод покажет ошибку при сохранении формы
                }
            }
        });
    }

    /** Текст без пробелов по краям; пустая строка — null или ошибка, если поле обязательное. */
    public static String text(TextInputControl control, String field, String label, boolean required) {
        String value = control.getText() == null ? "" : control.getText().trim();
        if (value.isEmpty()) {
            if (required) {
                throw new ValidationException(field, "Поле «" + label + "» обязательно для заполнения.");
            }
            return null;
        }
        return value;
    }

    /** Строка не длиннее max символов. */
    public static String text(TextInputControl control, String field, String label, boolean required, int max) {
        String value = text(control, field, label, required);
        if (value != null && value.length() > max) {
            throw new ValidationException(field, "Поле «" + label + "»: не более " + max
                    + " символов (сейчас " + value.length() + ").");
        }
        return value;
    }

    /** Выбранный элемент выпадающего списка. */
    public static <T> T selected(ComboBox<T> comboBox, String field, String label) {
        T value = comboBox.getValue();
        if (value == null) {
            throw new ValidationException(field, "Выберите значение в поле «" + label + "».");
        }
        return value;
    }

    /**
     * Дата из DatePicker. Текст редактора разбирается вручную: если пользователь ввёл
     * дату с клавиатуры и не нажал Enter, значение DatePicker ещё не обновлено.
     */
    public static LocalDate date(DatePicker picker, String field, String label, boolean required) {
        String text = picker.getEditor().getText() == null ? "" : picker.getEditor().getText().trim();
        if (text.isEmpty()) {
            if (required) {
                throw new ValidationException(field, "Поле «" + label + "» обязательно для заполнения.");
            }
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(text, DATE);
            picker.setValue(date);
            return date;
        } catch (DateTimeParseException e) {
            throw new ValidationException(field, "Поле «" + label + "»: дата «" + text
                    + "» указана неверно. Формат: " + DateTimeUtil.DATE_PATTERN.toLowerCase() + ".");
        }
    }

    /** Время в формате ЧЧ:ММ. */
    public static LocalTime time(TextInputControl control, String field, String label) {
        String text = text(control, field, label, true);
        try {
            return LocalTime.parse(text, TIME);
        } catch (DateTimeParseException e) {
            throw new ValidationException(field, "Поле «" + label + "»: время «" + text
                    + "» указано неверно. Формат: ЧЧ:ММ, например 18:30.");
        }
    }

    /** Число с дробной частью; разделитель — точка или запятая. */
    public static BigDecimal decimal(TextInputControl control, String field, String label) {
        String text = text(control, field, label, true).replace(" ", "").replace(',', '.');
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new ValidationException(field, "Поле «" + label + "»: «" + text + "» — не число.");
        }
    }

    /** Число в диапазоне [min, max]. */
    public static BigDecimal decimal(TextInputControl control, String field, String label,
                                     BigDecimal min, BigDecimal max) {
        BigDecimal value = decimal(control, field, label);
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new ValidationException(field, "Поле «" + label + "»: допустимо от "
                    + min.stripTrailingZeros().toPlainString() + " до " + max.stripTrailingZeros().toPlainString() + ".");
        }
        return value;
    }
}
