package ru.mirea.cyberclub.ui;

/**
 * Цветовые темы консоли (фирменная палитра клуба, та же будет в JavaFX-версии):
 * <ul>
 *   <li>LIGHT — оранжевый фон, фиолетовый текст;</li>
 *   <li>DARK — чёрный фон, светло-голубой текст;</li>
 *   <li>NONE — без управляющих последовательностей (для терминалов без поддержки ANSI).</li>
 * </ul>
 * Цвета задаются 24-битными ANSI-кодами (ESC[48;2;r;g;bm — фон, ESC[38;2;r;g;bm — текст).
 */
public enum ConsoleTheme {

    NONE("Без цвета", "", "", "", "", ""),
    LIGHT("Светлая (оранжевый фон, фиолетовый текст)",
            rgb(48, 255, 140, 0),     // фон: оранжевый
            rgb(38, 75, 0, 130),      // текст: фиолетовый (indigo)
            rgb(38, 40, 0, 80),       // акцент (заголовки): тёмно-фиолетовый
            rgb(38, 120, 0, 0),       // ошибки: тёмно-красный
            rgb(38, 0, 80, 0)),       // успех: тёмно-зелёный
    DARK("Тёмная (чёрный фон, светло-голубой текст)",
            rgb(48, 0, 0, 0),         // фон: чёрный
            rgb(38, 135, 206, 250),   // текст: светло-голубой
            rgb(38, 255, 255, 255),   // акцент: белый
            rgb(38, 255, 99, 71),     // ошибки: томатный
            rgb(38, 144, 238, 144));  // успех: светло-зелёный

    public static final String ESC = "[";
    public static final String RESET = ESC + "0m";
    public static final String BOLD = ESC + "1m";
    public static final String CLEAR_SCREEN = ESC + "2J" + ESC + "H";
    public static final String CLEAR_LINE = ESC + "K";

    private final String title;
    private final String background;
    private final String foreground;
    private final String accent;
    private final String error;
    private final String success;

    ConsoleTheme(String title, String background, String foreground, String accent, String error, String success) {
        this.title = title;
        this.background = background;
        this.foreground = foreground;
        this.accent = accent;
        this.error = error;
        this.success = success;
    }

    private static String rgb(int layer, int r, int g, int b) {
        return ESC + layer + ";2;" + r + ";" + g + ";" + b + "m";
    }

    public String getTitle() {
        return title;
    }

    public boolean isColored() {
        return this != NONE;
    }

    /** Базовые атрибуты темы: фон + цвет обычного текста. */
    public String base() {
        return background + foreground;
    }

    /** Атрибуты для заголовков. */
    public String accent() {
        return isColored() ? background + accent + BOLD : "";
    }

    public String error() {
        return isColored() ? background + error + BOLD : "";
    }

    public String success() {
        return isColored() ? background + success : "";
    }

    @Override
    public String toString() {
        return title;
    }
}
