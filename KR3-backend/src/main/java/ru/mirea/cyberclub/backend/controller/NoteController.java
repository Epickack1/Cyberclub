package ru.mirea.cyberclub.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.dto.NoteDtos.NoteRequest;
import ru.mirea.cyberclub.backend.dto.NoteDtos.NoteResponse;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.service.NoteService;

/**
 * Заметки к брони. Читать — любой сотрудник, создавать — OPERATOR и ADMIN (правила SecurityConfig),
 * изменять и удалять — автор заметки или ADMIN (проверка в NoteService).
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/notes")
@Tag(name = "Заметки к броням")
public class NoteController {
    private final NoteService notes;

    public NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    @Operation(summary = "История заметок: сначала закреплённые, затем новые")
    public List<NoteResponse> list(@PathVariable UUID bookingId, @AuthenticationPrincipal CurrentUser user) {
        return notes.list(bookingId, user);
    }

    @PostMapping
    @Operation(summary = "Добавить заметку (автор и время — от сервера)")
    public ResponseEntity<NoteResponse> create(@PathVariable UUID bookingId, @Valid @RequestBody NoteRequest request,
                                               @AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(notes.create(bookingId, request, user));
    }

    @PutMapping("/{noteId}")
    @Operation(summary = "Изменить текст или закрепление (нужен version)")
    public NoteResponse update(@PathVariable UUID bookingId, @PathVariable UUID noteId,
                               @Valid @RequestBody NoteRequest request, @AuthenticationPrincipal CurrentUser user) {
        return notes.update(bookingId, noteId, request, user);
    }

    @DeleteMapping("/{noteId}")
    @Operation(summary = "Удалить заметку")
    public ResponseEntity<Void> delete(@PathVariable UUID bookingId, @PathVariable UUID noteId,
                                       @AuthenticationPrincipal CurrentUser user) {
        notes.delete(bookingId, noteId, user);
        return ResponseEntity.noContent().build();
    }
}
