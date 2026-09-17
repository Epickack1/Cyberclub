package ru.mirea.cyberclub.repository.jdbc;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.repository.ClientRepository;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * JDBC-реализация хранилища клиентов (таблица clients).
 */
public class JdbcClientRepository extends AbstractJdbcRepository implements ClientRepository {

    private static final String SELECT = "SELECT id, nickname, full_name, phone, email, birth_date, created_at FROM clients";

    public JdbcClientRepository(DatabaseManager db) {
        super(db);
    }

    @Override
    public Client save(Client client) {
        String sql = "INSERT INTO clients (nickname, full_name, phone, email, birth_date) VALUES (?, ?, ?, ?, ?)";
        long id = executeInsert(sql, ps -> {
            ps.setString(1, client.getNickname());
            ps.setString(2, client.getFullName());
            ps.setString(3, client.getPhone());
            ps.setString(4, client.getEmail());
            ps.setDate(5, Date.valueOf(client.getBirthDate()));
        });
        client.setId(id);
        return findById(id).orElse(client);
    }

    @Override
    public Optional<Client> findById(Long id) {
        return queryOne(SELECT + " WHERE id = ?", ps -> ps.setLong(1, id), JdbcClientRepository::mapRow);
    }

    @Override
    public List<Client> findAll() {
        return queryList(SELECT + " ORDER BY id", NO_PARAMS, JdbcClientRepository::mapRow);
    }

    @Override
    public boolean update(Client client) {
        String sql = "UPDATE clients SET nickname = ?, full_name = ?, phone = ?, email = ?, birth_date = ? WHERE id = ?";
        return executeUpdate(sql, ps -> {
            ps.setString(1, client.getNickname());
            ps.setString(2, client.getFullName());
            ps.setString(3, client.getPhone());
            ps.setString(4, client.getEmail());
            ps.setDate(5, Date.valueOf(client.getBirthDate()));
            ps.setLong(6, client.getId());
        }) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return executeUpdate("DELETE FROM clients WHERE id = ?", ps -> ps.setLong(1, id)) > 0;
    }

    @Override
    public long count() {
        return queryLong("SELECT COUNT(*) FROM clients", NO_PARAMS);
    }

    @Override
    public Optional<Client> findByNickname(String nickname) {
        return queryOne(SELECT + " WHERE LOWER(nickname) = LOWER(?)", ps -> ps.setString(1, nickname),
                JdbcClientRepository::mapRow);
    }

    @Override
    public Optional<Client> findByPhone(String phone) {
        return queryOne(SELECT + " WHERE phone = ?", ps -> ps.setString(1, phone), JdbcClientRepository::mapRow);
    }

    @Override
    public Optional<Client> findByEmail(String email) {
        return queryOne(SELECT + " WHERE LOWER(email) = LOWER(?)", ps -> ps.setString(1, email),
                JdbcClientRepository::mapRow);
    }

    @Override
    public List<Client> searchByNicknameOrName(String query) {
        String pattern = likePattern(query);
        return queryList(SELECT + " WHERE nickname ILIKE ? OR full_name ILIKE ? ORDER BY nickname", ps -> {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
        }, JdbcClientRepository::mapRow);
    }

    /** Преобразование строки ResultSet в объект Client. */
    static Client mapRow(ResultSet rs) throws SQLException {
        return new Client(
                rs.getLong("id"),
                rs.getString("nickname"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getObject("birth_date", LocalDate.class),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
