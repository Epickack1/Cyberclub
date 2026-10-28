package ru.mirea.cyberclub.auth;

import java.util.List;

/**
 * Роли пользователей — те же, что на сервере КР3. Права проверяет сервер; приложение по роли
 * только решает, что показать (например, вкладку «Пользователи» видит лишь ADMIN).
 * CLIENT — посетитель клуба: входит на сайт, в это приложение для сотрудников он не входит.
 */
public enum Role {
    VIEWER("Просмотр"),
    OPERATOR("Администратор зала"),
    ADMIN("Администратор системы"),
    CLIENT("Клиент клуба");

    /** Роли сотрудников — их выбирают при регистрации на вкладке «Пользователи». */
    public static final List<Role> STAFF = List.of(VIEWER, OPERATOR, ADMIN);

    private final String title;

    Role(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return title;
    }
}
