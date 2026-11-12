package ru.mirea.cyberclub.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import ru.mirea.cyberclub.backend.dto.BookingDtos.BulkAction;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BulkFailure;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BulkRequest;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BulkResult;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;

/**
 * Массовые операции над бронями. Здесь намеренно нет общей транзакции: каждая бронь
 * обрабатывается отдельным вызовом транзакционного {@link BookingService}, поэтому ошибка
 * одной брони (активная бронь в архив, недопустимый переход статуса) не отменяет остальные.
 * В ответе — отдельно успешные и ошибочные записи.
 */
@Service
public class BulkBookingService {
    private final BookingService bookings;
    private final AuditService audit;

    public BulkBookingService(BookingService bookings, AuditService audit) {
        this.bookings = bookings;
        this.audit = audit;
    }

    public BulkResult apply(BulkRequest request) {
        validate(request);
        List<UUID> succeeded = new ArrayList<>();
        List<BulkFailure> failures = new ArrayList<>();
        for (UUID id : request.ids()) {
            try {
                switch (request.action()) {
                    case ARCHIVE -> bookings.archive(id);
                    case RESTORE -> bookings.restore(id);
                    case SET_STATUS -> bookings.changeStatus(id, request.status());
                    case ADD_TAG -> bookings.addTag(id, request.tag());
                    case REMOVE_TAG -> bookings.removeTag(id, request.tag());
                    case MARK_PAID -> bookings.confirmPayment(id, null);
                }
                succeeded.add(id);
            } catch (BusinessRuleException | BadRequestException | NotFoundException | ConflictException ex) {
                failures.add(new BulkFailure(id, ex.getMessage()));
            } catch (ObjectOptimisticLockingFailureException ex) {
                failures.add(new BulkFailure(id, "Запись изменена другим пользователем во время операции."));
            } catch (DataIntegrityViolationException ex) {
                failures.add(new BulkFailure(id, "Изменение нарушает ограничения данных (например, место уже занято)."));
            }
        }
        audit.record("BOOKING_BULK", AuditService.BOOKING, null, "Массовая операция " + describe(request)
                + ": успешно " + succeeded.size() + " из " + request.ids().size() + ", ошибок " + failures.size());
        return new BulkResult(request.action(), request.ids().size(), succeeded.size(), succeeded, failures);
    }

    private static void validate(BulkRequest request) {
        if (request.action() == BulkAction.SET_STATUS && request.status() == null) {
            throw new BadRequestException("status", "Для смены статуса укажите status.");
        }
        if ((request.action() == BulkAction.ADD_TAG || request.action() == BulkAction.REMOVE_TAG)) {
            Tags.normalize(request.tag(), "tag");
        }
    }

    private static String describe(BulkRequest request) {
        return switch (request.action()) {
            case ARCHIVE -> "\"в архив\"";
            case RESTORE -> "\"восстановить\"";
            case SET_STATUS -> "\"статус " + request.status().getTitle() + "\"";
            case ADD_TAG -> "\"добавить тег " + Tags.normalize(request.tag(), "tag") + "\"";
            case REMOVE_TAG -> "\"снять тег " + Tags.normalize(request.tag(), "tag") + "\"";
            case MARK_PAID -> "\"оплата подтверждена\"";
        };
    }
}
