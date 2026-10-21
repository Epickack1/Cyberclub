package ru.mirea.cyberclub.backend.error;

/** Конфликт с текущим состоянием данных: устаревшая версия, занятое место, повтор уникального поля — HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}
