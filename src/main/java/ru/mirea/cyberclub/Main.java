package ru.mirea.cyberclub;

import java.io.Console;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;

import org.fusesource.jansi.AnsiConsole;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.DataAccessException;
import ru.mirea.cyberclub.ui.ConsoleApp;
import ru.mirea.cyberclub.ui.ConsoleIO;
import ru.mirea.cyberclub.ui.ConsoleTheme;
import ru.mirea.cyberclub.ui.ThemePreferences;
import ru.mirea.cyberclub.util.AppConfig;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * Точка входа. Настраивает консоль (кодировка, ANSI-цвета), проверяет
 * подключение к базе данных и запускает приложение.
 *
 * <p>Аргументы: {@code --no-color} — отключить цветовые темы
 * (то же делает переменная окружения NO_COLOR).</p>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        // Apache POI использует Log4j API; без реализации логгера он печатает предупреждение —
        // подключаем встроенный простой логгер и отключаем графическую подсистему (autoSizeColumn).
        System.setProperty("log4j2.loggerContextFactory", "org.apache.logging.log4j.simple.SimpleLoggerContextFactory");
        System.setProperty("java.awt.headless", "true");

        boolean noColor = Arrays.asList(args).contains("--no-color") || System.getenv("NO_COLOR") != null;
        Console console = System.console();
        boolean ansiInstalled = installAnsi();
        boolean colorSupported = !noColor && console != null && ansiInstalled;

        PrintStream out = ansiInstalled ? AnsiConsole.out() : System.out;
        Scanner in = new Scanner(System.in, inputCharset(console));
        ConsoleIO io = new ConsoleIO(in, out, colorSupported);

        ThemePreferences themePreferences = new ThemePreferences();
        io.applyTheme(themePreferences.load(ConsoleTheme.DARK));

        int exitCode = 0;
        try {
            AppConfig config = AppConfig.load();
            DatabaseManager db = new DatabaseManager(config);
            db.checkConnection();
            new ConsoleApp(io, db, themePreferences, Path.of("export")).run();
        } catch (DataAccessException e) {
            io.printError("Ошибка подключения к базе данных: " + e.getMessage());
            io.println("Проверьте, что служба PostgreSQL запущена и база создана скриптом db\\init.ps1,");
            io.println("либо задайте параметры подключения переменными CYBERCLUB_DB_URL, CYBERCLUB_DB_USER, CYBERCLUB_DB_PASSWORD.");
            exitCode = 1;
        } catch (AppException e) {
            io.printError("Ошибка: " + e.getMessage());
            exitCode = 1;
        } finally {
            io.resetTheme();
            if (ansiInstalled) {
                AnsiConsole.systemUninstall();
            }
        }
        System.exit(exitCode);
    }

    /** Включает обработку ANSI-последовательностей в консоли Windows (библиотека Jansi). */
    private static boolean installAnsi() {
        try {
            AnsiConsole.systemInstall();
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * Кодировка ввода: явное свойство stdin.encoding, иначе кодировка консоли,
     * иначе (ввод перенаправлен из файла/канала) UTF-8.
     */
    private static Charset inputCharset(Console console) {
        String explicit = System.getProperty("stdin.encoding");
        if (explicit != null && Charset.isSupported(explicit)) {
            return Charset.forName(explicit);
        }
        if (console != null) {
            return console.charset();
        }
        return StandardCharsets.UTF_8;
    }
}
