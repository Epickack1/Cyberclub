package ru.mirea.cyberclub.backend.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.persistence.criteria.Predicate;
import ru.mirea.cyberclub.backend.domain.AuditEvent;
import ru.mirea.cyberclub.backend.dto.AuditDtos.AuditResponse;
import ru.mirea.cyberclub.backend.filter.BookingSpecifications;
import ru.mirea.cyberclub.backend.repository.AuditEventRepository;
import ru.mirea.cyberclub.backend.security.CurrentUser;

/**
 * Журнал аудита: кто, что и когда сделал. Запись идёт в той же транзакции, что и изменение,
 * поэтому откатившееся изменение не оставляет в журнале ложного следа. Неудачный вход
 * записывается тоже: транзакция входа на UnauthorizedException не откатывается.
 */
@Service
public class AuditService {
    public static final String BOOKING = "BOOKING";
    public static final String NOTE = "NOTE";
    public static final String STATION = "STATION";
    public static final String CLIENT = "CLIENT";
    public static final String USER = "USER";

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditService(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /** Событие от имени вошедшего пользователя (берётся из SecurityContext). */
    @Transactional
    public void record(String eventType, String entityType, Object entityId, String summary) {
        CurrentUser actor = currentUser();
        recordAs(actor == null ? null : actor.id(), actor == null ? null : actor.email(),
                eventType, entityType, entityId, summary);
    }

    /** Событие с явно указанным пользователем — при входе SecurityContext ещё пуст. */
    @Transactional
    public void recordAs(UUID actorId, String actorEmail, String eventType, String entityType, Object entityId,
                         String summary) {
        events.save(new AuditEvent(clock.instant(), actorId, cut(actorEmail, 100), eventType, entityType,
                entityId == null ? null : cut(entityId.toString(), 64), cut(summary, 500),
                cut(MDC.get("correlationId"), 64), cut(clientIp(), 64)));
    }

    /** Поиск по типу события, email, описанию и идентификатору; строка — только значение параметра. */
    @Transactional(readOnly = true)
    public Page<AuditResponse> search(String q, String eventType, Pageable pageable) {
        Specification<AuditEvent> spec = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (q != null && !q.isBlank()) {
                String pattern = "%" + BookingSpecifications.escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
                where.add(cb.or(
                        cb.like(cb.lower(root.get("eventType")), pattern, '\\'),
                        cb.like(cb.lower(root.get("actorEmail")), pattern, '\\'),
                        cb.like(cb.lower(root.get("summary")), pattern, '\\'),
                        cb.like(cb.lower(root.get("entityId")), pattern, '\\'),
                        cb.like(cb.lower(root.get("correlationId")), pattern, '\\')));
            }
            if (eventType != null && !eventType.isBlank()) {
                where.add(cb.equal(root.get("eventType"), eventType.trim().toUpperCase(Locale.ROOT)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        return events.findAll(spec, pageable).map(e -> new AuditResponse(e.getId(), e.getOccurredAt(), e.getActorId(),
                e.getActorEmail(), e.getEventType(), e.getEntityType(), e.getEntityId(), e.getSummary(),
                e.getCorrelationId(), e.getIpAddress()));
    }

    private static CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof CurrentUser user ? user : null;
    }

    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
