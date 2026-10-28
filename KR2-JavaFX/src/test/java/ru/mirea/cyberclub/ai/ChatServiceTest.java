package ru.mirea.cyberclub.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.ValidationException;

/**
 * Диалог с моделью: сборка системного промпта, ведение истории и поведение при ошибке.
 * Сеть не используется — клиент подменяется заглушкой, которая записывает, что ушло в запрос.
 */
class ChatServiceTest {

    /** Перехватывает исходящие сообщения вместо реального HTTP-вызова. */
    static class RecordingClient extends OpenRouterClient {
        private final List<List<ChatMessage>> requests = new ArrayList<>();
        private RuntimeException failure;
        private int callCount;

        RecordingClient() {
            super(new OpenRouterConfig("http://example.test", "test-key", "test-model"));
        }

        @Override
        public String chat(List<ChatMessage> messages) {
            requests.add(List.copyOf(messages));
            callCount++;
            if (failure != null) {
                throw failure;
            }
            return "ответ #" + callCount;
        }
    }

    private RecordingClient client;
    private ChatService service;

    @BeforeEach
    void setUp() {
        client = new RecordingClient();
        service = new ChatService(client);
    }

    @Test
    @DisplayName("Первое сообщение в запросе — системный промпт с названием приложения")
    void systemPromptComesFirst() {
        service.ask("Привет");

        List<ChatMessage> sent = client.requests.get(0);
        assertThat(sent).isNotEmpty();
        assertThat(sent.get(0).role()).isEqualTo(ChatMessage.ROLE_SYSTEM);
        assertThat(sent.get(0).content()).contains("CyberClub");
        assertThat(sent.get(1).role()).isEqualTo(ChatMessage.ROLE_USER);
        assertThat(sent.get(1).content()).isEqualTo("Привет");
    }

    @Test
    @DisplayName("История растёт: реплика пользователя, потом ответ ассистента")
    void historyGrowsWithBothReplies() {
        service.ask("Сколько броней?");
        service.ask("А клиентов?");

        List<ChatMessage> history = service.history();
        assertThat(history).hasSize(4);
        assertThat(history.get(0)).isEqualTo(ChatMessage.user("Сколько броней?"));
        assertThat(history.get(1).role()).isEqualTo(ChatMessage.ROLE_ASSISTANT);
        assertThat(history.get(2)).isEqualTo(ChatMessage.user("А клиентов?"));
        assertThat(history.get(3).content()).isEqualTo("ответ #2");
    }

    @Test
    @DisplayName("Каждый запрос несёт всю историю — модель видит контекст диалога")
    void eachRequestCarriesFullHistory() {
        service.ask("первый");
        service.ask("второй");

        // Второй запрос собирается ДО получения второго ответа:
        // системный промпт + первый вопрос + первый ответ + второй вопрос.
        List<ChatMessage> secondRequest = client.requests.get(1);
        assertThat(secondRequest).hasSize(4);
        assertThat(secondRequest.get(1).content()).isEqualTo("первый");
        assertThat(secondRequest.get(2).role()).isEqualTo(ChatMessage.ROLE_ASSISTANT);
        assertThat(secondRequest.get(3).content()).isEqualTo("второй");
    }

    @Test
    @DisplayName("Снимок данных клуба подставляется в системный промпт")
    void clubContextInjectedIntoSystemPrompt() {
        service.setClubContextSupplier(() -> "Броней сегодня: 5");

        service.ask("Привет");

        assertThat(client.requests.get(0).get(0).content())
                .contains("Снимок данных клуба")
                .contains("Броней сегодня: 5");
    }

    @Test
    @DisplayName("Ошибка поставщика снимка не роняет диалог")
    void brokenClubContextDoesNotBreakChat() {
        service.setClubContextSupplier(() -> {
            throw new IllegalStateException("БД недоступна");
        });

        service.ask("Привет");

        assertThat(client.requests.get(0).get(0).content())
                .doesNotContain("Снимок данных клуба");
    }

    @Test
    @DisplayName("Пустое сообщение не уходит в модель")
    void emptyMessageRejected() {
        assertThatThrownBy(() -> service.ask("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(client.requests).isEmpty();
    }

    @Test
    @DisplayName("При ошибке API реплика пользователя откатывается — повтор не задвоит диалог")
    void failedRequestRollsBackUserMessage() {
        client.failure = new AppException("Сеть недоступна");

        assertThatThrownBy(() -> service.ask("Привет")).isInstanceOf(AppException.class);

        assertThat(service.history()).isEmpty();

        client.failure = null;
        service.ask("Привет");
        List<ChatMessage> sent = client.requests.get(1);
        assertThat(sent.stream().filter(m -> m.role().equals(ChatMessage.ROLE_USER))).hasSize(1);
    }

    @Test
    @DisplayName("clear очищает историю, но оставляет системный промпт")
    void clearKeepsSystemPrompt() {
        service.ask("Привет");
        assertThat(service.history()).hasSize(2);

        service.clear();

        assertThat(service.history()).isEmpty();
        service.ask("Ещё раз");
        assertThat(client.requests.get(1).get(0).role()).isEqualTo(ChatMessage.ROLE_SYSTEM);
    }

    @Test
    @DisplayName("История защищена от изменения извне")
    void historyIsUnmodifiable() {
        service.ask("Привет");

        assertThatThrownBy(() -> service.history().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Без API-ключа checkConfigured сообщает, что нужно задать ключ")
    void checkConfiguredRequiresApiKey() {
        ChatService noKey = new ChatService(
                new RecordingClient() {
                    @Override
                    public OpenRouterConfig config() {
                        return new OpenRouterConfig("http://example.test", "", null);
                    }
                });

        assertThatThrownBy(noKey::checkConfigured).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Модель берётся из настроек клиента")
    void modelComesFromConfig() {
        assertThat(service.model()).isEqualTo("test-model");
    }
}
