package ru.mirea.cyberclub.backend.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Единый JSON-формат ошибки API.
 *
 * <p>Клиент получает только понятное сообщение и, при ошибке ввода, список полей.
 * Stack trace, SQL и имена внутренних классов в ответ не попадают.</p>
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> validationErrors) {
}
