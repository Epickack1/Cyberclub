package ru.mirea.cyberclub.repository;

import java.util.List;
import java.util.Optional;

import ru.mirea.cyberclub.model.Client;

/**
 * Хранилище клиентов клуба.
 */
public interface ClientRepository extends CrudRepository<Client, Long> {

    Optional<Client> findByNickname(String nickname);

    Optional<Client> findByPhone(String phone);

    Optional<Client> findByEmail(String email);

    /** Поиск по части ника или ФИО без учёта регистра. */
    List<Client> searchByNicknameOrName(String query);
}
