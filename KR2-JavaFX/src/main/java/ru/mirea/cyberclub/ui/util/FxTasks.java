package ru.mirea.cyberclub.ui.util;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.concurrent.Task;

/**
 * Выполняет обращение к базе данных в фоновом потоке, чтобы окно не «зависало»,
 * а результат или ошибку передаёт обратно в JavaFX Application Thread
 * (только из него можно менять элементы интерфейса).
 */
public final class FxTasks {

    private FxTasks() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "cyberclub-db");
        thread.setDaemon(true);
        thread.start();
    }
}
