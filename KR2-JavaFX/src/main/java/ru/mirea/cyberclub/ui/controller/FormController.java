package ru.mirea.cyberclub.ui.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.ui.util.Dialogs;

/**
 * Общая часть контроллеров форм добавления/редактирования (клиент, место, бронь).
 * Наследник описывает только чтение полей и вызов сервиса в {@link #submit()},
 * а показ ошибок и подсветка полей реализованы здесь один раз — без дублирования.
 *
 * @param <R> что возвращает форма после успешного сохранения
 */
public abstract class FormController<R> {

    private static final String ERROR_CLASS = "field-error";

    /** Метка для текста ошибки внизу формы (fx:id="errorLabel" в FXML). */
    @FXML
    protected Label errorLabel;

    private final Map<String, Control> fields = new HashMap<>();

    /** Заголовок диалога (например «Новый клиент» / «Изменение клиента»). */
    public abstract String title();

    /**
     * Читает поля формы, проверяет их и сохраняет через сервис.
     *
     * @throws AppException если данные некорректны или нарушено бизнес-правило
     */
    protected abstract R submit();

    /**
     * Связывает ключ поля из {@link ValidationException#getField()} с элементом формы.
     * Подсветка ошибки снимается, как только пользователь начинает исправлять значение.
     */
    protected void registerField(String key, Control control) {
        fields.put(key, control);
        Runnable clear = () -> control.getStyleClass().remove(ERROR_CLASS);
        if (control instanceof TextInputControl input) {
            input.textProperty().addListener((obs, old, text) -> clear.run());
        }
        if (control instanceof ComboBoxBase<?> combo) {
            combo.valueProperty().addListener((obs, old, value) -> clear.run());
        }
        if (control instanceof DatePicker picker) {
            picker.getEditor().textProperty().addListener((obs, old, text) -> clear.run());
        }
    }

    /**
     * Вызывается кнопкой «Сохранить». При ошибке форма остаётся открытой,
     * под полями появляется сообщение, а неверное поле подсвечивается.
     */
    public final Optional<R> trySubmit() {
        clearErrors();
        try {
            return Optional.ofNullable(submit());
        } catch (ValidationException e) {
            Control field = e.getField() == null ? null : fields.get(e.getField());
            if (field != null) {
                field.getStyleClass().add(ERROR_CLASS);
                field.requestFocus();
            }
            showError(e.getMessage());
        } catch (AppException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            showError(Dialogs.friendly(e));
        }
        return Optional.empty();
    }

    protected void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
        resizeWindow();
    }

    /** Окно диалога подстраивает высоту под появившееся или скрытое сообщение. */
    private void resizeWindow() {
        if (errorLabel.getScene() != null && errorLabel.getScene().getWindow() != null) {
            errorLabel.getScene().getWindow().sizeToScene();
        }
    }

    private void clearErrors() {
        fields.values().forEach(node -> node.getStyleClass().remove(ERROR_CLASS));
        errorLabel.setText(null);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        resizeWindow();
    }
}
