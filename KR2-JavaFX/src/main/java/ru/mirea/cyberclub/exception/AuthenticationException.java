package ru.mirea.cyberclub.exception;

/**
 * Вход не выполнен или сессия закончилась: неверный email/пароль, учётная запись отключена
 * или заблокирована после серии неудачных попыток, токен истёк.
 */
public class AuthenticationException extends AppException {

    public AuthenticationException(String message) {
        super(message);
    }
}
