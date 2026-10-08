package ru.mirea.cyberclub.export;

import java.nio.file.Path;

/**
 * Формат выгрузки данных. Сервис экспорта знает только этот интерфейс
 * и выбирает реализацию по расширению файла (полиморфизм: CSV или Excel).
 */
public interface Exporter {

    /** Расширение файла без точки, например "csv". */
    String extension();

    /** Описание для фильтра FileChooser, например "CSV (разделитель ;)". */
    String description();

    /** Записывает данные в указанный файл (файл перезаписывается). */
    void export(ExportData data, Path file);
}
