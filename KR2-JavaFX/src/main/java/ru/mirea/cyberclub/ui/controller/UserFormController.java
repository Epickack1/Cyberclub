package ru.mirea.cyberclub.ui.controller;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.auth.Role;
import ru.mirea.cyberclub.auth.UserInfo;
import ru.mirea.cyberclub.service.UserAdminService;

/**
 * Форма регистрации пользователя (user-form.fxml). Открывается администратором
 * с вкладки «Пользователи» (сотрудник) или с вкладки «Клиенты» (учётная запись клиента
 * для сайта, см. {@link #forClient}); сохраняет через {@link UserAdminService} на сервере КР3.
 */
public class UserFormController extends FormController<UserInfo> {

    @FXML
    private TextField emailField;
    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<Role> roleCombo;
    @FXML
    private Label roleHint;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField passwordRepeatField;

    private final UserAdminService users;
    private boolean clientAccount;

    public UserFormController(AppContext context) {
        this.users = context.users();
    }

    @FXML
    private void initialize() {
        roleCombo.setItems(FXCollections.observableArrayList(Role.STAFF));
        roleCombo.setValue(Role.OPERATOR);
        roleCombo.valueProperty().addListener((obs, old, role) -> roleHint.setText(describe(role)));
        roleHint.setText(describe(Role.OPERATOR));
        registerField("email", emailField);
        registerField("displayName", nameField);
        registerField("roles", roleCombo);
        registerField("password", passwordField);
        registerField("passwordRepeat", passwordRepeatField);
    }

    private static String describe(Role role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case VIEWER -> "Только просмотр клиентов, мест, броней и статистики.";
            case OPERATOR -> "Ведёт клиентов и бронирования.";
            case ADMIN -> "Полный доступ, включая регистрацию пользователей.";
            case CLIENT -> "Вход на сайт клуба: своя карточка и свои бронирования. В это приложение не входит.";
        };
    }

    /** Учётная запись для клиента: email и имя из его карточки, роль — только «Клиент клуба». */
    public void forClient(String email, String fullName) {
        clientAccount = true;
        emailField.setText(email);
        emailField.setEditable(false);
        nameField.setText(fullName);
        roleCombo.setItems(FXCollections.observableArrayList(Role.CLIENT));
        roleCombo.setValue(Role.CLIENT);
        roleCombo.setDisable(true);
    }

    @Override
    public String title() {
        return clientAccount ? "Учётная запись клиента" : "Новый пользователь";
    }

    @Override
    protected UserInfo submit() {
        if (clientAccount) {
            return users.registerClient(emailField.getText(), nameField.getText(), passwordField.getText(),
                    passwordRepeatField.getText());
        }
        return users.register(emailField.getText(), nameField.getText(), passwordField.getText(),
                passwordRepeatField.getText(), roleCombo.getValue());
    }
}
