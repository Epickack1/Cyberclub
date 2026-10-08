package ru.mirea.cyberclub.model;

import java.math.BigDecimal;

/**
 * Игровое место клуба: ПК, консоль или комната-буткемп.
 */
public class Station extends BaseEntity {

    private String name;
    private StationType type;
    private BigDecimal hourlyRate;
    private String specs;
    private boolean active = true;

    public Station() {
    }

    public Station(String name, StationType type, BigDecimal hourlyRate, String specs, boolean active) {
        this.name = name;
        this.type = type;
        this.hourlyRate = hourlyRate;
        this.specs = specs;
        this.active = active;
    }

    public Station(Long id, String name, StationType type, BigDecimal hourlyRate, String specs, boolean active) {
        super(id);
        this.name = name;
        this.type = type;
        this.hourlyRate = hourlyRate;
        this.specs = specs;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StationType getType() {
        return type;
    }

    public void setType(StationType type) {
        this.type = type;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public String getSpecs() {
        return specs;
    }

    public void setSpecs(String specs) {
        this.specs = specs;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String describe() {
        return "Игровое место #" + getId() + " " + name + " (" + type.getTitle() + ")";
    }

    @Override
    public String toString() {
        return "Station{id=" + getId() + ", name=" + name + ", type=" + type
                + ", hourlyRate=" + hourlyRate + ", active=" + active + "}";
    }
}
