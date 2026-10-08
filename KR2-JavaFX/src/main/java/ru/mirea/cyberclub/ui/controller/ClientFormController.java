package ru.mirea.cyberclub.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.ui.util.FormInput;

/**
 * Форма клиента (client-form.fxml): добавление и редактирование.
 * Формат ника/телефона/email, уникальность и возраст проверяет {@link ClientService}.
 */
public class ClientFormController extends FormController<Client> {

    @FXML
    private TextField nicknameField;
    @FXML
    private TextField fullNameField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField emailField;
    @FXML
    private DatePicker birthDatePicker;

    private final ClientService clientService;

    /** Редактируемый клиент; null — режим добавления. */
    private Client editing;

    public ClientFormController(AppContext context) {
        this.clientService = context.clients();
    }

    /** Вызывается FXMLLoader после внедрения полей с fx:id. */
    @FXML
    private void initialize() {
        FormInput.setupDatePicker(birthDatePicker);
        registerField("nickname", nicknameField);
        registerField("fullName", fullNameField);
        registerField("phone", phoneField);
        registerField("email", emailField);
        registerField("birthDate", birthDatePicker);
    }

    /** Режим редактирования: поля заполняются текущими значениями клиента. */
    public void edit(Client client) {
        this.editing = client;
        nicknameField.setText(client.getNickname());
        fullNameField.setText(client.getFullName());
        phoneField.setText(client.getPhone());
        emailField.setText(client.getEmail());
        birthDatePicker.setValue(client.getBirthDate());
    }

    @Override
    public String title() {
        return editing == null ? "Новый клиент" : "Изменение клиента " + editing.getNickname();
    }

    @Override
    protected Client submit() {
        // Новый объект, а не editing: при ошибке строка в таблице не должна измениться
        Client client = new Client(
                FormInput.text(nicknameField, "nickname", "Ник", true, 20),
                FormInput.text(fullNameField, "fullName", "ФИО", true, 100),
                FormInput.text(phoneField, "phone", "Телефон", true),
                FormInput.text(emailField, "email", "Email", true, 100),
                FormInput.date(birthDatePicker, "birthDate", "Дата рождения", true));
        if (editing == null) {
            return clientService.create(client);
        }
        client.setId(editing.getId());
        return clientService.update(client);
    }
}
