package ru.mirea.cyberclub.repository;

import java.util.List;
import java.util.Optional;

import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;

/**
 * Хранилище игровых мест.
 */
public interface StationRepository extends CrudRepository<Station, Long> {

    Optional<Station> findByName(String name);

    List<Station> findByType(StationType type);

    List<Station> findActive();
}
