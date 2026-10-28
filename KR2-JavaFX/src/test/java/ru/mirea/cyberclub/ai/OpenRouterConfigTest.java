package ru.mirea.cyberclub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.exception.ValidationException;

/**
 * Настройки OpenRouter: ключ не должен утекать в журнал, а его отсутствие —
 * давать понятную подсказку, а не ошибку HTTP 401 от сервера.
 */
class OpenRouterConfigTest {

    private static OpenRouterConfig config(String apiKey, String model) {
        return new OpenRouterConfig(OpenRouterConfig.DEFAULT_BASE_URL, apiKey, model);
    }

    @Test
    @DisplayName("Ключ скрыт в toString — он не попадёт в журнал")
    void apiKeyHiddenInToString() {
        OpenRouterConfig c = config("sk-or-secret-value", "some/model");

        assertThat(c.toString())
                .doesNotContain("sk-or-secret-value")
                .contains("***")
                .contains("some/model");
    }

    @Test
    @DisplayName("Пустой ключ показывается как «не задан», а не как пустая строка")
    void blankApiKeyIsShownAsMissing() {
        assertThat(config("", null).toString())
                .contains("<не задан>")
                .contains("<бесплатная по умолчанию>");
    }

    @Test
    @DisplayName("Без ключа requireApiKey подсказывает, где его взять")
    void requireApiKeyExplainsWhereToGetKey() {
        assertThatThrownBy(() -> config(null, null).requireApiKey())
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("OPENROUTER_API_KEY")
                .hasMessageContaining("openrouter.ai/keys");
    }

    @Test
    @DisplayName("С ключом проверка проходит")
    void requireApiKeyPassesWithKey() {
        assertThatCode(() -> config("sk-or-abc", null).requireApiKey()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("hasApiKey учитывает пустые значения и пробелы")
    void hasApiKeyChecksBlanks() {
        assertThat(config("sk-or-abc", null).hasApiKey()).isTrue();
        assertThat(config("   ", null).hasApiKey()).isFalse();
        assertThat(config(null, null).hasApiKey()).isFalse();
    }

    @Test
    @DisplayName("configuredModel: пустое значение превращается в пустой Optional")
    void configuredModelTreatsBlankAsEmpty() {
        assertThat(config(null, "").configuredModel()).isEmpty();
        assertThat(config(null, "  ").configuredModel()).isEmpty();
        assertThat(config(null, "meta/llama").configuredModel()).contains("meta/llama");
    }

    @Test
    @DisplayName("Базовый адрес по умолчанию указывает на api/v1 OpenRouter")
    void defaultBaseUrl() {
        assertThat(OpenRouterConfig.DEFAULT_BASE_URL)
                .isEqualTo("https://openrouter.ai/api/v1");
        assertThat(OpenRouterConfig.load().baseUrl()).isNotBlank();
    }
}
