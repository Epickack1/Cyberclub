package ru.mirea.cyberclub.backend.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Заметка сотрудника к брони: история общения с клиентом, договорённости, жалобы.
 * Автор хранится ссылкой и именем — после удаления учётной записи заметка остаётся подписанной.
 */
@Entity
@Table(name = "booking_notes")
public class BookingNote extends AuditedVersionedEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    protected BookingNote() {
    }

    public BookingNote(Booking booking, UUID authorId, String authorName, String body, boolean pinned) {
        this.id = UUID.randomUUID();
        this.booking = booking;
        this.authorId = authorId;
        this.authorName = authorName;
        this.body = body;
        this.pinned = pinned;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public UUID getAuthorId() { return authorId; }
    public String getAuthorName() { return authorName; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
}
