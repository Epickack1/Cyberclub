package ru.mirea.cyberclub.mcp;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

import ru.mirea.cyberclub.AppServices;

/**
 * MCP-сервер приложения: публикует инструменты {@link CyberclubTools} внешнему ИИ.
 *
 * <p>Транспорт — STDIO: внешний MCP-клиент (Claude Desktop, opencode, Copilot) запускает
 * наше приложение отдельным процессом и разговаривает с ним по stdin/stdout, посылая
 * JSON-RPC. Поэтому это отдельный запуск без JavaFX — см. {@link McpMain}.</p>
 *
 * <p>Схема взаимодействия:</p>
 * <pre>
 * внешний ИИ ──stdin (JSON-RPC)──▶ McpMain ──▶ CyberclubMcpServer ──▶ CyberclubTools
 *     ▲                                                              │
 *     └──────────────stdout (JSON-RPC) ◀─────────────────────────────┘
 *                                       └─▶ AppServices ─▶ PostgreSQL
 * </pre>
 *
 * <p>Каждый вызов инструмента обёрнут так, чтобы исключение бизнес-правило превратилось
 * в результат с {@code isError=true} и текстом причины: иначе SDK превратил бы его
 * в непонятную внутреннюю ошибку протокола, и ИИ не смог бы объяснить пользователю,
 * что именно пошло не так (например, «станция уже занята»).</p>
 */
public final class CyberclubMcpServer implements AutoCloseable {

    /** Идентификатор сервера, который клиент видит в списке подключённых MCP-серверов. */
    public static final String SERVER_NAME = "cyberclub";
    public static final String SERVER_VERSION = "2.0.0";

    private static final String INSTRUCTIONS = """
            Сервер даёт доступ к киберспортивному клубу CyberClub: брони стационаров, \
            клиенты, игровые зоны и статистика. Данные читаются и изменяются в PostgreSQL.
            Для изменения данных вызывайте инструменты create_booking, set_booking_status \
            и create_client — бизнес-правила (доступность, длительность, возраст) проверяются \
            на стороне приложения, а текст ошибки возвращается вам как есть.
            """;

    private final McpSyncServer server;

    /** Потоки по умолчанию: stdin/stdout процесса — так работает любой MCP-клиент. */
    public CyberclubMcpServer(AppServices services) {
        this(services, System.in, System.out, INSTRUCTIONS);
    }

    /**
     * Явные потоки ввода-вывода. Нужны {@link McpMain}, чтобы подставить stdin,
     * который сообщает о своем закрытии: без этого процесс не завершается после
     * отключения клиента и остаётся висеть в списке процессов.
     */
    public CyberclubMcpServer(AppServices services, java.io.InputStream in, java.io.OutputStream out) {
        this(services, in, out, INSTRUCTIONS);
    }

    /** Позволяет подменить инструкции в тестах. */
    static CyberclubMcpServer withInstructions(AppServices services, String instructions) {
        return new CyberclubMcpServer(services, System.in, System.out, instructions);
    }

    private CyberclubMcpServer(AppServices services, java.io.InputStream in, java.io.OutputStream out,
                               String instructions) {
        this.server = start(services, instructions, in, out);
    }

    /** Список инструментов, которые увидит клиент. Используется в тестах. */
    public java.util.List<String> toolNames() {
        return server.listTools().stream().map(Tool::name).toList();
    }

    private static McpSyncServer start(AppServices services, String instructions,
                                       java.io.InputStream in, java.io.OutputStream out) {
        McpJsonMapper mapper = McpJsonDefaults.getMapper();
        McpServerTransportProvider transport = new StdioServerTransportProvider(mapper, in, out);

        McpServer.SyncSpecification<McpServer.SingleSessionSyncSpecification> spec =
                McpServer.sync(transport)
                        .serverInfo(SERVER_NAME, SERVER_VERSION)
                        .instructions(instructions)
                        .capabilities(ServerCapabilities.builder().tools(true).build());

        for (CyberclubTools.Tool tool : CyberclubTools.all(services)) {
            Tool mcpTool = Tool.builder(tool.name(), tool.inputSchema())
                    .description(tool.description())
                    .build();
            spec = spec.toolCall(mcpTool, (exchange, request) -> invoke(tool, request));
        }
        return spec.build();
    }

    /** Обёртка вызова: превращает результат или исключение в CallToolResult. */
    static CallToolResult invoke(CyberclubTools.Tool tool, CallToolRequest request) {
        try {
            String text = tool.run(request == null ? null : request.arguments());
            return CallToolResult.builder().addTextContent(text).isError(false).build();
        } catch (Exception e) {
            String message = e.getMessage();
            if (message == null || message.isBlank()) {
                message = e.getClass().getSimpleName();
            }
            return CallToolResult.builder()
                    .addTextContent("Ошибка при выполнении \"" + tool.name() + "\": " + message)
                    .isError(true)
                    .build();
        }
    }

    /** Доступ к самому серверу — нужен тестам, чтобы проверить зарегистрированные инструменты. */
    public McpSyncServer unwrap() {
        return server;
    }

    @Override
    public void close() {
        server.closeGracefully();
    }
}
