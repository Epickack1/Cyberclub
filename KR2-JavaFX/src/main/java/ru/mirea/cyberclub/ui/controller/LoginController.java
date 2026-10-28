package ru.mirea.cyberclub.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.auth.UserInfo;
import ru.mirea.cyberclub.exception.AuthenticationException;
import ru.mirea.cyberclub.service.AuthService;

/**
 * Окно входа (login-view.fxml). Кнопка «Войти» проверяет email и пароль на сервере КР3;
 * при ошибке окно не закрывается, а под полями показывается причина.
 */
public class LoginController extends FormController<UserInfo> {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label serverLabel;

    private final AuthService auth;

    public LoginController(AppContext context) {
        this.auth = context.auth();
    }

    @FXML
    private void initialize() {
        registerField("email", emailField);
        registerField("password", passwordField);
        serverLabel.setText("Сервер: " + auth.serverUrl());
        Platform.runLater(emailField::requestFocus);
    }

    @Override
    public String title() {
        return "Вход в CyberClub";
    }

    @Override
    protected UserInfo submit() {
        try {
            return auth.login(emailField.getText(), passwordField.getText());
        } catch (AuthenticationException e) {
            passwordField.clear();     // неверный пароль набирается заново
            throw e;
        }
    }
}
