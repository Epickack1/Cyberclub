package ru.mirea.cyberclub.ui;

import java.util.ArrayList;
import java.util.List;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.DataAccessException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.exception.InputCancelledException;
import ru.mirea.cyberclub.exception.ValidationException;

/**
 * Базовый класс консольных меню: печатает пункты, читает выбор, выполняет
 * действие и возвращается в меню. Любая ошибка выводится сообщением —
 * программа не завершается. Конкретные меню регистрируют свои пункты
 * через {@link #add(String, Runnable)} (полиморфизм: общий цикл, разное наполнение).
 */
public abstract class AbstractMenu {

    /** Пункт меню: подпись и действие. */
    protected record MenuItem(String label, Runnable action) {
    }

    protected final ConsoleIO io;
    private final String title;
    private final List<MenuItem> items = new ArrayList<>();

    protected AbstractMenu(ConsoleIO io, String title) {
        this.io = io;
        this.title = title;
    }

    protected void add(String label, Runnable action) {
        items.add(new MenuItem(label, action));
    }

    /** Подпись пункта «0» — «Назад» для подменю, «Выход» для главного. */
    protected String exitLabel() {
        return "Назад";
    }

    /** Печать шапки меню; главное меню переопределяет её на баннер. */
    protected void printTitle() {
        io.printHeader("--- " + title + " ---");
    }

    public void run() {
        while (true) {
            printTitle();
            for (int i = 0; i < items.size(); i++) {
                io.println((i + 1) + ". " + items.get(i).label());
            }
            io.println("0. " + exitLabel());
            int choice;
            try {
                choice = io.readMenuChoice(items.size());
            } catch (InputCancelledException e) {
                // поток ввода закрыт (Ctrl+Z) — выходим из меню, как по пункту 0
                return;
            }
            if (choice == 0) {
                return;
            }
            execute(items.get(choice - 1));
        }
    }

    private void execute(MenuItem item) {
        try {
            io.println();
            item.action().run();
        } catch (InputCancelledException e) {
            io.println(e.getMessage());
        } catch (EntityNotFoundException | ValidationException | BusinessRuleException e) {
            io.printError("Ошибка: " + e.getMessage());
        } catch (DataAccessException e) {
            io.printError("Ошибка базы данных: " + e.getMessage());
        } catch (AppException e) {
            io.printError("Ошибка: " + e.getMessage());
        } catch (RuntimeException e) {
            io.printError("Непредвиденная ошибка: " + e);
        }
    }
}
