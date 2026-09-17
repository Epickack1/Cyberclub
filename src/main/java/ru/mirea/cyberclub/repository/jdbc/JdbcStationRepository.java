package ru.mirea.cyberclub.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.StationRepository;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * JDBC-реализация хранилища игровых мест (таблица stations).
 */
public class JdbcStationRepository extends AbstractJdbcRepository implements StationRepository {

    private static final String SELECT = "SELECT id, name, type, hourly_rate, specs, active FROM stations";

    public JdbcStationRepository(DatabaseManager db) {
        super(db);
    }

    @Override
    public Station save(Station station) {
        String sql = "INSERT INTO stations (name, type, hourly_rate, specs, active) VALUES (?, ?, ?, ?, ?)";
        long id = executeInsert(sql, ps -> {
            ps.setString(1, station.getName());
            ps.setString(2, station.getType().name());
            ps.setBigDecimal(3, station.getHourlyRate());
            ps.setString(4, station.getSpecs());
            ps.setBoolean(5, station.isActive());
        });
        station.setId(id);
        return station;
    }

    @Override
    public Optional<Station> findById(Long id) {
        return queryOne(SELECT + " WHERE id = ?", ps -> ps.setLong(1, id), JdbcStationRepository::mapRow);
    }

    @Override
    public List<Station> findAll() {
        return queryList(SELECT + " ORDER BY id", NO_PARAMS, JdbcStationRepository::mapRow);
    }

    @Override
    public boolean update(Station station) {
        String sql = "UPDATE stations SET name = ?, type = ?, hourly_rate = ?, specs = ?, active = ? WHERE id = ?";
        return executeUpdate(sql, ps -> {
            ps.setString(1, station.getName());
            ps.setString(2, station.getType().name());
            ps.setBigDecimal(3, station.getHourlyRate());
            ps.setString(4, station.getSpecs());
            ps.setBoolean(5, station.isActive());
            ps.setLong(6, station.getId());
        }) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return executeUpdate("DELETE FROM stations WHERE id = ?", ps -> ps.setLong(1, id)) > 0;
    }

    @Override
    public long count() {
        return queryLong("SELECT COUNT(*) FROM stations", NO_PARAMS);
    }

    @Override
    public Optional<Station> findByName(String name) {
        return queryOne(SELECT + " WHERE UPPER(name) = UPPER(?)", ps -> ps.setString(1, name),
                JdbcStationRepository::mapRow);
    }

    @Override
    public List<Station> findByType(StationType type) {
        return queryList(SELECT + " WHERE type = ? ORDER BY id", ps -> ps.setString(1, type.name()),
                JdbcStationRepository::mapRow);
    }

    @Override
    public List<Station> findActive() {
        return queryList(SELECT + " WHERE active = TRUE ORDER BY id", NO_PARAMS, JdbcStationRepository::mapRow);
    }

    static Station mapRow(ResultSet rs) throws SQLException {
        return new Station(
                rs.getLong("id"),
                rs.getString("name"),
                StationType.valueOf(rs.getString("type")),
                rs.getBigDecimal("hourly_rate"),
                rs.getString("specs"),
                rs.getBoolean("active"));
    }
}
