package ru.mirea.cyberclub.ui;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.Function;

import ru.mirea.cyberclub.exception.InputCancelledException;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Ввод и вывод в консоли. Все методы чтения защищены от некорректного ввода:
 * при ошибке печатается сообщение и запрос повторяется, программа не завершается.
 * Пустая строка в обязательном поле = отмена текущей операции ({@link InputCancelledException}).
 */
public class ConsoleIO {

    private final Scanner in;
    private final PrintStream out;
    private final boolean colorSupported;
    private ConsoleTheme theme = ConsoleTheme.NONE;

    public ConsoleIO(Scanner in, PrintStream out, boolean colorSupported) {
        this.in = in;
        this.out = out;
        this.colorSupported = colorSupported;
    }

    // ------------------------------------------------------------------ вывод

    public void println(String text) {
        out.println(theme.base() + text + ConsoleTheme.CLEAR_LINE);
    }

    public void println() {
        println("");
    }

    public void print(String text) {
        out.print(theme.base() + text);
        out.flush();
    }

    public void printHeader(String title) {
        println();
        printStyled(theme.accent(), title);
    }

    public void printBanner(String title) {
        String line = "=".repeat(48);
        println();
        printStyled(theme.accent(), line);
        printStyled(theme.accent(), centered(title, 48));
        printStyled(theme.accent(), line);
    }

    public void printError(String message) {
        printStyled(theme.error(), message);
    }

    public void printSuccess(String message) {
        printStyled(theme.success(), message);
    }

    private void printStyled(String style, String text) {
        out.println(style + text + ConsoleTheme.CLEAR_LINE + theme.base());
    }

    private static String centered(String text, int width) {
        int pad = Math.max(0, (width - text.length()) / 2);
        return " ".repeat(pad) + text;
    }

    // ------------------------------------------------------------------- темы

    public boolean isColorSupported() {
        return colorSupported;
    }

    public ConsoleTheme getTheme() {
        return theme;
    }

    /** Включает тему: закрашивает экран фоном темы. */
    public void applyTheme(ConsoleTheme newTheme) {
        this.theme = colorSupported ? newTheme : ConsoleTheme.NONE;
        if (theme.isColored()) {
            out.print(theme.base() + ConsoleTheme.CLEAR_SCREEN);
        } else {
            out.print(ConsoleTheme.RESET);
        }
        out.flush();
    }

    /** Возвращает терминалу исходные цвета (вызывается при выходе). */
    public void resetTheme() {
        if (theme.isColored()) {
            out.print(ConsoleTheme.RESET + ConsoleTheme.CLEAR_SCREEN);
            out.flush();
        }
        theme = ConsoleTheme.NONE;
    }

    // ------------------------------------------------------------------- ввод

    /** Читает строку; пустой ввод — отмена операции. */
    public String readLine(String prompt) {
        String value = readOptionalLine(prompt);
        if (value == null) {
            throw new InputCancelledException();
        }
        return value;
    }

    /** Читает строку; пустой ввод возвращает null (используется при редактировании: «оставить как было»). */
    public String readOptionalLine(String prompt) {
        print(prompt + " ");
        String line;
        try {
            line = in.nextLine();
        } catch (NoSuchElementException e) {
            // поток ввода закрыт (Ctrl+Z / конец файла) — завершаем работу корректно
            throw new InputCancelledException();
        }
        line = line.trim();
        return line.isEmpty() ? null : line;
    }

    /** Выбор пункта меню: целое число от 0 до max, ошибки ввода не прерывают программу. */
    public int readMenuChoice(int max) {
        while (true) {
            String line = readOptionalLine("Выберите действие:");
            if (line == null) {
                continue;
            }
            try {
                int value = Integer.parseInt(line);
                if (value >= 0 && value <= max) {
                    return value;
                }
                printError("Ошибка: введите число от 0 до " + max + ".");
            } catch (NumberFormatException e) {
                printError("Ошибка: пункт меню должен быть целым числом.");
            }
        }
    }

    public long readLong(String prompt) {
        while (true) {
            String line = readLine(prompt);
            try {
                return Long.parseLong(line);
            } catch (NumberFormatException e) {
                printError("Ошибка: " + fieldName(prompt) + " должен быть целым числом.");
            }
        }
    }

