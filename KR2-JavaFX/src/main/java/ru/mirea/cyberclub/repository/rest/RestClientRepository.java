package ru.mirea.cyberclub.repository.rest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.repository.ClientRepository;

/**
 * REST-реализация хранилища клиентов: каждый метод — один запрос к /api/clients.
 */
public class RestClientRepository implements ClientRepository {

    private static final TypeReference<List<Client>> LIST = new TypeReference<>() { };

    private final RestClient rest;

    public RestClientRepository(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Client save(Client client) {
        return rest.post("/clients", client, Client.class);
    }

    @Override
    public Optional<Client> findById(Long id) {
        return rest.find("/clients/" + id, Client.class);
    }

    @Override
    public List<Client> findAll() {
        return rest.get("/clients", Map.of(), LIST);
    }

    @Override
    public boolean update(Client client) {
        rest.put("/clients/" + client.getId(), client, Client.class);
        return true;
    }

    @Override
    public boolean deleteById(Long id) {
        rest.delete("/clients/" + id);
        return true;
    }

    @Override
    public long count() {
        return rest.get("/clients/count", Map.of(), new TypeReference<Long>() { });
    }

    @Override
    public Optional<Client> findByNickname(String nickname) {
        return first(Map.of("nickname", nickname));
    }

    @Override
    public Optional<Client> findByPhone(String phone) {
        return first(Map.of("phone", phone));
    }

    @Override
    public Optional<Client> findByEmail(String email) {
        return first(Map.of("email", email));
    }

    @Override
    public List<Client> searchByNicknameOrName(String query) {
        return rest.get("/clients", Map.of("q", query), LIST);
    }

    private Optional<Client> first(Map<String, ?> params) {
        return rest.get("/clients", params, LIST).stream().findFirst();
    }
}
