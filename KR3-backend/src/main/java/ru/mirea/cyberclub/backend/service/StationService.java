package ru.mirea.cyberclub.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.Station;
import ru.mirea.cyberclub.backend.dto.StationDtos.StationRequest;
import ru.mirea.cyberclub.backend.dto.StationDtos.StationResponse;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.repository.StationRepository;

/**
 * Игровые места — справочник. Физически не удаляются: место отключается
 * (переводится на обслуживание), а история бронирований сохраняется.
 */
@Service
public class StationService {
    private static final long NO_ID = -1L;

    private final StationRepository stations;
    private final DtoMapper mapper;
    private final AuditService audit;

    public StationService(StationRepository stations, DtoMapper mapper, AuditService audit) {
        this.stations = stations;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<StationResponse> list(boolean activeOnly) {
        List<Station> found = activeOnly ? stations.findByActiveTrueOrderByNameAsc() : stations.findAllByOrderByNameAsc();
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StationResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public StationResponse create(StationRequest request) {
        checkNameFree(request.name(), NO_ID);
        Station station = stations.save(new Station(request.name().trim(), request.type(), request.hourlyRate(),
                blankToNull(request.specs())));
        audit.record("STATION_CREATED", AuditService.STATION, station.getId(), "Добавлено место " + station.getName()
                + " (" + station.getType().getTitle() + ", " + station.getHourlyRate().toPlainString() + " руб./ч)");
        return mapper.toResponse(station);
    }

    @Transactional
    public StationResponse update(Long id, StationRequest request) {
        Station station = require(id);
        Versions.check(request.version(), station.getVersion());
        checkNameFree(request.name(), id);
        station.setName(request.name().trim());
        station.setType(request.type());
        station.setHourlyRate(request.hourlyRate());
        station.setSpecs(blankToNull(request.specs()));
        Station saved = stations.saveAndFlush(station);
        audit.record("STATION_UPDATED", AuditService.STATION, id, "Изменено место " + saved.getName()
                + " (" + saved.getType().getTitle() + ", " + saved.getHourlyRate().toPlainString() + " руб./ч)");
        return mapper.toResponse(saved);
    }

    /** Включение и отключение места без удаления. */
    @Transactional
    public StationResponse setActive(Long id, boolean active) {
        Station station = require(id);
        station.setActive(active);
        Station saved = stations.saveAndFlush(station);
        audit.record(active ? "STATION_ENABLED" : "STATION_DISABLED", AuditService.STATION, id,
                "Место " + saved.getName() + (active ? " снова доступно" : " отключено (обслуживание)"));
        return mapper.toResponse(saved);
    }

    Station require(Long id) {
        return stations.findById(id).orElseThrow(() -> new NotFoundException("Игровое место с id " + id + " не найдено."));
    }

    private void checkNameFree(String name, long selfId) {
        if (stations.existsByNameIgnoreCaseAndIdNot(name.trim(), selfId)) {
            throw new ConflictException("Игровое место с названием " + name.trim() + " уже существует.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
