package ru.mirea.cyberclub.repository.rest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.StationRepository;

/**
 * REST-реализация хранилища игровых мест: запросы к /api/stations.
 */
public class RestStationRepository implements StationRepository {

    private static final TypeReference<List<Station>> LIST = new TypeReference<>() { };

    private final RestClient rest;

    public RestStationRepository(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Station save(Station station) {
        return rest.post("/stations", station, Station.class);
    }

    @Override
    public Optional<Station> findById(Long id) {
        return rest.find("/stations/" + id, Station.class);
    }

    @Override
    public List<Station> findAll() {
        return rest.get("/stations", Map.of(), LIST);
    }

    @Override
    public boolean update(Station station) {
        rest.put("/stations/" + station.getId(), station, Station.class);
        return true;
    }

    @Override
    public boolean deleteById(Long id) {
        rest.delete("/stations/" + id);
        return true;
    }

    @Override
    public long count() {
        return rest.get("/stations/count", Map.of(), new TypeReference<Long>() { });
    }

    @Override
    public Optional<Station> findByName(String name) {
        return rest.get("/stations", Map.of("name", name), LIST).stream().findFirst();
    }

    @Override
    public List<Station> findByType(StationType type) {
        return rest.get("/stations", Map.of("type", type.name()), LIST);
    }

    @Override
    public List<Station> findActive() {
        return rest.get("/stations", Map.of("active", true), LIST);
    }
}
