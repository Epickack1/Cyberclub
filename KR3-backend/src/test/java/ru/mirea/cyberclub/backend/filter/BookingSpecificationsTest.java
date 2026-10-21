package ru.mirea.cyberclub.backend.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Безопасность поиска: служебные символы LIKE из строки пользователя ищутся как обычный текст. */
class BookingSpecificationsTest {

    @Test
    void escapesLikeWildcards() {
        assertThat(BookingSpecifications.escapeLike("100%")).isEqualTo("100\\%");
        assertThat(BookingSpecifications.escapeLike("Nova_Q")).isEqualTo("Nova\\_Q");
        assertThat(BookingSpecifications.escapeLike("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void sqlInjectionTextStaysPlainText() {
        String evil = "'); DROP TABLE bookings; --";
        // Строка не меняется (в ней нет % и _) и уйдёт в запрос только как значение параметра.
        assertThat(BookingSpecifications.escapeLike(evil)).isEqualTo(evil);
    }
}
