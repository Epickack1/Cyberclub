package ru.mirea.cyberclub.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import ru.mirea.cyberclub.backend.domain.Booking;
import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Station;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientResponse;
import ru.mirea.cyberclub.backend.dto.StationDtos.StationResponse;

/** Преобразует сущности в DTO. Вызывается внутри транзакции, пока связанные объекты доступны. */
@Component
public class DtoMapper {
    private final Clock clock;

    public DtoMapper(Clock clock) {
        this.clock = clock;
    }

    public ClientResponse toResponse(Client c) {
        return new ClientResponse(c.getId(), c.getNickname(), c.getFullName(), c.getPhone(), c.getEmail(),
                c.getBirthDate(), c.getAge(LocalDate.now(clock)), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    public StationResponse toResponse(Station s) {
        return new StationResponse(s.getId(), s.getName(), s.getType(), s.getType().getTitle(), s.getHourlyRate(),
                s.getSpecs(), s.isActive(), s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }

    public BookingResponse toResponse(Booking b) {
        Client c = b.getClient();
        Station s = b.getStation();
        return new BookingResponse(b.getId(), b.getNumber(), c.getId(), c.getNickname(), c.getFullName(),
                s.getId(), s.getName(), s.getType(), b.getStartTime(), b.getEndTime(),
                Duration.between(b.getStartTime(), b.getEndTime()).toMinutes(),
                b.getStatus(), b.getStatus().getTitle(), b.getTotalPrice(), b.isPaid(), b.getComment(),
                b.isArchived(), b.getArchivedAt(), b.getCreatedAt(), b.getUpdatedAt(), b.getVersion());
    }
}
