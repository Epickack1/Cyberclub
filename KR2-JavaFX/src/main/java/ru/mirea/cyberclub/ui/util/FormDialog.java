package ru.mirea.cyberclub.ui.util;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.ui.controller.FormController;

/**
 * Открывает форму из FXML (корневой элемент — DialogPane) как модальное диалоговое окно.
 * Кнопка «Сохранить» не закрывает окно, пока форма не сохранится без ошибок.
 */
public final class FormDialog {

    private FormDialog() {
    }

    public static <R> Optional<R> show(Window owner, AppContext.View<? extends FormController<R>> view) {
        return show(owner, view, "Сохранить", "Отмена");
    }

    /** То же с другими подписями кнопок (например «Войти» / «Выход» в окне входа). */
    public static <R> Optional<R> show(Window owner, AppContext.View<? extends FormController<R>> view,
                                       String okText, String cancelText) {
        FormController<R> form = view.controller();
        Dialog<R> dialog = new Dialog<>();
        dialog.setDialogPane((DialogPane) view.root());
        Dialogs.prepare(dialog, owner, form.title(), form.title());

        ButtonType save = new ButtonType(okText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(cancelText, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(save, cancel);

        AtomicReference<R> result = new AtomicReference<>();
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(save);
        saveButton.getStyleClass().add("primary-button");
        // Фильтр события срабатывает до закрытия окна: при ошибке событие «поглощается» (consume)
        saveButton.addEventFilter(ActionEvent.ACTION, event ->
                form.trySubmit().ifPresentOrElse(result::set, event::consume));

        dialog.setResultConverter(button -> button == save ? result.get() : null);
        return dialog.showAndWait();
    }
}
