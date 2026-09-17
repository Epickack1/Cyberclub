package ru.mirea.cyberclub.ui;

import java.util.List;

import ru.mirea.cyberclub.repository.SchemaInspector;
import ru.mirea.cyberclub.repository.SchemaInspector.ColumnInfo;
import ru.mirea.cyberclub.repository.SchemaInspector.TableInfo;

/**
 * Меню «Вывести таблицы базы данных»: структура таблиц (через JDBC DatabaseMetaData)
 * и их содержимое.
 */
public class DbTablesMenu extends AbstractMenu {

    private static final int ROW_LIMIT = 200;

    private final SchemaInspector inspector;
    private final TablePrinter tables;

    public DbTablesMenu(ConsoleIO io, SchemaInspector inspector) {
        super(io, "Таблицы базы данных");
        this.inspector = inspector;
        this.tables = new TablePrinter(io);
        add("Структура таблиц (колонки, типы, число строк)", this::structure);
        add("Содержимое таблицы", this::content);
        add("Содержимое всех таблиц", this::allContent);
    }

    private void structure() {
        for (TableInfo table : inspector.listTables()) {
            io.printHeader("Таблица " + table.name() + " (" + table.rowCount() + " строк)");
            for (ColumnInfo column : table.columns()) {
                io.println("  " + pad(column.name(), 14) + pad(column.type(), 16) + (column.nullable() ? "NULL" : "NOT NULL"));
            }
        }
    }

    private void content() {
        List<TableInfo> list = inspector.listTables();
        io.println("Таблицы: " + String.join(", ", list.stream().map(TableInfo::name).toList()));
        String name = io.readLine("Имя таблицы:");
        printTable(name);
    }

    private void allContent() {
        for (TableInfo table : inspector.listTables()) {
            printTable(table.name());
        }
    }

    private void printTable(String name) {
        io.printHeader("Таблица " + name.toLowerCase());
        tables.printTableData(inspector.readTable(name, ROW_LIMIT));
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text + " " : text + " ".repeat(width - text.length());
    }
}
