package ru.mirea.cyberclub.exception;

/**
 * Пользователь отменил ввод (пустая строка вместо обязательного значения).
 * Используется консольным интерфейсом для выхода из текущей операции в меню.
 */
public class InputCancelledException extends AppException {

    public InputCancelledException() {
        super("Операция отменена.");
    }
}
