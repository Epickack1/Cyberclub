package ru.mirea.cyberclub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.auth.AuthApiClient;
import ru.mirea.cyberclub.auth.Role;
import ru.mirea.cyberclub.auth.UserInfo;
import ru.mirea.cyberclub.exception.AuthenticationException;
import ru.mirea.cyberclub.exception.ValidationException;

/** Вход и регистрация пользователей без сети: HTTP-клиент сервера КР3 заменён заглушкой. */
class AuthServicesTest {

    /** Заглушка сервера КР3: запоминает вызовы и выдаёт токены с заданным сроком. */
    private static final class FakeServer extends AuthApiClient {
        final List<String> calls = new ArrayList<>();
        Instant accessExpiresAt = Instant.now().plusSeconds(900);
        boolean refreshFails;

        FakeServer() {
            super("http://fake/api");
        }

        private Tokens tokens(String access) {
            UserInfo admin = new UserInfo(UUID.randomUUID(), "admin@club.local", "Админ", Set.of(Role.ADMIN),
                    null, true, false, null, null, Instant.now());
            return new Tokens(access, accessExpiresAt, "refresh-" + access, admin);
        }

        @Override
        public Tokens login(String email, String password) {
            calls.add("login " + email);
            if (!"Club2026root".equals(password)) {
                throw new AuthenticationException("Неверный email или пароль.");
            }
            if (email.startsWith("guest")) {
                UserInfo guest = new UserInfo(UUID.randomUUID(), email, "Гость", Set.of(Role.CLIENT), 7L,
                        true, false, null, null, Instant.now());
                return new Tokens("c1", accessExpiresAt, "refresh-c1", guest);
            }
            return tokens("a1");
        }

        @Override
        public void logout(String refreshToken) {
            calls.add("logout " + refreshToken);
        }

        @Override
        public Tokens refresh(String refreshToken) {
            calls.add("refresh " + refreshToken);
            if (refreshFails) {
                throw new AuthenticationException("Сессия истекла.");
            }
            accessExpiresAt = Instant.now().plusSeconds(900);
            return tokens("a2");
        }

        @Override
        public List<UserInfo> users(String token) {
            calls.add("users " + token);
            return List.of();
        }

        @Override
        public UserInfo createUser(String token, String email, String displayName, String password, Set<Role> roles) {
            calls.add("create " + email + " " + displayName + " " + roles.stream().map(Enum::name).sorted().toList());
            return new UserInfo(UUID.randomUUID(), email, displayName, roles,
                    roles.contains(Role.CLIENT) ? 7L : null, true, false, null, null, Instant.now());
        }
    }

    private final FakeServer server = new FakeServer();
    private final AuthService auth = new AuthService(server);
    private final UserAdminService users = new UserAdminService(server, auth);

    @Test
    void loginValidatesFieldsBeforeCallingServer() {
        assertThatThrownBy(() -> auth.login("  ", "x")).isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getField()).isEqualTo("email");
        assertThatThrownBy(() -> auth.login("not-an-email", "x")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> auth.login("admin@club.local", "")).isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getField()).isEqualTo("password");
        assertThat(server.calls).isEmpty();
    }

    @Test
    void loginStoresSessionAndRole() {
        UserInfo user = auth.login(" admin@club.local ", "Club2026root");
        assertThat(user.mainRole()).isEqualTo(Role.ADMIN);
        assertThat(auth.isLoggedIn()).isTrue();
        assertThat(auth.hasRole(Role.ADMIN)).isTrue();
        assertThat(server.calls).containsExactly("login admin@club.local");
    }

    @Test
    void wrongPasswordShowsServerMessage() {
        assertThatThrownBy(() -> auth.login("admin@club.local", "wrong1234"))
                .isInstanceOf(AuthenticationException.class).hasMessage("Неверный email или пароль.");
        assertThat(auth.isLoggedIn()).isFalse();
    }

    @Test
    void expiredAccessTokenIsRefreshedAutomatically() {
        server.accessExpiresAt = Instant.now().minusSeconds(5);
        auth.login("admin@club.local", "Club2026root");
        users.getAll();
        assertThat(server.calls).containsExactly("login admin@club.local", "refresh refresh-a1", "users a2");
    }

    @Test
    void endedSessionRequiresNewLogin() {
        server.accessExpiresAt = Instant.now().minusSeconds(5);
        server.refreshFails = true;
        auth.login("admin@club.local", "Club2026root");
        assertThatThrownBy(users::getAll).isInstanceOf(AuthenticationException.class);
        assertThat(auth.isLoggedIn()).isFalse();
    }

    @Test
    void registerChecksFieldsLikeServer() {
        auth.login("admin@club.local", "Club2026root");
        assertField(() -> users.register("bad", "Иван", "Smena2026", "Smena2026", Role.OPERATOR), "email");
        assertField(() -> users.register("ivan@club.local", "И", "Smena2026", "Smena2026", Role.OPERATOR), "displayName");
        assertField(() -> users.register("ivan@club.local", "Иван", "short1", "short1", Role.OPERATOR), "password");
        assertField(() -> users.register("ivan@club.local", "Иван", "12345678", "12345678", Role.OPERATOR), "password");
        assertField(() -> users.register("ivan@club.local", "Иван", "ivan20261", "ivan20261", Role.OPERATOR), "password");
        assertField(() -> users.register("ivan@club.local", "Иван", "Smena2026", "Smena2027", Role.OPERATOR), "passwordRepeat");
        assertField(() -> users.register("ivan@club.local", "Иван", "Smena2026", "Smena2026", null), "roles");
        assertThat(server.calls).containsExactly("login admin@club.local");
    }

    @Test
    void registerSendsNormalizedEmailAndRole() {
        auth.login("admin@club.local", "Club2026root");
        UserInfo created = users.register(" Ivan@Club.Local ", " Иван Петров ", "Smena2026", "Smena2026", Role.OPERATOR);
        assertThat(created.email()).isEqualTo("ivan@club.local");
        assertThat(server.calls).last().isEqualTo("create ivan@club.local Иван Петров [OPERATOR]");
    }

    @Test
    void clientAccountCannotEnterStaffApp() {
        assertThatThrownBy(() -> auth.login("guest@mail.test", "Club2026root"))
                .isInstanceOf(AuthenticationException.class).hasMessageContaining("клиента клуба");
        assertThat(auth.isLoggedIn()).isFalse();
        assertThat(server.calls).containsExactly("login guest@mail.test", "logout refresh-c1");
    }

    @Test
    void registerClientSendsClientRoleOnly() {
        auth.login("admin@club.local", "Club2026root");
        UserInfo created = users.registerClient("Guest@Mail.Test", "Гость Сайта", "Visit2026site", "Visit2026site");
        assertThat(created.isClient()).isTrue();
        assertThat(created.clientId()).isEqualTo(7L);
        assertThat(server.calls).last().isEqualTo("create guest@mail.test Гость Сайта [CLIENT]");
    }

    @Test
    void temporaryPasswordSatisfiesPolicy() {
        for (int i = 0; i < 200; i++) {
            String password = UserAdminService.temporaryPassword();
            UserAdminService.validatePassword(password, "employee@club.local", "password");
            assertThat(password).hasSize(10);
        }
    }

    private static void assertField(Runnable action, String field) {
        assertThatThrownBy(action::run).isInstanceOf(ValidationException.class)
                .extracting(e -> ((ValidationException) e).getField()).isEqualTo(field);
    }
}
