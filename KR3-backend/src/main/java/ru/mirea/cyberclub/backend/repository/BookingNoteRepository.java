package ru.mirea.cyberclub.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.mirea.cyberclub.backend.domain.BookingNote;

public interface BookingNoteRepository extends JpaRepository<BookingNote, UUID> {

    /** История заметок брони: сначала закреплённые, внутри — от новых к старым. */
    List<BookingNote> findByBookingIdOrderByPinnedDescCreatedAtDesc(UUID bookingId);

    Optional<BookingNote> findByIdAndBookingId(UUID id, UUID bookingId);
}
