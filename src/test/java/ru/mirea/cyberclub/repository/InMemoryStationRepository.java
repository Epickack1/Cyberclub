package ru.mirea.cyberclub.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;

/**
 * Хранилище игровых мест в памяти для модульных тестов.
 */
public class InMemoryStationRepository implements StationRepository {

    private final Map<Long, Station> storage = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Station save(Station station) {
        station.setId(nextId++);
        storage.put(station.getId(), station);
        return station;
    }

    @Override
    public Optional<Station> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Station> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean update(Station station) {
        return storage.replace(station.getId(), station) != null;
    }

    @Override
    public boolean deleteById(Long id) {
        return storage.remove(id) != null;
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public Optional<Station> findByName(String name) {
        return storage.values().stream().filter(s -> s.getName().equalsIgnoreCase(name)).findFirst();
    }

    @Override
    public List<Station> findByType(StationType type) {
        return storage.values().stream().filter(s -> s.getType() == type).toList();
    }

    @Override
    public List<Station> findActive() {
        return storage.values().stream().filter(Station::isActive).toList();
    }
}
