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
    public static final String SERVER_VERSION = "2.1.0";

    private static final String INSTRUCTIONS = """
            Сервер даёт доступ к киберспортивному клубу CyberClub: брони стационаров, \
            клиенты, игровые зоны и статистика. Источник данных зависит от запуска: \
            либо прямое подключение к PostgreSQL, либо REST API КР3 — в обоих случаях \
            набор инструментов один и тот же.

            Как работать:
            1. Сначала прочитайте данные (list_clients, list_stations, list_bookings), \
            чтобы получить идентификаторы. Не придумывайте id — берите их из ответов \
            инструментов.
            2. В create_booking клиента можно указать телефоном (clientPhone) или \
            идентификатором (clientId), стационар — названием (stationName) или \
            идентификатором (stationId). Если пользователь назвал человека по имени \
            или телефону, найдите его через list_clients.
            3. Если пользователь не просил выполнить изменение сразу, кратко подтвердите \
            действие перед удалением или сменой статуса.

            Инструменты:
            • Брони — list_bookings, get_booking, search_bookings, create_booking, \
            set_booking_status, find_free_slots, reschedule_booking.
            • Клиенты — list_clients, get_client, create_client, update_client, \
            delete_client.
            • Стационары и статистика — list_stations, get_statistics.

            Жёсткие правила (нарушать нельзя):
            • Не создавайте клиента, пока не получены ВСЕ контактные данные: ник, ФИО, \
            телефон, email и дата рождения. Если чего-то не хватает — спросите \
            пользователя в чате и ничего не создавайте до ответа.
            • Никогда не выдумывайте телефон, email, дату рождения и ник. \
            «Примерно так» здесь недопустимо: эти поля уникальны и проверяются форматом.
            • Перед созданием брони убедитесь, что известны клиент, игровое место, дата, \
            время начала и время окончания. Не хватает хотя бы одного — уточните, \
            а не подставляйте значение по умолчанию.
            • Не удаляйте и не отменяйте чужие записи, чтобы «освободить» время: \
            сначала предложите пользователю вариант.

            Как разрешать конфликты занятости:
            1. Если create_booking вернул «место уже занято», вызовите find_free_slots \
            на тот же интервал (при желании с zone).
            2. Покажите пользователю свободные места и занятые брони.
            3. Если подходит другое место — создайте бронь там или перенесите текущую \
            через reschedule_booking.
            4. Если свободных мест нет — предложите другое время, снова проверив его \
            через find_free_slots.

            Бизнес-правила, которые проверяет приложение (текст ошибки возвращается как есть):
            • Ник клиента: 3–20 символов, только латиница, цифры и подчёркивание.
            • Телефон в формате +7XXXXXXXXXX; ник, телефон и email уникальны.
            • Возраст клиента — не младше 12 лет.
            • Бронь не может пересекаться с другой бронью того же стационара, начало \
            должно быть раньше окончания, а стационар — активен.
            • Статусы брони: CREATED → CONFIRMED → ACTIVE → COMPLETED, отмена — CANCELLED.
            • Удалить клиента можно только без бронирований: история броней — основа \
            статистики и отчётности.
            • update_client меняет только переданные поля, остальные сохраняются как были.
            • reschedule_booking меняет время и место, но не клиента.

            Отвечайте по-русски, кратко и по делу. Суммы — в рублях, время — по Москве.
            Когда спрашивают, что вы умеете, перечисляйте именно эти инструменты и не \
            обещайте того, чего в списке нет (например, приёма оплаты).
            """;

    private final McpSyncServer server;

    /** Потоки по умолчанию: stdin/stdout процесса — так работает любой MCP-клиент. */
    public CyberclubMcpServer(ClubApi api) {
        this(api, System.in, System.out, INSTRUCTIONS);
    }

    /** Режим по умолчанию — прямое подключение к базе КР2. */
    public CyberclubMcpServer(AppServices services) {
        this(new JdbcClubApi(services), System.in, System.out, INSTRUCTIONS);
    }

    /**
     * Явные потоки ввода-вывода. Нужны {@link McpMain}, чтобы подставить stdin,
     * который сообщает о своем закрытии: без этого процесс не завершается после
     * отключения клиента и остаётся висеть в списке процессов.
     */
    public CyberclubMcpServer(ClubApi api, java.io.InputStream in, java.io.OutputStream out) {
        this(api, in, out, INSTRUCTIONS);
    }

    /** Режим по умолчанию — прямое подключение к базе КР2. */
    public CyberclubMcpServer(AppServices services, java.io.InputStream in, java.io.OutputStream out) {
        this(new JdbcClubApi(services), in, out, INSTRUCTIONS);
    }

    /** Позволяет подменить инструкции в тестах. */
    static CyberclubMcpServer withInstructions(ClubApi api, String instructions) {
        return new CyberclubMcpServer(api, System.in, System.out, instructions);
    }

    private CyberclubMcpServer(ClubApi api, java.io.InputStream in, java.io.OutputStream out,
                               String instructions) {
        this.server = start(api, instructions, in, out);
    }

    /** Список инструментов, которые увидит клиент. Используется в тестах. */
    public java.util.List<String> toolNames() {
        return server.listTools().stream().map(Tool::name).toList();
    }

    private static McpSyncServer start(ClubApi api, String instructions,
                                       java.io.InputStream in, java.io.OutputStream out) {
        McpJsonMapper mapper = McpJsonDefaults.getMapper();
        McpServerTransportProvider transport = new StdioServerTransportProvider(mapper, in, out);

        McpServer.SyncSpecification<McpServer.SingleSessionSyncSpecification> spec =
                McpServer.sync(transport)
                        .serverInfo(SERVER_NAME, SERVER_VERSION)
                        .instructions(instructions)
                        .capabilities(ServerCapabilities.builder().tools(true).build());

        for (CyberclubTools.Tool tool : CyberclubTools.all(api)) {
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
