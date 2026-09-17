package ru.mirea.cyberclub.ui;

/**
 * Меню «Тема оформления»: переключение цветовой темы консоли с сохранением выбора.
 */
public class ThemeMenu extends AbstractMenu {

    private final ThemePreferences preferences;

    public ThemeMenu(ConsoleIO io, ThemePreferences preferences) {
        super(io, "Тема оформления");
        this.preferences = preferences;
        for (ConsoleTheme theme : ConsoleTheme.values()) {
            add(theme.getTitle(), () -> select(theme));
        }
    }

    private void select(ConsoleTheme theme) {
        if (!io.isColorSupported() && theme.isColored()) {
            io.println("Текущий терминал не поддерживает ANSI-цвета: тема сохранена, но будет применена при запуске в цветном терминале.");
            preferences.save(theme);
            return;
        }
        io.applyTheme(theme);
        preferences.save(theme);
        io.printBanner("КИБЕРСПОРТИВНЫЙ КЛУБ \"CYBERCLUB\"");
        io.printSuccess("Тема \"" + theme.getTitle() + "\" применена.");
    }
}
