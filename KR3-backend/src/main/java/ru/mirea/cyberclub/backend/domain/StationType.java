package ru.mirea.cyberclub.backend.domain;

/** Тип (зона) игрового места. */
public enum StationType {
    STANDARD("Стандарт"),
    VIP("VIP"),
    BOOTCAMP("Буткемп"),
    CONSOLE("Консоль");

    private final String title;

    StationType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    /** VIP и буткемп — только для клиентов 18+. */
    public boolean isAdultsOnly() {
        return this == VIP || this == BOOTCAMP;
    }
}
