package ru.mirea.cyberclub.backend.security;

import java.util.Locale;

import org.springframework.stereotype.Component;

import ru.mirea.cyberclub.backend.error.BadRequestException;

/** Требования к паролю сотрудника. Сам пароль в журнал и в сообщения об ошибках не попадает. */
@Component
public class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    /** Проверяет пароль; field — имя поля формы для подсветки ошибки. */
    public void validate(String password, String email, String field) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BadRequestException(field, "Пароль: от " + MIN_LENGTH + " до " + MAX_LENGTH + " символов.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BadRequestException(field, "Пароль должен содержать буквы и цифры.");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new BadRequestException(field, "Пароль не должен содержать пробелов.");
        }
        String login = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
        if (login.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(login)) {
            throw new BadRequestException(field, "Пароль не должен содержать имя учётной записи.");
        }
    }
}
