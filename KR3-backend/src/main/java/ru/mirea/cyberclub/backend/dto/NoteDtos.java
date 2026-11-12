package ru.mirea.cyberclub.backend.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTO заметок к брони. */
public final class NoteDtos {
    private NoteDtos() {
    }

    /** Создание и изменение заметки; {@code version} обязателен при изменении. */
    public record NoteRequest(
            @Schema(example = "Клиент просил место у окна, перезвонить за час до начала")
            @NotBlank(message = "Текст заметки обязателен")
            @Size(max = 2000, message = "Заметка не длиннее 2000 символов") String body,
            boolean pinned,
            Long version) {
    }

    public record NoteResponse(
            UUID id,
            UUID bookingId,
            UUID authorId,
            String authorName,
            String body,
            boolean pinned,
            boolean editable,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