    public Long readOptionalLong(String prompt) {
        while (true) {
            String line = readOptionalLine(prompt);
            if (line == null) {
                return null;
            }
            try {
                return Long.parseLong(line);
            } catch (NumberFormatException e) {
                printError("Ошибка: " + fieldName(prompt) + " должен быть целым числом.");
            }
        }
    }

    public BigDecimal readMoney(String prompt) {
        BigDecimal value = readOptionalMoney(prompt);
        if (value == null) {
            throw new InputCancelledException();
        }
        return value;
    }

    public BigDecimal readOptionalMoney(String prompt) {
        while (true) {
            String line = readOptionalLine(prompt);
            if (line == null) {
                return null;
            }
            try {
                return new BigDecimal(line.replace(',', '.').replace(" ", ""));
            } catch (NumberFormatException e) {
                printError("Ошибка: сумма должна быть числом, например 150 или 199.50.");
            }
        }
    }

    public LocalDate readDate(String prompt) {
        while (true) {
            String line = readLine(prompt + " (" + DateTimeUtil.DATE_PATTERN + "):");
            try {
                return LocalDate.parse(line, DateTimeUtil.DATE);
            } catch (DateTimeParseException e) {
                printError("Ошибка: дата должна быть в формате " + DateTimeUtil.DATE_PATTERN + ", например 15.03.2001.");
            }
        }
    }

    public LocalDate readOptionalDate(String prompt) {
        while (true) {
            String line = readOptionalLine(prompt + " (" + DateTimeUtil.DATE_PATTERN + "):");
            if (line == null) {
                return null;
            }
            try {
                return LocalDate.parse(line, DateTimeUtil.DATE);
            } catch (DateTimeParseException e) {
                printError("Ошибка: дата должна быть в формате " + DateTimeUtil.DATE_PATTERN + ", например 15.03.2001.");
            }
        }
    }

    public LocalDateTime readDateTime(String prompt) {
        LocalDateTime value = readOptionalDateTime(prompt);
        if (value == null) {
            throw new InputCancelledException();
        }
        return value;
    }

    public LocalDateTime readOptionalDateTime(String prompt) {
        while (true) {
            String line = readOptionalLine(prompt + " (" + DateTimeUtil.DATE_TIME_PATTERN + "):");
            if (line == null) {
                return null;
            }
            try {
                return LocalDateTime.parse(line, DateTimeUtil.DATE_TIME);
            } catch (DateTimeParseException e) {
                printError("Ошибка: дата и время должны быть в формате " + DateTimeUtil.DATE_TIME_PATTERN
                        + ", например 20.09.2026 18:30.");
            }
        }
    }

    /** Выбор значения перечисления из нумерованного списка. */
    public <E extends Enum<E>> E readEnum(String prompt, E[] values, Function<E, String> titles) {
        E value = readOptionalEnum(prompt, values, titles);
        if (value == null) {
            throw new InputCancelledException();
        }
        return value;
    }

    public <E extends Enum<E>> E readOptionalEnum(String prompt, E[] values, Function<E, String> titles) {
        println(prompt);
        for (int i = 0; i < values.length; i++) {
            println("  " + (i + 1) + ". " + titles.apply(values[i]));
        }
        while (true) {
            String line = readOptionalLine("Номер варианта:");
            if (line == null) {
                return null;
            }
            try {
                int index = Integer.parseInt(line);
                if (index >= 1 && index <= values.length) {
                    return values[index - 1];
                }
                printError("Ошибка: введите число от 1 до " + values.length + ".");
            } catch (NumberFormatException e) {
                printError("Ошибка: номер варианта должен быть целым числом.");
            }
        }
    }

    /** Подтверждение действия: д/y — да, всё остальное — нет. */
    public boolean confirm(String prompt) {
        String line = readOptionalLine(prompt + " (д/н):");
        if (line == null) {
            return false;
        }
        String answer = line.toLowerCase();
        return answer.equals("д") || answer.equals("да") || answer.equals("y") || answer.equals("yes");
    }

    public void pause() {
        readOptionalLine("Нажмите Enter, чтобы продолжить...");
    }

    /** Имя поля из подсказки вида «Введите ID:» → «ID». */
    private static String fieldName(String prompt) {
        String name = prompt.replaceFirst("(?iu)^введите\\s+", "").replace(":", "").trim();
        return name.isEmpty() ? "значение" : name;
    }
}
