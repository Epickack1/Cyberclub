package ru.mirea.cyberclub.ui.util;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mirea.cyberclub.CyberClubApp;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.DataAccessException;

/**
 * Стандартные диалоговые окна приложения: ошибка, сообщение, подтверждение, выбор.
 * Пользователь видит понятный текст; технические подробности (stack trace) пишутся только в журнал.
 */
public final class Dialogs {

    private static final Logger log = LoggerFactory.getLogger(Dialogs.class);

    public static final ButtonType YES = new ButtonType("Да", ButtonBar.ButtonData.YES);
    public static final ButtonType NO = new ButtonType("Нет", ButtonBar.ButtonData.NO);
    public static final ButtonType OK = new ButtonType("OK", ButtonBar.ButtonData.OK_DONE);
    public static final ButtonType CANCEL = new ButtonType("Отмена", ButtonBar.ButtonData.CANCEL_CLOSE);

    private Dialogs() {
    }

    /** Сообщение об ошибке операции. */
    public static void error(Window owner, Throwable error) {
        error(owner, "Операция не выполнена", friendly(error));
    }

    public static void error(Window owner, String header, String message) {
        Alert alert = alert(Alert.AlertType.ERROR, owner, "Ошибка", header, message, OK);
        alert.showAndWait();
    }

    /** Ошибка, после которой приложение не может продолжить работу (окна ещё нет). */
    public static void fatal(String header, String message) {
        error(null, header, message);
    }

    public static void info(Window owner, String header, String message) {
        alert(Alert.AlertType.INFORMATION, owner, "CyberClub", header, message, OK).showAndWait();
    }

    /** Подтверждение действия: true, если пользователь нажал «Да». */
    public static boolean confirm(Window owner, String header, String message) {
        Alert alert = alert(Alert.AlertType.CONFIRMATION, owner, "Подтверждение", header, message, YES, NO);
        // По умолчанию фокус на «Нет»: случайное нажатие Enter ничего не удалит
        ((Button) alert.getDialogPane().lookupButton(YES)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(NO)).setDefaultButton(true);
        return alert.showAndWait().orElse(NO) == YES;
    }

    /** Выбор одного значения из списка (например, нового статуса брони). */
    public static <T> Optional<T> choose(Window owner, String header, String label, List<T> options) {
        ChoiceDialog<T> dialog = new ChoiceDialog<>(options.get(0), options);
        prepare(dialog, owner, "Выбор", header);
        dialog.setContentText(label);
        dialog.getDialogPane().getButtonTypes().setAll(OK, CANCEL);
        dialog.setResultConverter(button -> button == OK ? dialog.getSelectedItem() : null);
        return dialog.showAndWait();
    }

    /**
     * Текст ошибки для пользователя. Исключения приложения ({@link AppException}) уже содержат
     * понятное сообщение; прочие ошибки — непредвиденные, их подробности записываются в журнал.
     */
    public static String friendly(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof DataAccessException) {
                log.warn("Ошибка базы данных", t);
                return "Ошибка при работе с базой данных.\n" + t.getMessage();
            }
            if (t instanceof AppException) {
                return t.getMessage();
            }
        }
        log.error("Непредвиденная ошибка", error);
        return "Непредвиденная ошибка (" + error.getClass().getSimpleName()
                + "). Подробности записаны в журнал приложения.";
    }

    /** Подключает стили приложения к диалогу и задаёт владельца (диалог модален к окну). */
    public static void prepare(Dialog<?> dialog, Window owner, String title, String header) {
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        URL css = CyberClubApp.class.getResource(CyberClubApp.STYLESHEET);
        if (css != null) {
            dialog.getDialogPane().getStylesheets().add(css.toExternalForm());
        }
    }

    private static Alert alert(Alert.AlertType type, Window owner, String title, String header,
                               String message, ButtonType... buttons) {
        Alert alert = new Alert(type, message, buttons);
        prepare(alert, owner, title, header);
        alert.getDialogPane().setMinWidth(460);
        return alert;
    }
}
