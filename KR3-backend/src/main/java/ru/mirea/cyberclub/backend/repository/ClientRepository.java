package ru.mirea.cyberclub.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.mirea.cyberclub.backend.domain.Client;

public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByNicknameIgnoreCaseAndIdNot(String nickname, Long id);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Поиск по нику, ФИО, телефону или email. Строка передаётся параметром, а не склеивается в запрос. */
    @Query("""
            select c from Client c
            where lower(c.nickname) like :pattern escape '\\'
               or lower(c.fullName) like :pattern escape '\\'
               or c.phone like :pattern escape '\\'
               or lower(c.email) like :pattern escape '\\'
            order by c.nickname""")
    List<Client> search(@Param("pattern") String pattern);
}
