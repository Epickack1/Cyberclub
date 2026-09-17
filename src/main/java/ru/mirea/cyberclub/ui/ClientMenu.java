package ru.mirea.cyberclub.ui;

import java.time.LocalDate;

import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Меню «Клиенты»: CRUD и поиск по клиентам.
 */
public class ClientMenu extends AbstractMenu {

    private final ClientService clientService;
    private final TablePrinter tables;

    public ClientMenu(ConsoleIO io, ClientService clientService) {
        super(io, "Клиенты");
        this.clientService = clientService;
        this.tables = new TablePrinter(io);
        add("Список клиентов", this::listAll);
        add("Добавить клиента", this::create);
        add("Найти клиента по ID", this::findById);
        add("Изменить клиента", this::update);
        add("Удалить клиента", this::delete);
        add("Поиск по нику или ФИО", this::search);
    }

    private void listAll() {
        tables.printClients(clientService.getAll());
    }

    private void create() {
        io.println("Новый клиент (пустая строка - отмена):");
        Client client = new Client();
        client.setNickname(io.readLine("Ник (3-20 символов, латиница/цифры/_):"));
        client.setFullName(io.readLine("ФИО:"));
        client.setPhone(io.readLine("Телефон (+7XXXXXXXXXX):"));
        client.setEmail(io.readLine("Email:"));
        client.setBirthDate(io.readDate("Дата рождения"));
        Client saved = clientService.create(client);
        io.printSuccess("Клиент добавлен: " + saved.describe());
    }

    private void findById() {
        long id = io.readLong("Введите ID клиента:");
        Client client = clientService.getById(id);
        tables.printClients(java.util.List.of(client));
        io.println("Зарегистрирован: " + DateTimeUtil.format(client.getCreatedAt())
                + ", возраст: " + client.getAge(LocalDate.now()));
    }

    private void update() {
        long id = io.readLong("Введите ID клиента:");
        Client client = clientService.getById(id);
        tables.printClients(java.util.List.of(client));
        io.println("Введите новые значения (пустая строка - оставить текущее):");

        String nickname = io.readOptionalLine("Ник [" + client.getNickname() + "]:");
        if (nickname != null) {
            client.setNickname(nickname);
        }
        String fullName = io.readOptionalLine("ФИО [" + client.getFullName() + "]:");
        if (fullName != null) {
            client.setFullName(fullName);
        }
        String phone = io.readOptionalLine("Телефон [" + client.getPhone() + "]:");
        if (phone != null) {
            client.setPhone(phone);
        }
        String email = io.readOptionalLine("Email [" + client.getEmail() + "]:");
        if (email != null) {
            client.setEmail(email);
        }
        LocalDate birthDate = io.readOptionalDate("Дата рождения [" + DateTimeUtil.format(client.getBirthDate()) + "]");
        if (birthDate != null) {
            client.setBirthDate(birthDate);
        }
        Client saved = clientService.update(client);
        io.printSuccess("Клиент обновлён: " + saved.describe());
    }

    private void delete() {
        long id = io.readLong("Введите ID клиента:");
        Client client = clientService.getById(id);
        if (!io.confirm("Удалить клиента " + client.getNickname() + " (" + client.getFullName() + ")?")) {
            io.println("Удаление отменено.");
            return;
        }
        clientService.delete(id);
        io.printSuccess("Клиент #" + id + " удалён.");
    }

    private void search() {
        String query = io.readLine("Часть ника или ФИО:");
        tables.printClients(clientService.search(query));
    }
}
