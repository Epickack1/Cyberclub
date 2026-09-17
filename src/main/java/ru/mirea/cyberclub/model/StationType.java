package ru.mirea.cyberclub.model;

/**
 * Тип (зона) игрового места клуба.
 */
public enum StationType {
    STANDARD("Стандарт"),
    VIP("VIP"),
    BOOTCAMP("Буткемп"),
    CONSOLE("Консоль");

    private final String title;

    StationType(String title) {
        this.title = title;
    }

    /** Название для отображения в консоли и отчётах. */
    public String getTitle() {
        return title;
    }

    /** Зоны, доступные только совершеннолетним клиентам (бизнес-правило). */
    public boolean isAdultsOnly() {
        return this == VIP || this == BOOTCAMP;
    }

    @Override
    public String toString() {
        return title;
    }
}
