package ru.mirea.cyberclub.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ru.mirea.cyberclub.exception.AppException;

/**
 * Клиент OpenRouter (https://openrouter.ai/api/v1) — формат запросов совместим с
 * OpenAI Chat Completions, поэтому используется стандартный {@link java.net.http.HttpClient}
 * из JDK без сторонних HTTP-библиотек, а сериализация — Jackson из SDK MCP.
 *
 * <h2>Как выбирается модель</h2>
 *
 * <p>Если модель задана явно в {@link OpenRouterConfig} — используется только она,
 * а её ошибки показываются как есть. Иначе берётся бесплатный каталог {@code GET /models}.
 * Каталог бесплатных моделей живёт своей жизнью, и одной проверки «id заканчивается на
 * :free» недостаточно: реальные замеры показали, что среди них</p>
 *
 * <ul>
 *     <li>часть отвечает 403 — «доступно только в агентных приложениях»;</li>
 *     <li>часть упирается в лимит — 429;</li>
 *     <li>часть отдаёт 200, но пустой текст ответа.</li>
 * </ul>
 *
 * <p>Поэтому {@link #chat(List)} идёт по цепочке кандидатов и закрепляет первую модель,
 * которая реально ответила. Раньше клиент брал первую попавшуюся и падал на 403.</p>
 */
public class OpenRouterClient {

    /** Модель считается рабочей, если ответила. Сколько кандидатов пробуем за один запрос. */
    static final int MAX_MODEL_ATTEMPTS = 4;

    /** Бесплатные модели — от большего контекста к меньшему. */
    private static final Comparator<JsonNode> FREE_MODEL_ORDER =
            Comparator.comparingInt((JsonNode m) -> m.path("context_length").asInt(0)).reversed();

    private final OpenRouterConfig config;
    private final ObjectMapper json;
    private final HttpClient http;

    /** Модель, которая подтверждена успешным ответом, либо выбрана явно. */
    private String resolvedModel;

    /** Каталог платится сетью один раз на процесс, а не на каждое сообщение. */
    private List<String> freeModelsCache;

    public OpenRouterClient(OpenRouterConfig config) {
        this(config, new ObjectMapper());
    }

