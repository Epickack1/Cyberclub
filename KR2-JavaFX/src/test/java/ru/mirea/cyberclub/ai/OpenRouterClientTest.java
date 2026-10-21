package ru.mirea.cyberclub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Разбор каталога моделей OpenRouter. Проверяется без сети: форма ответа зафиксирована
 * тестом, чтобы рефакторинг не вернул ошибку, когда список брался из корня ответа,
 * а корень у {@code GET /models} — объект вида {@code {"data": [...]}}.
 */
class OpenRouterClientTest {

    private static final String CATALOG = """
            {
              "data": [
                {"id": "a/slow:free",  "pricing": {"prompt": "0"},   "context_length": 1000},
                {"id": "b/fast:free",   "pricing": {"prompt": "0"},   "context_length": 100000},
                {"id": "c/paid",        "pricing": {"prompt": "1.5"}, "context_length": 999999},
                {"id": "d/pricey:free", "pricing": {"prompt": "0.5"}, "context_length": 999999}
              ],
              "total_count": 4
            }
            """;

    private final ObjectMapper json = new ObjectMapper();
    private final OpenRouterClient client = new OpenRouterClient(
            new OpenRouterConfig("http://example.test", "test-key", "test-model"));

    private List<String> parse(String body) throws Exception {
        JsonNode root = json.readTree(body);
        return client.parseFreeModels(root);
    }

    @Test
    @DisplayName("Список берётся из поля data, а не из корня ответа")
    void freeModelsAreReadFromDataField() throws Exception {
        assertThat(parse(CATALOG))
                .containsExactly("b/fast:free", "a/slow:free");
    }

    @Test
    @DisplayName("Платные модели отбрасываются: и без суффикса :free, и с ненулевой ценой")
    void paidModelsAreFilteredOut() throws Exception {
        List<String> ids = parse(CATALOG);

        assertThat(ids)
                .doesNotContain("c/paid")
                .doesNotContain("d/pricey:free");
    }

    @Test
    @DisplayName("Корень без массива data даёт пустой список, а не исключение")
    void rootWithoutDataArrayIsSafe() throws Exception {
        assertThat(parse("{\"total_count\": 0}")).isEmpty();
        assertThat(parse("[]")).isEmpty();
        assertThat(parse("\"строка\"")).isEmpty();
    }

    @Test
    @DisplayName("Цена нулём принимается и в виде числа, и в виде строки")
    void zeroPriceAcceptedInBothJsonTypes() throws Exception {
        List<String> ids = parse("""
                {"data": [
                  {"id": "num/free:free", "pricing": {"prompt": 0}},
                  {"id": "dec/free:free", "pricing": {"prompt": 0.0}},
                  {"id": "str/free:free", "pricing": {"prompt": "0"}}
                ]}
                """);

        assertThat(ids).containsExactlyInAnyOrder("num/free:free", "dec/free:free", "str/free:free");
    }

    @Test
    @DisplayName("Отсутствующая цена не отменяет суффикс :free")
    void missingPriceStillTrustsFreeSuffix() throws Exception {
        List<String> ids = parse("""
                {"data": [{"id": "mystery/free:free"}]}
                """);

        assertThat(ids).containsExactly("mystery/free:free");
    }

    @Test
    @DisplayName("Явно заданная модель не заставляет ходить в каталог")
    void configuredModelNeedsNoNetwork() {
        assertThat(client.resolveModel()).isEqualTo("test-model");
        assertThat(client.config().configuredModel()).contains("test-model");
    }

    @Test
    @DisplayName("Ответ с ошибкой вида {\"error\":...} не считается каталогом моделей")
    void errorBodyIsNotAModelList() throws Exception {
        assertThat(parse("{\"error\": {\"message\": \"rate limited\"}}")).isEmpty();
    }

    // ------------------------------------------------------- откат по цепочке моделей

    /**
     * Клиент без сети: каталог и запросы к модели подменяются скриптом,
     * а пробованные модели записываются для проверки порядка.
     */
    private static final class ScriptedClient extends OpenRouterClient {
        private final List<String> free;
        private final java.util.function.Function<String, String> script;
        final List<String> tried = new ArrayList<>();

        ScriptedClient(String configuredModel, List<String> free,
                       java.util.function.Function<String, String> script) {
            super(new OpenRouterConfig("http://example.test", "test-key", configuredModel));
            this.free = free;
            this.script = script;
        }

