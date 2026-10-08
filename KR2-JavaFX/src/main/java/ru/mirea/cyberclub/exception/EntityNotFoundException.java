package ru.mirea.cyberclub.exception;

/**
 * Запись с указанным идентификатором не найдена в базе данных.
 */
public class EntityNotFoundException extends AppException {

    public EntityNotFoundException(String entityName, Object id) {
        super(entityName + " с ID " + id + " не существует.");
    }

    public EntityNotFoundException(String message) {
        super(message);
    }
}
