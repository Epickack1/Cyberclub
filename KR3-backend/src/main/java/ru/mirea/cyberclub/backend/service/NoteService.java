package ru.mirea.cyberclub.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.Booking;
import ru.mirea.cyberclub.backend.domain.BookingNote;
import ru.mirea.cyberclub.backend.domain.Role;
import ru.mirea.cyberclub.backend.dto.NoteDtos.NoteRequest;
import ru.mirea.cyberclub.backend.dto.NoteDtos.NoteResponse;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.repository.BookingNoteRepository;
import ru.mirea.cyberclub.backend.security.CurrentUser;

/**
 * Заметки к брони. Читать могут все сотрудники, писать — администратор зала и администратор системы.
 * Изменить или удалить заметку может её автор; администратор системы — любую.
 */
@Service
public class NoteService {
    private static final int PREVIEW = 60;

    private final BookingNoteRepository notes;
    private final BookingService bookings;
    private final AuditService audit;

    public NoteService(BookingNoteRepository notes, BookingService bookings, AuditService audit) {
        this.notes = notes;
        this.bookings = bookings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID bookingId, CurrentUser user) {
        bookings.require(bookingId);
        return notes.findByBookingIdOrderByPinnedDescCreatedAtDesc(bookingId).stream()
                .map(note -> toResponse(note, user))
                .toList();
    }

    @Transactional
    public NoteResponse create(UUID bookingId, NoteRequest request, CurrentUser user) {
        Booking booking = bookings.require(bookingId);
        BookingNote note = notes.saveAndFlush(new BookingNote(booking, user.id(), user.displayName(),
                request.body().trim(), request.pinned()));
        audit.record("NOTE_CREATED", AuditService.NOTE, note.getId(),
                "Заметка к брони #" + booking.getNumber() + ": " + preview(note.getBody()));
        return toResponse(note, user);
    }

    @Transactional
    public NoteResponse update(UUID bookingId, UUID noteId, NoteRequest request, CurrentUser user) {
        BookingNote note = require(bookingId, noteId);
        checkCanModify(note, user);
        Versions.check(request.version(), note.getVersion());
        note.setBody(request.body().trim());
        note.setPinned(request.pinned());
        BookingNote saved = notes.saveAndFlush(note);
        audit.record("NOTE_UPDATED", AuditService.NOTE, noteId,
                "Изменена заметка к брони #" + note.getBooking().getNumber() + ": " + preview(saved.getBody()));
        return toResponse(saved, user);
    }

    @Transactional
    public void delete(UUID bookingId, UUID noteId, CurrentUser user) {
        BookingNote note = require(bookingId, noteId);
        checkCanModify(note, user);
        notes.delete(note);
        audit.record("NOTE_DELETED", AuditService.NOTE, noteId,
                "Удалена заметка к брони #" + note.getBooking().getNumber() + " (автор " + note.getAuthorName() + ")");
    }

    private BookingNote require(UUID bookingId, UUID noteId) {
        return notes.findByIdAndBookingId(noteId, bookingId)
                .orElseThrow(() -> new NotFoundException("Заметка не найдена."));
    }

    private static boolean canModify(BookingNote note, CurrentUser user) {
        return user.roles().contains(Role.ADMIN) || user.id().equals(note.getAuthorId());
    }

    private static void checkCanModify(BookingNote note, CurrentUser user) {
        if (!canModify(note, user)) {
            throw new AccessDeniedException("Изменить или удалить заметку может только её автор или администратор системы.");
        }
    }

    private static NoteResponse toResponse(BookingNote note, CurrentUser user) {
        return new NoteResponse(note.getId(), note.getBooking().getId(), note.getAuthorId(), note.getAuthorName(),
                note.getBody(), note.isPinned(), canModify(note, user), note.getCreatedAt(), note.getUpdatedAt(),
                note.getVersion());
    }

    private static String preview(String body) {
        String line = body.replaceAll("\\s+", " ");
        return line.length() <= PREVIEW ? line : line.substring(0, PREVIEW) + "…";
    }
}
