package ru.mirea.cyberclub.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.mirea.cyberclub.backend.domain.Station;

public interface StationRepository extends JpaRepository<Station, Long> {

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Station> findAllByOrderByNameAsc();

    Optional<Station> findByNameIgnoreCase(String name);

    List<Station> findByActiveTrueOrderByNameAsc();
}
