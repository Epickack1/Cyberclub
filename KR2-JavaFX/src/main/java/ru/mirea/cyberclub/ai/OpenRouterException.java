package ru.mirea.cyberclub.ai;

import ru.mirea.cyberclub.exception.AppException;

/**
 * Ошибка обращения к OpenRouter, в которой сохранён HTTP-статус.
 *
 * <p>Статус нужен не для показа, а для решения: можно ли попробовать другую бесплатную
 * модель. Часть моделей каталога отклоняется по коду 403 («доступно только в агентных
 * приложениях»), часть упирается в лимит (429) — на них есть смысл переключиться, а вот
 * неверный ключ (401) или отсутствие интернета переключением не лечатся.</p>
 */
public class OpenRouterException extends AppException {

    private final int statusCode;
    private final boolean retryableWithOtherModel;

    public OpenRouterException(String message, int statusCode) {
        this(message, statusCode, isModelLevelStatus(statusCode));
    }

    /**
     * @param statusCode HTTP-статус или 0, если ответ не был получен/разобран
     * @param retryableWithOtherModel true — ошибка относится к конкретной модели
     *                                и другая модель может сработать
     */
    public OpenRouterException(String message, int statusCode, boolean retryableWithOtherModel) {
        super(message);
        this.statusCode = statusCode;
        this.retryableWithOtherModel = retryableWithOtherModel;
    }

    public OpenRouterException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.retryableWithOtherModel = false;
    }

    public int statusCode() {
        return statusCode;
    }

    /** Можно ли спастись переключением на другую бесплатную модель. */
    public boolean isRetryableWithOtherModel() {
        return retryableWithOtherModel;
    }

    private static boolean isModelLevelStatus(int code) {
        return code == 403 || code == 404 || code == 429 || code >= 500;
    }
}
