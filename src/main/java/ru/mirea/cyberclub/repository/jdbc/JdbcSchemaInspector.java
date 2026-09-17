package ru.mirea.cyberclub.repository.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.repository.SchemaInspector;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * Чтение структуры базы через {@link DatabaseMetaData} (стандартный API JDBC)
 * и содержимого таблиц. Имя таблицы для SELECT берётся только из списка,
 * полученного от самой БД, и заключается в кавычки — подстановка произвольного
 * текста пользователя в SQL невозможна.
 */
public class JdbcSchemaInspector extends AbstractJdbcRepository implements SchemaInspector {

    private static final String SCHEMA = "public";

    public JdbcSchemaInspector(DatabaseManager db) {
        super(db);
    }

    @Override
    public List<TableInfo> listTables() {
        try (Connection connection = db.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            List<TableInfo> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(null, SCHEMA, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    tables.add(new TableInfo(name, readColumns(meta, name), countRows(connection, name)));
                }
            }
            return tables;
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "чтение структуры базы данных");
        }
    }

    @Override
    public TableData readTable(String tableName, int limit) {
        boolean known = listTables().stream().anyMatch(t -> t.name().equalsIgnoreCase(tableName));
        if (!known) {
            throw new EntityNotFoundException("Таблица \"" + tableName + "\" не найдена в базе данных.");
        }
        String sql = "SELECT * FROM " + quote(tableName.toLowerCase()) + " ORDER BY 1 LIMIT ?";
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData rsMeta = rs.getMetaData();
                int columnCount = rsMeta.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(rsMeta.getColumnLabel(i));
                }
                List<List<String>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<String> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        String value = rs.getString(i);
                        row.add(value == null ? "NULL" : value);
                    }
                    rows.add(row);
                }
                return new TableData(tableName.toLowerCase(), columns, rows);
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "чтение таблицы " + tableName);
        }
    }

    private List<ColumnInfo> readColumns(DatabaseMetaData meta, String table) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, SCHEMA, table, "%")) {
            while (rs.next()) {
                String type = rs.getString("TYPE_NAME");
                int size = rs.getInt("COLUMN_SIZE");
                if (type.equals("varchar")) {
                    type = type + "(" + size + ")";
                } else if (type.equals("numeric")) {
                    type = type + "(" + size + "," + rs.getInt("DECIMAL_DIGITS") + ")";
                }
                columns.add(new ColumnInfo(rs.getString("COLUMN_NAME"), type,
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable));
            }
        }
        return columns;
    }

    private long countRows(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + quote(table));
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    /** Заключает идентификатор в двойные кавычки по правилам SQL. */
    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
