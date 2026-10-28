package ru.mirea.cyberclub.repository.rest;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.DataAccessException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.exception.ValidationException;

/**
 * HTTP-клиент REST API киберклуба: отправляет запросы (java.net.http.HttpClient),
 * переводит объекты в JSON и обратно (Jackson), а ответ с ошибкой — в исключение того же
 * класса, что выбросил сервер. Поэтому сервисы и контроллеры JavaFX обрабатывают ошибки
 * одинаково при работе через REST и через JDBC.
 */
public class RestClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;

    /** @param baseUrl адрес REST API без завершающего «/», например http://localhost:8082/api */
    public RestClient(String baseUrl) {
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

    // ---------------------------------------------------------------- запросы

    public <T> T get(String path, Map<String, ?> params, TypeReference<T> type) {
        return read(send(request(path, params).GET()), type);
    }

    /** GET одной записи; 404 означает, что записи нет. */
    public <T> Optional<T> find(String path, Class<T> type) {
        HttpResponse<String> response = sendRaw(request(path, Map.of()).GET());
        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        check(response);
        return Optional.of(read(response, type));
    }

    public <T> T post(String path, Object body, Class<T> type) {
        return read(send(request(path, Map.of()).POST(jsonBody(body))), type);
    }

    public <T> T put(String path, Object body, Class<T> type) {
        return read(send(request(path, Map.of()).PUT(jsonBody(body))), type);
    }

    public void delete(String path) {
        send(request(path, Map.of()).DELETE());
    }

    /**
     * Проверка при запуске: сервер отвечает и видит базу данных (/actuator/health = UP).
     *
     * @return описание базы данных, к которой подключён сервер
     */
    public String checkHealth() {
        String root = baseUrl.endsWith("/api") ? baseUrl.substring(0, baseUrl.length() - 4) : baseUrl;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(root + "/actuator/health"))
                .timeout(TIMEOUT).header("Accept", "application/json").GET();
        HttpResponse<String> response = sendRaw(builder);
        JsonNode health = parse(response.body());
        String status = health.path("status").asText("UNKNOWN");
        JsonNode db = health.path("components").path("cyberclubDatabase").path("details");
        if (!"UP".equals(status)) {
            throw new DataAccessException("REST API " + baseUrl + " запущен, но не работает (status = " + status + "). "
                    + db.path("error").asText("Проверьте подключение сервера к PostgreSQL."));
        }
        return db.path("database").asText("");
    }

    // ------------------------------------------------------------- внутреннее

    private HttpRequest.Builder request(String path, Map<String, ?> params) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path + query(params)))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    private static String query(Map<String, ?> params) {
        Map<String, Object> present = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (value != null) {
                present.put(key, value);
            }
        });
        if (present.isEmpty()) {
            return "";
        }
        return present.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(String.valueOf(e.getValue()), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&", "?", ""));
    }

    private HttpRequest.BodyPublisher jsonBody(Object body) {
        try {
            return HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new AppException("Не удалось подготовить данные для отправки: " + e.getOriginalMessage(), e);
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        HttpResponse<String> response = sendRaw(builder);
        check(response);
        return response;
    }

    private HttpResponse<String> sendRaw(HttpRequest.Builder builder) {
        HttpRequest request = builder.build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (ConnectException e) {
            throw new DataAccessException("Нет связи с сервером REST API " + baseUrl
                    + ". Запустите cyberclub-backend (run.cmd) и повторите действие.", e);
        } catch (HttpTimeoutException e) {
            throw new DataAccessException("Сервер REST API " + baseUrl + " не ответил за "
                    + TIMEOUT.toSeconds() + " с.", e);
        } catch (IOException e) {
            throw new DataAccessException("Ошибка обмена с сервером REST API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("Запрос к серверу REST API прерван.", e);
        }
    }

    /** Ответ 4xx/5xx → исключение приложения по полю type тела ApiError. */
    private void check(HttpResponse<String> response) {
        int code = response.statusCode();
        if (code < 400) {
            return;
        }
        JsonNode error = parseQuietly(response.body());
        String type = error.path("type").asText("");
        String message = error.path("message").asText("Сервер вернул ошибку HTTP " + code + ".");
        String field = error.path("field").isTextual() ? error.path("field").asText() : null;
        throw switch (type) {
            case "ValidationException" -> new ValidationException(field, message);
            case "BusinessRuleException" -> new BusinessRuleException(message);
            case "EntityNotFoundException" -> new EntityNotFoundException(message);
            case "DataAccessException" -> new DataAccessException(message);
            default -> code == 404 ? new EntityNotFoundException(message) : new AppException(message);
        };
    }

    private <T> T read(HttpResponse<String> response, Class<T> type) {
        if (type == Void.class) {
            return null;
        }
        try {
            return json.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            throw new DataAccessException("Сервер вернул данные в неожиданном формате: " + e.getOriginalMessage(), e);
        }
    }

    private <T> T read(HttpResponse<String> response, TypeReference<T> type) {
        try {
            return json.readValue(response.body(), type);
        } catch (JsonProcessingException e) {
            throw new DataAccessException("Сервер вернул данные в неожиданном формате: " + e.getOriginalMessage(), e);
        }
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            throw new DataAccessException("Сервер вернул данные в неожиданном формате: " + e.getOriginalMessage(), e);
        }
    }

    private JsonNode parseQuietly(String body) {
        try {
            return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }
}
