package ru.mirea.cyberclub.ai;

import java.util.Optional;

import ru.mirea.cyberclub.exception.ValidationException;

/**
 * Настройки подключения к OpenRouter — единому шлюзу к сотням моделей разных провайдеров.
 * Формат запросов у OpenRouter совместим с OpenAI Chat Completions.
 *
 * <p>API-ключ читается только из переменной окружения {@code OPENROUTER_API_KEY} и
 * не попадает ни в репозиторий, ни в {@code toString()}, а значит и в журнал —
 * как и пароль базы данных в {@code DatabaseConfig}.</p>
 *
 * <p>Модель указывать необязательно: если переменной нет, приложение само выберет первую
 * бесплатную из каталога OpenRouter. Это сделано намеренно — бесплатный каталог меняется,
 * и жёстко зашитый идентификатор устаревает.</p>
 *
 * @param baseUrl базовый адрес API
 * @param apiKey  ключ доступа, может быть пустым, пока не задан в окружении
 * @param model   идентификатор модели или null — тогда будет выбрана бесплатная
 */
public record OpenRouterConfig(String baseUrl, String apiKey, String model) {

    private static final String RESOURCE = "/openrouter.properties";

    /** Базовый адрес OpenRouter (OpenAI-совместимый). */
    public static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

    /**
     * Подстраховка, если каталог недоступен по сети. Берётся большая универсальная
     * бесплатная модель; при наличии связи фактический выбор делается по ответу /models.
     */
    public static final String FALLBACK_MODEL = "nvidia/nemotron-3-super-120b-a12b:free";

    /**
     * Читает настройки: переменная окружения имеет приоритет над файлом настроек,
     * как и в {@code DatabaseConfig}.
     */
    public static OpenRouterConfig load() {
        return new OpenRouterConfig(
                env("OPENROUTER_BASE_URL", prop("openrouter.base.url", DEFAULT_BASE_URL)),
                env("OPENROUTER_API_KEY", prop("openrouter.api.key", "")),
                env("OPENROUTER_MODEL", prop("openrouter.model", "")));
    }

    /** Конфиг только из окружения — удобно для тестов и для MCP-процесса. */
    public static OpenRouterConfig fromEnvironment() {
        return new OpenRouterConfig(env("OPENROUTER_BASE_URL", DEFAULT_BASE_URL),
                env("OPENROUTER_API_KEY", ""),
                env("OPENROUTER_MODEL", ""));
    }

    private static String prop(String key, String defaultValue) {
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = OpenRouterConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return defaultValue;
            }
            props.load(in);
        } catch (java.io.IOException e) {
            return defaultValue;
        }
        return props.getProperty(key, defaultValue);
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Явно заданный модель; пустое значение превращается в Optional.empty. */
    public Optional<String> configuredModel() {
        return (model == null || model.isBlank()) ? Optional.empty() : Optional.of(model);
    }

    /**
     * Проверка, что ключ настроен. Вызывается перед первым обращением к API, чтобы
     * пользователь получил понятное сообщение, а не 401 от сервера.
     */
    public void requireApiKey() {
        if (!hasApiKey()) {
            throw new ValidationException("apiKey",
                    "Не задан API-ключ OpenRouter. Установите переменную окружения "
                            + "OPENROUTER_API_KEY (ключ берётся на openrouter.ai/keys).");
        }
    }

    /** Ключ не показывается — иначе он попал бы в журнал. */
    @Override
    public String toString() {
        return "OpenRouterConfig{baseUrl=" + baseUrl
                + ", apiKey=" + (hasApiKey() ? "***" : "<не задан>")
                + ", model=" + (model == null || model.isBlank() ? "<бесплатная по умолчанию>" : model) + "}";
    }
}
