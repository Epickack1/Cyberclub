package ru.mirea.cyberclub.exception;

/**
 * Базовое исключение приложения. Непроверяемое (unchecked), чтобы не засорять
 * сигнатуры методов: перехватывается в слое консольного интерфейса.
 */
public class AppException extends RuntimeException {

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
