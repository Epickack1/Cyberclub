package ru.mirea.cyberclub;

import javafx.application.Application;

/**
 * Обычный main-класс для запуска из IDE или из jar.
 * Класс не наследуется от Application, поэтому JVM не требует JavaFX на module path:
 * библиотеки JavaFX подключаются из Maven-зависимостей как обычный classpath.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(CyberClubApp.class, args);
    }
}
