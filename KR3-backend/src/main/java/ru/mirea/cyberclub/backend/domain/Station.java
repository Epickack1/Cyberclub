package ru.mirea.cyberclub.backend.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Игровое место — справочная сущность, на которую ссылаются бронирования.
 * Место не удаляется, а отключается (переводится на обслуживание).
 */
@Entity
@Table(name = "stations")
public class Station extends AuditedVersionedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StationType type;

    @Column(name = "hourly_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(length = 255)
    private String specs;

    @Column(nullable = false)
    private boolean active = true;

    protected Station() {
    }

    public Station(String name, StationType type, BigDecimal hourlyRate, String specs) {
        this.name = name;
        this.type = type;
        this.hourlyRate = hourlyRate;
        this.specs = specs;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StationType getType() { return type; }
    public void setType(StationType type) { this.type = type; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public void setHourlyRate(BigDecimal hourlyRate) { this.hourlyRate = hourlyRate; }
    public String getSpecs() { return specs; }
    public void setSpecs(String specs) { this.specs = specs; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
