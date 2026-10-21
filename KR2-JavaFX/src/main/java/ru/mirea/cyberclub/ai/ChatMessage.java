package ru.mirea.cyberclub.ai;

/**
 * Одно сообщение диалога в формате OpenAI Chat Completions — того формата,
 * который поддерживает OpenRouter и все основные провайдеры.
 *
 * <p>Три роли задаются статическими фабриками, чтобы роля нельзя было перепутать со свободным
 * текстом (например, вложить {@code "user"} в поле content). Record сделан неизменяемым:
 * историю можно безопасно передавать в сервис и в тесты.</p>
 */
public record ChatMessage(String role, String content) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public ChatMessage {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Роль сообщения не может быть пустой");
        }
        if (content == null) {
            throw new IllegalArgumentException("Текст сообщения не может быть null");
        }
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ROLE_SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content);
    }
}
