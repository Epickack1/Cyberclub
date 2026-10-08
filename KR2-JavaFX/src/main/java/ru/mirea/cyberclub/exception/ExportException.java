package ru.mirea.cyberclub.exception;

/**
 * Ошибка формирования файла экспорта (нет прав на запись, диск заполнен и т.п.).
 */
public class ExportException extends AppException {

    public ExportException(String message, Throwable cause) {
        super(message, cause);
    }
}
