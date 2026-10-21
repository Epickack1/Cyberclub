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

    public StationService(StationRepository stations, DtoMapper mapper) {
        this.stations = stations;
        this.mapper = mapper;
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
        Station station = new Station(request.name().trim(), request.type(), request.hourlyRate(), blankToNull(request.specs()));
        return mapper.toResponse(stations.save(station));
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
        return mapper.toResponse(stations.saveAndFlush(station));
    }

    /** Включение и отключение места без удаления. */
    @Transactional
    public StationResponse setActive(Long id, boolean active) {
        Station station = require(id);
        station.setActive(active);
        return mapper.toResponse(stations.saveAndFlush(station));
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