        @Override
        public List<String> listFreeModels() {
            return free;
        }

        @Override
        String requestChat(String model, List<ChatMessage> messages) {
            tried.add(model);
            return script.apply(model);
        }
    }

    private static ScriptedClient restrictedFirst() {
        return new ScriptedClient(null,
                List.of("a/restricted:free", "b/works:free"),
                model -> {
                    if (model.startsWith("a/")) {
                        throw new OpenRouterException(
                                "OpenRouter ответил ошибкой 403: only available on agentic harnesses", 403);
                    }
                    return "ответ от " + model;
                });
    }

    @Test
    @DisplayName("Модель с 403 пропускается, берётся следующая рабочая")
    void skipsRestrictedModelAndTakesNext() {
        ScriptedClient client = restrictedFirst();

        String reply = client.chat(List.of(ChatMessage.user("привет")));

        assertThat(reply).isEqualTo("ответ от b/works:free");
        assertThat(client.tried).containsExactly("a/restricted:free", "b/works:free");
        assertThat(client.resolveModel()).isEqualTo("b/works:free");
    }

    @Test
    @DisplayName("Рабочая модель закрепляется: следующий запрос начинается сразу с неё")
    void workingModelIsRemembered() {
        ScriptedClient client = restrictedFirst();

        client.chat(List.of(ChatMessage.user("раз")));
        client.tried.clear();
        client.chat(List.of(ChatMessage.user("два")));

        assertThat(client.tried).containsExactly("b/works:free");
    }

    @Test
    @DisplayName("Пустой ответ модели тоже ведёт к следующей кандидатуре")
    void blankAnswerFallsThroughToNextModel() {
        ScriptedClient client = new ScriptedClient(null,
                List.of("a/silent:free", "b/talkative:free"),
                model -> model.startsWith("a/") ? "" : "настоящий ответ");

        assertThat(client.chat(List.of(ChatMessage.user("привет")))).isEqualTo("настоящий ответ");
        assertThat(client.tried).containsExactly("a/silent:free", "b/talkative:free");
    }

    @Test
    @DisplayName("Ошибка 401 (неверный ключ) не маскируется подменой модели")
    void unauthorizedDoesNotSwitchModels() {
        ScriptedClient client = new ScriptedClient(null,
                List.of("a/free:free", "b/free:free"),
                model -> {
                    throw new OpenRouterException("OpenRouter ответил ошибкой 401: Invalid API key", 401);
                });

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("привет"))))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("401");

        assertThat(client.tried).containsExactly("a/free:free");
    }

    @Test
    @DisplayName("Явно заданная модель: её ошибка показывается, а не перекрывается другой")
    void explicitModelErrorIsNotMasked() {
        ScriptedClient client = new ScriptedClient("custom/exact-model",
                List.of("a/free:free", "b/free:free"),
                model -> {
                    throw new OpenRouterException("OpenRouter ответил ошибкой 403: restricted", 403);
                });

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("привет"))))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("403");

        assertThat(client.tried).containsExactly("custom/exact-model");
    }

    @Test
    @DisplayName("Цепочка кандидатов ограничена — запрос не превращается в марафон")
    void candidateChainIsBounded() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add("p/model-" + i + ":free");
        }
        ScriptedClient client = new ScriptedClient(null, many,
                model -> {
                    throw new OpenRouterException("OpenRouter ответил ошибкой 429: rate limited", 429);
                });

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("привет"))))
                .isInstanceOf(OpenRouterException.class);

        assertThat(client.tried).hasSize(OpenRouterClient.MAX_MODEL_ATTEMPTS);
    }

    @Test
    @DisplayName("Ошибки сети не приводят к перебору моделей: интернета нет у всех")
    void networkErrorDoesNotTriggerFallback() {
        ScriptedClient client = new ScriptedClient(null,
                List.of("a/free:free", "b/free:free"),
                model -> {
                    throw new OpenRouterException("Не удалось связаться с OpenRouter: connection refused",
                            new java.io.IOException("connection refused"));
                });

        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("привет"))))
                .isInstanceOf(OpenRouterException.class)
                .hasMessageContaining("Не удалось связаться");

        assertThat(client.tried).containsExactly("a/free:free");
    }
}
