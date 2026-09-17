package ru.mirea.cyberclub.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.BookingRepository;
import ru.mirea.cyberclub.repository.StationRepository;

/**
 * Бизнес-логика работы с игровыми местами.
 */
public class StationService {

    private static final Pattern NAME = Pattern.compile("^[A-Z0-9-]{2,20}$");
    private static final BigDecimal MAX_RATE = new BigDecimal("100000");

    private final StationRepository stationRepository;
    private final BookingRepository bookingRepository;

    public StationService(StationRepository stationRepository, BookingRepository bookingRepository) {
        this.stationRepository = stationRepository;
        this.bookingRepository = bookingRepository;
    }

    public Station create(Station station) {
        normalize(station);
        validate(station);
        checkUniqueName(station);
        return stationRepository.save(station);
    }

    public List<Station> getAll() {
        return stationRepository.findAll();
    }

    public List<Station> getActive() {
        return stationRepository.findActive();
    }

    public List<Station> getByType(StationType type) {
        return stationRepository.findByType(type);
    }

    public Station getById(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Игровое место", id));
    }

    public Station update(Station station) {
        getById(station.getId());
        normalize(station);
        validate(station);
        checkUniqueName(station);
        if (!stationRepository.update(station)) {
            throw new EntityNotFoundException("Игровое место", station.getId());
        }
        return getById(station.getId());
    }

    /** Включает место или переводит его на обслуживание. */
    public Station setActive(Long id, boolean active) {
        Station station = getById(id);
        station.setActive(active);
        stationRepository.update(station);
        return station;
    }

    /** Правило: место с историей бронирований удалять нельзя — только переводить на обслуживание. */
    public void delete(Long id) {
        Station station = getById(id);
        if (bookingRepository.existsByStationId(id)) {
            throw new BusinessRuleException("У места " + station.getName()
                    + " есть бронирования - удалить нельзя. Переведите его на обслуживание.");
        }
        if (!stationRepository.deleteById(id)) {
            throw new EntityNotFoundException("Игровое место", id);
        }
    }

    public long count() {
        return stationRepository.count();
    }

    private void normalize(Station station) {
        if (station.getName() != null) {
            station.setName(station.getName().trim().toUpperCase());
        }
        if (station.getSpecs() != null && station.getSpecs().isBlank()) {
            station.setSpecs(null);
        }
    }

    private void validate(Station station) {
        if (station.getName() == null || station.getName().isBlank()) {
            throw new ValidationException("Название игрового места обязательно.");
        }
        if (!NAME.matcher(station.getName()).matches()) {
            throw new ValidationException("Название: 2-20 символов, латинские буквы, цифры и дефис (например PC-01).");
        }
        if (station.getType() == null) {
            throw new ValidationException("Тип игрового места обязателен.");
        }
        BigDecimal rate = station.getHourlyRate();
        if (rate == null || rate.signum() <= 0) {
            throw new ValidationException("Тариф должен быть больше нуля.");
        }
        if (rate.compareTo(MAX_RATE) > 0) {
            throw new ValidationException("Тариф не может превышать " + MAX_RATE + " руб./час.");
        }
        if (station.getSpecs() != null && station.getSpecs().length() > 255) {
            throw new ValidationException("Характеристики не могут быть длиннее 255 символов.");
        }
    }

    private void checkUniqueName(Station station) {
        Optional<Station> existing = stationRepository.findByName(station.getName());
        if (existing.isPresent() && !existing.get().getId().equals(station.getId())) {
            throw new ValidationException("Игровое место с названием " + station.getName() + " уже существует.");
        }
    }
}
