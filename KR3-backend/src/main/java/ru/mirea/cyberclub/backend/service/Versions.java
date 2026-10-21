package ru.mirea.cyberclub.backend.service;

import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.ConflictException;

/** Проверка optimistic locking: клиент присылает версию, которую видел, при изменении записи. */
final class Versions {
    private Versions() {
    }

    static void check(Long requested, long actual) {
        if (requested == null) {
            throw new BadRequestException("version", "Для изменения записи укажите version.");
        }
        if (requested != actual) {
            throw new ConflictException("Запись уже изменена другим пользователем. Обновите данные и повторите.");
        }
    }
}
