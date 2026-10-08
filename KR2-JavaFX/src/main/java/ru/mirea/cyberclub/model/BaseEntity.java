package ru.mirea.cyberclub.model;

import java.util.Objects;

/**
 * Базовый класс всех сущностей предметной области.
 * Инкапсулирует идентификатор записи (первичный ключ в таблице БД).
 */
public abstract class BaseEntity {

    private Long id;

    protected BaseEntity() {
    }

    protected BaseEntity(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /** Новая запись, которая ещё не сохранена в базе данных. */
    public boolean isNew() {
        return id == null;
    }

    /**
     * Краткое описание сущности для сообщений пользователю.
     * Каждый наследник реализует по-своему (полиморфизм).
     */
    public abstract String describe();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
