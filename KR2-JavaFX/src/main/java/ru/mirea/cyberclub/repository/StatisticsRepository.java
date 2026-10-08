package ru.mirea.cyberclub.repository;

import ru.mirea.cyberclub.model.Statistics;

/**
 * Сбор сводных показателей агрегирующими SQL-запросами.
 */
public interface StatisticsRepository {

    Statistics collect();
}