    public OpenRouterClient(OpenRouterConfig config, ObjectMapper json) {
        this.config = config;
        this.json = json;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public OpenRouterConfig config() {
        return config;
    }

    /**
     * Идентификатор модели для отображения. Намеренно ходит в сеть только если
     * клиент уже знает рабочую модель: вызывается из интерфейса, а поток UI
     * блокировать нельзя.
     */
    public String resolveModel() {
        if (config.configuredModel().isPresent()) {
            return config.configuredModel().get();
        }
        if (resolvedModel != null) {
            return resolvedModel;
        }
        return OpenRouterConfig.FALLBACK_MODEL;
    }

    /**
     * Отправляет историю диалога и возвращает текст ответа ассистента.
     *
     * @param messages сообщения диалога; последнее обычно от пользователя
     * @return текст ответа модели, всегда непустой
     * @throws OpenRouterException если ни одна из пробуемых моделей не ответила
     */
    public String chat(List<ChatMessage> messages) {
        config.requireApiKey();
        if (messages == null || messages.isEmpty()) {
            throw new AppException("История диалога пуста: нечего отправлять модели.");
        }

        AppException last = null;
        for (String model : candidateModels()) {
            try {
                String reply = requestChat(model, messages);
                if (reply != null && !reply.isBlank()) {
                    // Модель доказала, что работает: начинаем с неё в следующий раз.
                    resolvedModel = model;
                    return reply.strip();
                }
                last = new OpenRouterException(
                        "Модель " + model + " вернула пустой ответ.", 0, true);
            } catch (OpenRouterException e) {
                if (!e.isRetryableWithOtherModel()) {
                    // Неверный ключ, не тот формат запроса и т.п. — смена модели не поможет.
                    throw e;
                }
                last = e;
            }
        }
        throw last != null
                ? last
                : new AppException("Не удалось получить ответ модели. Попробуйте позже.");
    }

    /**
     * Список моделей, которые будут пробоваться по порядку. При явно заданной модели
     * список состоит из неё одной — тогда ошибки не приглушаются подменой.
     */
    private List<String> candidateModels() {
        var explicit = config.configuredModel();
        if (explicit.isPresent()) {
            return List.of(explicit.get());
        }

        List<String> ordered = new ArrayList<>();
        if (resolvedModel != null) {
            ordered.add(resolvedModel);
        }
        for (String id : freeModels()) {
            if (!ordered.contains(id)) {
                ordered.add(id);
            }
        }
        if (ordered.isEmpty()) {
            ordered.add(OpenRouterConfig.FALLBACK_MODEL);
        }
        return List.copyOf(ordered.subList(0, Math.min(ordered.size(), MAX_MODEL_ATTEMPTS)));
    }

    /** Бесплатные модели каталога; недоступность каталога не должна ронять чат. */
    private List<String> freeModels() {
        if (freeModelsCache != null) {
            return freeModelsCache;
        }
        try {
            freeModelsCache = List.copyOf(listFreeModels());
        } catch (RuntimeException e) {
            freeModelsCache = List.of();
        }
        return freeModelsCache;
    }

    /** Идентификаторы всех моделей, доступных бесплатно, от большего контекста к меньшему. */
    public List<String> listFreeModels() {
        return parseFreeModels(send(get("models")));
    }

    /**
     * Разбор ответа {@code GET /models}. Выделен отдельно, чтобы форма ответа проверялась
     * юнит-тестом без сети: корень ответа — объект вида {@code {"data": [...], ...}},
     * и именно из поля {@code data} берётся список.
     *
     * @param root разобранный корень ответа
     * @return идентификаторы бесплатных моделей, от большего контекста к меньшему
     */
    List<String> parseFreeModels(JsonNode root) {
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<JsonNode> models = new ArrayList<>();
        for (JsonNode m : data) {
            if (isFree(m)) {
                models.add(m);
            }
        }
        models.sort(FREE_MODEL_ORDER);
        List<String> ids = new ArrayList<>(models.size());
        for (JsonNode m : models) {
            ids.add(m.path("id").asText());
        }
        return ids;
    }

    /**
     * Бесплатность определяется по суффиксу :free в идентификаторе — этого достаточно,
     * если цена почему-то не пришла. Найденную же цену проверяем способом, который
     * переживёт смену типа в JSON: и строку "0", и число 0 / 0.0.
     */
    private static boolean isFree(JsonNode model) {
        if (!model.path("id").asText().endsWith(":free")) {
            return false;
        }
        JsonNode price = model.path("pricing").path("prompt");
        if (price.isMissingNode() || price.isNull()) {
            return true;
        }
        try {
            return Double.parseDouble(price.asText()) == 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ HTTP

    /**
     * Один запрос к модели. Пустой ответ — не исключение, а признак сменить модель.
     *
     * <p>Метод package-private (а не private) намеренно: он — точка, где заканчивается
     * HTTP и начинается бизнес-решение «эта модель не годится», и цепочку откатов
     * иначе пришлось бы проверять настоящей сетью.</p>
     */
    String requestChat(String model, List<ChatMessage> messages) {
        ObjectNode body = json.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 1024);
        body.put("temperature", 0.7);
        ArrayNode arr = body.putArray("messages");
        for (ChatMessage m : messages) {
            ObjectNode item = arr.addObject();
            item.put("role", m.role());
            item.put("content", m.content());
        }

        JsonNode response = send(post("chat/completions", body));
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new OpenRouterException("Модель " + model
                    + " не вернула ни одного варианта ответа: " + abbreviate(response.toString()), 0, true);
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull()) {
            return "";
        }
        return content.asText();
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create(config.baseUrl() + "/" + path))
                .timeout(Duration.ofSeconds(45))
                .header("Authorization", "Bearer " + config.apiKey())
                .header("Accept", "application/json")
                .header("X-Title", "CyberClub KR2")
                .GET()
                .build();
    }

    private HttpRequest post(String path, JsonNode body) {
        try {
            return HttpRequest.newBuilder(URI.create(config.baseUrl() + "/" + path))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("X-Title", "CyberClub KR2")
                    .header("HTTP-Referer", "https://github.com/Epickack1/Cyberclub")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AppException("Не удалось сформировать JSON-запрос: " + e.getMessage(), e);
        }
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new OpenRouterException("Не удалось связаться с OpenRouter (" + config.baseUrl()
                    + "): " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenRouterException("Запрос к OpenRouter прерван.", e);
        }

        String raw = response.body() == null ? "" : response.body();
        JsonNode parsed = parseQuietly(raw);
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new OpenRouterException("OpenRouter ответил ошибкой " + status
                    + ": " + errorMessage(parsed, raw), status);
        }
        if (parsed == null) {
            throw new OpenRouterException("Некорректный ответ OpenRouter: " + abbreviate(raw), 0, false);
        }
        return parsed;
    }

    /** OpenRouter отдаёт ошибки и в виде {"error":{...}}, и в виде {"detail":"..."}. */
    private static String errorMessage(JsonNode parsed, String raw) {
        if (parsed != null) {
            if (parsed.hasNonNull("error")) {
                JsonNode error = parsed.path("error");
                if (error.isObject() && error.hasNonNull("message")) {
                    return error.path("message").asText();
                }
                return error.asText();
            }
            if (parsed.hasNonNull("detail")) {
                return parsed.path("detail").asText();
            }
        }
        return abbreviate(raw);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 300 ? flat : flat.substring(0, 300) + "…";
    }

    private JsonNode parseQuietly(String raw) {
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }
}
