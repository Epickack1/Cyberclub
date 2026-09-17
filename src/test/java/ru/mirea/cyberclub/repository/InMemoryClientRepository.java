package ru.mirea.cyberclub.repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ru.mirea.cyberclub.model.Client;

/**
 * Хранилище клиентов в памяти для модульных тестов сервисов
 * (та же реализация интерфейса, что и JDBC, но без базы данных).
 */
public class InMemoryClientRepository implements ClientRepository {

    private final Map<Long, Client> storage = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Client save(Client client) {
        client.setId(nextId++);
        storage.put(client.getId(), client);
        return client;
    }

    @Override
    public Optional<Client> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Client> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean update(Client client) {
        return storage.replace(client.getId(), client) != null;
    }

    @Override
    public boolean deleteById(Long id) {
        return storage.remove(id) != null;
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public Optional<Client> findByNickname(String nickname) {
        return storage.values().stream().filter(c -> c.getNickname().equalsIgnoreCase(nickname)).findFirst();
    }

    @Override
    public Optional<Client> findByPhone(String phone) {
        return storage.values().stream().filter(c -> c.getPhone().equals(phone)).findFirst();
    }

    @Override
    public Optional<Client> findByEmail(String email) {
        return storage.values().stream().filter(c -> c.getEmail().equalsIgnoreCase(email)).findFirst();
    }

    @Override
    public List<Client> searchByNicknameOrName(String query) {
        String q = query.toLowerCase();
        return storage.values().stream()
                .filter(c -> c.getNickname().toLowerCase().contains(q) || c.getFullName().toLowerCase().contains(q))
                .toList();
    }
}
