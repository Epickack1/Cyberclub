package ru.mirea.cyberclub.backend.dto;

import java.util.List;

/** DTO импорта броней из CSV: предварительная проверка и применение. */
public final class ImportDtos {
    private ImportDtos() {
    }

    /**
     * Режим импорта.
     * CREATE_ONLY — только новые брони; строки с номером существующей брони пропускаются.
     * UPSERT_BY_NUMBER — строка с номером существующей брони её обновляет, строка без номера создаёт новую.
     */
    public enum ImportMode { CREATE_ONLY, UPSERT_BY_NUMBER }

    /** Ошибка строки: номер строки файла (заголовок — строка 1), поле или null, текст. */
    public record RowIssue(long row, String field, String message) {
    }

    public record ImportPreview(ImportMode mode, int totalRows, int validRows, int invalidRows,
                                int toCreate, int toUpdate, List<RowIssue> issues) {
    }

    public record ImportResult(ImportMode mode, int totalRows, int created, int updated, int skipped,
                               List<RowIssue> issues) {
    }
}
