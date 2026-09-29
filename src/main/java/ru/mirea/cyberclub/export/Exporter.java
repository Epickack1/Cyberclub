package ru.mirea.cyberclub.export;

import java.nio.file.Path;

/**
 * Контракт формата экспорта. Меню знает только его и не зависит
 * от конкретного формата (полиморфизм: работает ExcelExporter или CsvExporter).
 */
public interface Exporter {

    /** Название формата — пункт в меню экспорта. */
    String getTitle();

    /**
     * Записывает данные в каталог {@code dir} и возвращает путь
     * к получившемуся файлу (или папке с файлами).
     */
    Path export(ExportData data, Path dir);
}
