package ru.mirea.cyberclub.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * Обёртка вызова инструмента: главное её свойство — ни одно исключение не должно
 * дойти до клиента как внутренняя ошибка протокола. ИИ должен получить текст причины
 * и признак isError, иначе он не сможет объяснить пользователю, что пошло не так.
 */
class CyberclubMcpServerTest {

    private static CyberclubTools.Tool tool(String name, java.util.function.Function<Map<String, Object>, String> body) {
        return new CyberclubTools.Tool(name, "тестовый инструмент", Map.of(), body);
    }

    private static CallToolResult invoke(CyberclubTools.Tool tool, Map<String, Object> args) {
        return CyberclubMcpServer.invoke(tool, new CallToolRequest(tool.name(), args));
    }

    private static String text(CallToolResult result) {
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
        return ((TextContent) result.content().get(0)).text();
    }

    @Test
    @DisplayName("Успешный вызов: текст результата, isError = false")
    void successReturnsTextAndNoError() {
        CallToolResult result = invoke(tool("echo", args -> "привет"), Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(text(result)).isEqualTo("привет");
    }

    @Test
    @DisplayName("Аргументы доходят до тела инструмента")
    void argumentsReachBody() {
        CallToolResult result = invoke(
                tool("echo", args -> String.valueOf(args.get("q"))),
                Map.of("q", "значение"));

        assertThat(text(result)).isEqualTo("значение");
    }

    @Test
    @DisplayName("Исключение бизнес-правила превращается в isError с текстом причины")
    void businessRuleErrorBecomesIsErrorWithMessage() {
        CallToolResult result = invoke(
                tool("busy", args -> {
                    throw new ru.mirea.cyberclub.exception.BusinessRuleException("Место PC-01 уже занято");
                }),
                Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result))
                .contains("busy")
                .contains("Место PC-01 уже занято")
                .doesNotContain("Exception");
    }

    @Test
    @DisplayName("Без сообщения у исключения в ответ идёт имя класса, а не пустота")
    void exceptionWithoutMessageStillExplains() {
        CallToolResult result = invoke(
                tool("boom", args -> {
                    throw new IllegalStateException();
                }),
                Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("IllegalStateException").isNotBlank();
    }

    @Test
    @DisplayName("Пустая строка-сообщение заменяется на имя класса")
    void blankMessageFallsBackToClassName() {
        CallToolResult result = invoke(
                tool("boom", args -> {
                    throw new RuntimeException("   ");
                }),
                Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("RuntimeException");
    }

    @Test
    @DisplayName("null-запрос и null-аргументы не роняют обёртку")
    void nullRequestAndArgumentsAreSafe() {
        CallToolResult fromNullRequest = CyberclubMcpServer.invoke(
                tool("safe", args -> "получил " + args), null);
        assertThat(fromNullRequest.isError()).isFalse();
        assertThat(text(fromNullRequest)).contains("получил {}");

        CallToolResult fromNullArgs = invoke(tool("safe", args -> "пусто"), null);
        assertThat(fromNullArgs.isError()).isFalse();
        assertThat(text(fromNullArgs)).isEqualTo("пусто");
    }

    @Test
    @DisplayName("Сервер описывает себя стабильным именем и версией")
    void serverIdentityIsStable() {
        assertThat(CyberclubMcpServer.SERVER_NAME).isEqualTo("cyberclub");
        assertThat(CyberclubMcpServer.SERVER_VERSION).matches("\\d+\\.\\d+\\.\\d+");
    }
}
