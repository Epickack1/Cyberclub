package ru.mirea.cyberclub.backend.service;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import ru.mirea.cyberclub.backend.error.BadRequestException;

/**
 * Правила тегов брони: строчные буквы (латиница или кириллица), цифры, "_" и "-", от 2 до 30 символов.
 * Регистр и пробелы по краям не важны: "  Турнир " и "турнир" — один тег. То же ограничение — в БД (V5).
 */
public final class Tags {
    public static final int MAX_PER_BOOKING = 10;
    private static final Pattern TAG = Pattern.compile("[a-zа-яё0-9_-]{2,30}");

    private Tags() {
    }

    public static String normalize(String tag, String field) {
        String value = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT);
        if (!TAG.matcher(value).matches()) {
            throw new BadRequestException(field, "Тег \"" + (tag == null ? "" : tag.trim())
                    + "\": от 2 до 30 символов — буквы, цифры, \"_\" и \"-\", без пробелов.");
        }
        return value;
    }

    public static Set<String> normalizeAll(Collection<String> tags, String field) {
        Set<String> result = new TreeSet<>();
        if (tags != null) {
            tags.forEach(tag -> result.add(normalize(tag, field)));
        }
        if (result.size() > MAX_PER_BOOKING) {
            throw new BadRequestException(field, "У брони не больше " + MAX_PER_BOOKING + " тегов.");
        }
        return result;
    }
}
