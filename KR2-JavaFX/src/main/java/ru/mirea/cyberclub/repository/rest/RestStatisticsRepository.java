package ru.mirea.cyberclub.repository.rest;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

import ru.mirea.cyberclub.model.Statistics;
import ru.mirea.cyberclub.repository.StatisticsRepository;

/**
 * Сводные показатели с сервера: GET /api/statistics.
 */
public class RestStatisticsRepository implements StatisticsRepository {

    private final RestClient rest;

    public RestStatisticsRepository(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Statistics collect() {
        return rest.get("/statistics", Map.of(), new TypeReference<Statistics>() { });
    }
}
