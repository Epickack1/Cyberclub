package ru.mirea.cyberclub.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ru.mirea.cyberclub.auth.AuthApiClient;
import ru.mirea.cyberclub.auth.Role;
import ru.mirea.cyberclub.auth.UserInfo;
import ru.mirea.cyberclub.exception.ValidationException;

/**
 * Пользователи системы (только для администратора): список, регистрация, отключение,
 * сброс пароля, снятие блокировки. Данные хранит сервер КР3; поля проверяются здесь,
 * чтобы сразу подсветить ошибку, и ещё раз на сервере.
 */
public class UserAdminService {

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final AuthApiClient client;
    private final AuthService auth;

    public UserAdminService(AuthApiClient client, AuthService auth) {
        this.client = client;
        this.auth = auth;
    }

    public List<UserInfo> getAll() {
        return auth.withToken(client::users);
    }

    public UserInfo register(String email, String displayName, String password, String passwordRepeat, Role role) {
        String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (mail.isEmpty()) {
            throw new ValidationException("email", "Введите email.");
        }
        if (!AuthService.EMAIL.matcher(mail).matches() || mail.length() > 100) {
            throw new ValidationException("email", "Некорректный email.");
        }
        String name = displayName == null ? "" : displayName.trim();
        if (name.length() < 2 || name.length() > 100) {
            throw new ValidationException("displayName", "Имя: от 2 до 100 символов.");
        }
        validatePassword(password, mail, "password");
        if (!password.equals(passwordRepeat)) {
            throw new ValidationException("passwordRepeat", "Пароли не совпадают.");
        }
        if (role == null) {
            throw new ValidationException("roles", "Выберите роль.");
        }
        return auth.withToken(token -> client.createUser(token, mail, name, password, Set.of(role)));
    }

    /**
     * Учётная запись для клиента клуба: email и имя берутся из карточки клиента,
     * по email сервер привязывает учётную запись к этой карточке.
     */
    public UserInfo registerClient(String email, String fullName, String password, String passwordRepeat) {
        return register(email, fullName, password, passwordRepeat, Role.CLIENT);
    }

    public UserInfo setEnabled(UserInfo user, boolean enabled) {
        return auth.withToken(token -> client.setEnabled(token, user.id(), enabled));
    }

    public UserInfo unlock(UserInfo user) {
        return auth.withToken(token -> client.unlock(token, user.id()));
    }

    /**
     * Сбрасывает пароль на новый временный и возвращает его — администратор передаёт его сотруднику.
     * Сервер при этом закрывает все сессии пользователя.
     */
    public String resetPassword(UserInfo user) {
        String password = temporaryPassword();
        auth.withToken(token -> client.resetPassword(token, user.id(), password));
        return password;
    }

    /** Те же требования, что на сервере: длина, буквы и цифры, без пробелов и без имени из email. */
    static void validatePassword(String password, String email, String field) {
        if (password == null || password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new ValidationException(field, "Пароль: от " + PASSWORD_MIN + " до " + PASSWORD_MAX + " символов.");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw new ValidationException(field, "Пароль должен содержать буквы и цифры.");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new ValidationException(field, "Пароль не должен содержать пробелов.");
        }
        String login = email.split("@", 2)[0];
        if (login.length() >= 4 && password.toLowerCase(Locale.ROOT).contains(login)) {
            throw new ValidationException(field, "Пароль не должен содержать имя из email.");
        }
    }

    /** 10 символов: буквы и цифры (без похожих 0/O и 1/l). */
    static String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        for (int i = 0; i < 3; i++) {
            sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        return sb.toString();
    }
}
