package ru.mirea.cyberclub.backend.web;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import ru.mirea.cyberclub.backend.error.BadRequestException;

/**
 * Создаёт Pageable из параметров запроса. Сортировать можно только по полям из белого списка,
 * размер страницы ограничен — клиент не может запросить всю базу одним запросом.
 */
@Component
public class PageableFactory {
    public static final int MAX_PAGE_SIZE = 100;

    /** Имя в API -> поле сущности. */
    private static final Map<String, String> BOOKING_FIELDS = Map.of(
            "number", "number",
            "start", "startTime",
            "price", "totalPrice",
            "status", "status",
            "client", "client.nickname",
            "station", "station.name",
            "created", "createdAt",
            "updated", "updatedAt");

    public Pageable bookings(int page, int size, String sort) {
        if (page < 0) {
            throw new BadRequestException("page", "Номер страницы не может быть отрицательным.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size", "Размер страницы должен быть от 1 до " + MAX_PAGE_SIZE + ".");
        }
        String[] parts = sort == null || sort.isBlank() ? new String[]{"start", "desc"} : sort.split(",", 2);
        String field = BOOKING_FIELDS.get(parts[0].trim());
        if (field == null) {
            throw new BadRequestException("sort", "Сортировка возможна по полям: " + String.join(", ", BOOKING_FIELDS.keySet()) + ".");
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Вторичная сортировка по номеру делает порядок стабильным между страницами.
        return PageRequest.of(page, size, Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "number")));
    }
}
