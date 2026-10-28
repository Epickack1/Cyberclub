package ru.mirea.cyberclub.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ru.mirea.cyberclub.backend.domain.Role;
import ru.mirea.cyberclub.backend.domain.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    /** Email хранится в нижнем регистре, поэтому ищем по нормализованному значению. */
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByClientId(Long clientId);

    List<UserAccount> findAllByOrderByEmailAsc();

    /** Сколько включённых пользователей с ролью — нельзя оставить систему без администратора. */
    @Query("select count(u) from UserAccount u join u.roles r where r = :role and u.enabled = true")
    long countEnabledWithRole(Role role);
}
