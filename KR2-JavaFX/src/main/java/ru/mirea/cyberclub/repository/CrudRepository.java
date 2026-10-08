package ru.mirea.cyberclub.repository;

import java.util.List;
import java.util.Optional;

/**
 * Общий контракт хранилища для любой сущности (интерфейс + обобщённые типы).
 * Реализации: JDBC (основная, пакет repository.jdbc) и in-memory (в тестах).
 *
 * @param <T>  тип сущности
 * @param <ID> тип идентификатора
 */
public interface CrudRepository<T, ID> {

    /** Сохраняет новую запись и возвращает её с присвоенным идентификатором. */
    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    /** Обновляет существующую запись; возвращает true, если запись найдена и изменена. */
    boolean update(T entity);

    /** Удаляет запись; возвращает true, если запись существовала. */
    boolean deleteById(ID id);

    long count();
}
