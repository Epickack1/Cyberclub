package ru.mirea.cyberclub.auth;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.AuthenticationException;
import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.DataAccessException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.exception.ValidationException;

/**
 * HTTP-клиент сервера КР3 для входа и администрирования пользователей
 * ({@code /api/auth/**}, {@code /api/admin/users/**}).
 *
 * <p>Ошибки сервера приходят в JSON {@code {status, code, message, validationErrors}} и
 * превращаются в исключения приложения, поэтому формы показывают их так же, как ошибки
 * проверки данных: неверное поле подсвечивается, текст — под полями.</p>
 */
public class AuthApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;

    /** @param baseUrl адрес API сервера КР3 без «/» в конце, например http://localhost:8081/api */
    public AuthApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Ответ сервера на вход и обновление токенов. */
    public record Tokens(String accessToken, Instant accessExpiresAt, String refreshToken, UserInfo user) {
    }

    // ------------------------------------------------------------------ вход

    public Tokens login(String email, String password) {
        return send("POST", "/auth/login", Map.of("email", email, "password", password), null, Tokens.class);
    }

    public Tokens refresh(String refreshToken) {
        return send("POST", "/auth/refresh", Map.of("refreshToken", refreshToken), null, Tokens.class);
    }

    public void logout(String refreshToken) {
        send("POST", "/auth/logout", Map.of("refreshToken", refreshToken), null, Void.class);
    }

    // ------------------------------------------------------------ пользователи

    public List<UserInfo> users(String token) {
        return send("GET", "/admin/users", null, token, new TypeReference<List<UserInfo>>() { });
    }

    public UserInfo createUser(String token, String email, String displayName, String password, Set<Role> roles) {
        Map<String, Object> body = Map.of("email", email, "displayName", displayName,
                "password", password, "roles", roles);
        return send("POST", "/admin/users", body, token, UserInfo.class);
    }

    public UserInfo setEnabled(String token, UUID id, boolean enabled) {
        return send("POST", "/admin/users/" + id + "/enabled", Map.of("enabled", enabled), token, UserInfo.class);
    }

    public UserInfo resetPassword(String token, UUID id, String password) {
        return send("POST", "/admin/users/" + id + "/password", Map.of("password", password), token, UserInfo.class);
    }

    public UserInfo unlock(String token, UUID id) {
        return send("POST", "/admin/users/" + id + "/unlock", null, token, UserInfo.class);
    }

    // ------------------------------------------------------------- внутреннее

    private <T> T send(String method, String path, Object body, String token, Class<T> type) {
        HttpResponse<String> response = exchange(method, path, body, token);
        if (type == Void.class || response.body() == null || response.body().isBlank()) {
            return null;
        }
        try {
            return json.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            throw unexpectedFormat(e);
        }
    }

    private <T> T send(String method, String path, Object body, String token, TypeReference<T> type) {
        HttpResponse<String> response = exchange(method, path, body, token);
        try {
            return json.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            throw unexpectedFormat(e);
        }
    }

    private HttpResponse<String> exchange(String method, String path, Object body, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            String text = body == null ? "" : json.writeValueAsString(body);
            request.method(method, body == null && "GET".equals(method)
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(text, StandardCharsets.UTF_8));
            HttpResponse<String> response = http.send(request.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            check(response);
            return response;
        } catch (ConnectException e) {
            throw new DataAccessException("Нет связи с сервером авторизации " + baseUrl
                    + ". Запустите сервер КР3 (KR3-backend) или отключите вход: auth.enabled=false.", e);
        } catch (HttpTimeoutException e) {
            throw new DataAccessException("Сервер авторизации " + baseUrl + " не ответил за "
                    + TIMEOUT.toSeconds() + " с.", e);
        } catch (IOException e) {
            throw new DataAccessException("Ошибка обмена с сервером авторизации: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Запрос к серверу авторизации прерван.", e);
        }
    }

    /** Ответ 4xx/5xx → исключение приложения с текстом сервера. */
    private void check(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 400) {
            return;
        }
        JsonNode error = parseQuietly(response.body());
        String message = error.path("message").asText("Сервер авторизации вернул ошибку HTTP " + status + ".");
        JsonNode fields = error.path("validationErrors");
        if (status == 400 && fields.isObject() && fields.size() > 0) {
            Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
            Map.Entry<String, JsonNode> first = it.next();
            throw new ValidationException(first.getKey(), first.getValue().asText(message));
        }
        throw switch (status) {
            case 400 -> new ValidationException(message);
            case 401, 423 -> new AuthenticationException(message);
            case 403 -> new BusinessRuleException("Недостаточно прав для этой операции.");
            case 404 -> new EntityNotFoundException(message);
            case 409 -> new ValidationException("email", message);
            default -> new AppException(message);
        };
    }

    private JsonNode parseQuietly(String body) {
        try {
            return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }

    private static DataAccessException unexpectedFormat(JsonProcessingException e) {
        return new DataAccessException("Сервер авторизации вернул данные в неожиданном формате: "
                + e.getOriginalMessage(), e);
    }
}
