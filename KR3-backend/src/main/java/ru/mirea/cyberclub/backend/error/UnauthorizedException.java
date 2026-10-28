package ru.mirea.cyberclub.backend.error;

/** Неверный email/пароль, недействительный или отозванный токен — HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
