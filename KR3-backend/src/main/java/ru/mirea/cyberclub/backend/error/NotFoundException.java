package ru.mirea.cyberclub.backend.error;

/** Запрошенной записи нет — HTTP 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
