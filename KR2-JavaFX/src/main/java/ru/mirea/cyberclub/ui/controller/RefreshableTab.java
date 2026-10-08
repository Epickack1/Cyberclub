package ru.mirea.cyberclub.ui.controller;

/**
 * Вкладка главного окна, которая умеет перечитать свои данные из базы.
 * Главное окно вызывает {@link #refresh()} при переключении на вкладку и по F5.
 */
public interface RefreshableTab {

    void refresh();
}
