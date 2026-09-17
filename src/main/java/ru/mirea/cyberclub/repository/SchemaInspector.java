package ru.mirea.cyberclub.repository;

import java.util.List;

/**
 * Просмотр структуры и содержимого таблиц базы данных
 * (пункт меню «Вывести таблицы базы данных»).
 */
public interface SchemaInspector {

    /** Описание колонки таблицы. */
    record ColumnInfo(String name, String type, boolean nullable) {
    }

    /** Описание таблицы: имя, колонки, количество строк. */
    record TableInfo(String name, List<ColumnInfo> columns, long rowCount) {
    }

    /** Содержимое таблицы в виде строк-значений. */
    record TableData(String name, List<String> columnNames, List<List<String>> rows) {
    }

    List<TableInfo> listTables();

    TableData readTable(String tableName, int limit);
}
