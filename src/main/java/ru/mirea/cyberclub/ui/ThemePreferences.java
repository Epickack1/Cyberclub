package ru.mirea.cyberclub.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Запоминает выбранную тему между запусками в файле ~/.cyberclub.properties.
 * Ошибки чтения/записи не критичны: тогда действует тема по умолчанию.
 */
public class ThemePreferences {

    private static final String KEY = "theme";

    private final Path file;

    public ThemePreferences() {
        this(Path.of(System.getProperty("user.home"), ".cyberclub.properties"));
    }

    public ThemePreferences(Path file) {
        this.file = file;
    }

    public ConsoleTheme load(ConsoleTheme defaultTheme) {
        if (!Files.exists(file)) {
            return defaultTheme;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            return ConsoleTheme.valueOf(props.getProperty(KEY, defaultTheme.name()).trim().toUpperCase());
        } catch (IOException | IllegalArgumentException e) {
            return defaultTheme;
        }
    }

    public void save(ConsoleTheme theme) {
        Properties props = new Properties();
        props.setProperty(KEY, theme.name());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "CyberClub console settings");
        } catch (IOException ignored) {
            // настройка не сохранилась — не мешаем работе приложения
        }
    }
}
