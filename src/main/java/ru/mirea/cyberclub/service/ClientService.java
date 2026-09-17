package ru.mirea.cyberclub.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.repository.BookingRepository;
import ru.mirea.cyberclub.repository.ClientRepository;

/**
 * Бизнес-логика работы с клиентами: валидация данных, уникальность,
 * возрастное ограничение, запрет удаления клиента с незавершёнными бронями.
 */
public class ClientService {

    public static final int MIN_AGE = 12;

    private static final Pattern NICKNAME = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final Pattern PHONE = Pattern.compile("^\\+7\\d{10}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private final ClientRepository clientRepository;
    private final BookingRepository bookingRepository;
    private final Clock clock;

    public ClientService(ClientRepository clientRepository, BookingRepository bookingRepository) {
        this(clientRepository, bookingRepository, Clock.systemDefaultZone());
    }

    public ClientService(ClientRepository clientRepository, BookingRepository bookingRepository, Clock clock) {
        this.clientRepository = clientRepository;
        this.bookingRepository = bookingRepository;
        this.clock = clock;
    }

    public Client create(Client client) {
        normalize(client);
        validate(client);
        checkUnique(client);
        return clientRepository.save(client);
    }

    public List<Client> getAll() {
        return clientRepository.findAll();
    }

    public Client getById(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Клиент", id));
    }

    public Client update(Client client) {
        getById(client.getId());
        normalize(client);
        validate(client);
        checkUnique(client);
        if (!clientRepository.update(client)) {
            throw new EntityNotFoundException("Клиент", client.getId());
        }
        return getById(client.getId());
    }

    /** Правило: нельзя удалить клиента, у которого есть незавершённые бронирования. */
    public void delete(Long id) {
        Client client = getById(id);
        if (bookingRepository.existsUnfinishedByClientId(id)) {
            throw new BusinessRuleException("Нельзя удалить клиента " + client.getNickname()
                    + ": у него есть незавершённые бронирования (создано/подтверждено/активно).");
        }
        if (!clientRepository.deleteById(id)) {
            throw new EntityNotFoundException("Клиент", id);
        }
    }

    public List<Client> search(String query) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("Строка поиска не может быть пустой.");
        }
        return clientRepository.searchByNicknameOrName(query);
    }

    public long count() {
        return clientRepository.count();
    }

    // ----------------------------------------------------------------- правила

    /** Приводит поля к каноническому виду: обрезает пробелы, убирает разделители в телефоне. */
    private void normalize(Client client) {
        client.setNickname(trim(client.getNickname()));
        client.setFullName(trim(client.getFullName()));
        String phone = trim(client.getPhone());
        client.setPhone(phone == null ? null : phone.replaceAll("[\\s()-]", ""));
        String email = trim(client.getEmail());
        client.setEmail(email == null ? null : email.toLowerCase());
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Правило: все обязательные поля заполнены и имеют корректный формат. */
    private void validate(Client client) {
        if (client.getNickname() == null) {
            throw new ValidationException("Ник клиента обязателен.");
        }
        if (!NICKNAME.matcher(client.getNickname()).matches()) {
            throw new ValidationException("Ник: 3-20 символов, только латиница, цифры и \"_\".");
        }
        if (client.getFullName() == null) {
            throw new ValidationException("ФИО клиента обязательно.");
        }
        if (client.getFullName().length() > 100) {
            throw new ValidationException("ФИО не может быть длиннее 100 символов.");
        }
        if (client.getPhone() == null || !PHONE.matcher(client.getPhone()).matches()) {
            throw new ValidationException("Телефон должен быть в формате +7XXXXXXXXXX.");
        }
        if (client.getEmail() == null || !EMAIL.matcher(client.getEmail()).matches()) {
            throw new ValidationException("Некорректный email.");
        }
        LocalDate today = LocalDate.now(clock);
        if (client.getBirthDate() == null) {
            throw new ValidationException("Дата рождения обязательна.");
        }
        if (client.getBirthDate().isAfter(today)) {
            throw new ValidationException("Дата рождения не может быть в будущем.");
        }
        if (client.getAge(today) < MIN_AGE) {
            throw new BusinessRuleException("Клиент клуба должен быть не младше " + MIN_AGE + " лет.");
        }
    }

    /** Правило: ник, телефон и email уникальны среди клиентов. */
    private void checkUnique(Client client) {
        ensureFree(clientRepository.findByNickname(client.getNickname()), client, "Ник \"" + client.getNickname() + "\" уже занят.");
        ensureFree(clientRepository.findByPhone(client.getPhone()), client, "Телефон " + client.getPhone() + " уже зарегистрирован.");
        ensureFree(clientRepository.findByEmail(client.getEmail()), client, "Email " + client.getEmail() + " уже зарегистрирован.");
    }

    private static void ensureFree(Optional<Client> existing, Client client, String message) {
        if (existing.isPresent() && !existing.get().getId().equals(client.getId())) {
            throw new ValidationException(message);
        }
    }
}
