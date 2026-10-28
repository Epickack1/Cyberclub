package ru.mirea.cyberclub.backend.error;

/** Вход временно заблокирован после серии неудачных попыток — HTTP 423. */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String message) { super(message); }
}
