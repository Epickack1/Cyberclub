package ru.mirea.cyberclub.backend.filter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import ru.mirea.cyberclub.backend.domain.Booking;
import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Station;
import ru.mirea.cyberclub.backend.error.BadRequestException;

/**
 * Собирает условие поиска через Criteria API.
 *
 * <p>Пользовательская строка никогда не склеивается с текстом запроса: она попадает
 * в запрос только как значение параметра, поэтому SQL-инъекция невозможна.</p>
 */
public final class BookingSpecifications {
    private BookingSpecifications() {
    }

    public static Specification<Booking> from(BookingFilter f) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            Join<Booking, Client> client = root.join("client", JoinType.INNER);
            Join<Booking, Station> station = root.join("station", JoinType.INNER);

            if (f.q() != null && !f.q().isBlank()) {
                where.add(textSearch(f.q().trim(), root.get("number"), root.get("comment"), client, station, cb));
            }
            if (f.stationId() != null) {
                where.add(cb.equal(station.get("id"), f.stationId()));
            }
            if (f.stationType() != null) {
                where.add(cb.equal(station.get("type"), f.stationType()));
            }
            if (f.clientId() != null) {
                where.add(cb.equal(client.get("id"), f.clientId()));
            }
            if (f.status() != null) {
                where.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.paid() != null) {
                where.add(cb.equal(root.get("paid"), f.paid()));
            }
            BookingFilter.ArchiveScope scope = f.archive() == null ? BookingFilter.ArchiveScope.ACTIVE : f.archive();
            if (scope != BookingFilter.ArchiveScope.ALL) {
                where.add(cb.equal(root.get("archived"), scope == BookingFilter.ArchiveScope.ARCHIVED));
            }
            if (f.from() != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("startTime"), f.from().atStartOfDay()));
            }
            if (f.to() != null) {
                where.add(cb.lessThan(root.get("startTime"), f.to().plusDays(1).atStartOfDay()));
            }
            if (f.priceOp() != null || f.price() != null) {
                where.add(compare(root.get("totalPrice"), f.priceOp(), f.price(), cb));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    /** "#12" или "12" ищет по номеру брони, остальное — подстрока в нике, ФИО, месте и комментарии. */
    private static Predicate textSearch(String q, Expression<Long> number, Expression<String> comment,
                                        Join<Booking, Client> client, Join<Booking, Station> station,
                                        CriteriaBuilder cb) {
        String digits = q.startsWith("#") ? q.substring(1) : q;
        if (digits.matches("\\d{1,18}")) {
            return cb.equal(number, Long.parseLong(digits));
        }
        String pattern = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
        return cb.or(
                cb.like(cb.lower(client.get("nickname")), pattern, '\\'),
                cb.like(cb.lower(client.get("fullName")), pattern, '\\'),
                cb.like(cb.lower(station.get("name")), pattern, '\\'),
                cb.like(cb.lower(comment), pattern, '\\'));
    }

    private static Predicate compare(Expression<BigDecimal> field, BookingFilter.NumberOp op, BigDecimal value,
                                     CriteriaBuilder cb) {
        if (op == null || value == null) {
            throw new BadRequestException("Для фильтра по стоимости нужны и операция (priceOp), и значение (price).");
        }
        return switch (op) {
            case EQ -> cb.equal(field, value);
            case GT -> cb.greaterThan(field, value);
            case GTE -> cb.greaterThanOrEqualTo(field, value);
            case LT -> cb.lessThan(field, value);
            case LTE -> cb.lessThanOrEqualTo(field, value);
        };
    }

    /** Экранирует служебные символы LIKE, чтобы "%" и "_" в запросе искались как обычные символы. */
    public static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
