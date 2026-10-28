package ru.mirea.cyberclub.mcp;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.mirea.cyberclub.AppServices;
import ru.mirea.cyberclub.db.DatabaseConfig;
import ru.mirea.cyberclub.db.DatabaseManager;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.mcp.rest.RestClubApi;

/**
 * Точка входа MCP-сервера: отдельный процесс, который внешний ИИ запускает сам
 * и разговаривает с ним по stdin/stdout (транспорт STDIO).
 *
 * <p>Почему отдельный процесс, а не тот же, что JavaFX: клиент MCP управляет жизненным
 * циклом сервера сам — поднимает по запросу и завершает, когда отключается. Держать
 * в нём окно и поток JavaFX означало бы, что после закрытия окна сервер умирает.</p>
 *
 * <p><b>Важно:</b> stdout отдан протоколу JSON-RPC, поэтому в него нельзя печатать
 * ничего — любая лишняя строка ломает разбор сообщений клиентом. Вся диагностика идёт
 * в stderr (slf4j-simple пишет именно туда).</p>
 *
 * <p>Запуск:</p>
 * <pre>
 *   # как подключаемый клиент MCP (stdio):
 *   java -cp target/classes:&lt;зависимости&gt; ru.mirea.cyberclub.mcp.McpMain
 *
 *   # локальная проверка без клиента — перечисляет инструменты в stderr:
 *   java ... ru.mirea.cyberclub.mcp.McpMain --list-tools
 * </pre>
 *
 * <p>Источник данных выбирается по переменным окружения при запуске:</p>
 * <ul>
 *   <li>{@code CYBERCLUB_API_URL} (например {@code http://localhost:8080}) — MCP ходит в
 *       REST API КР3; базу при этом не открывает, все правила решает бэкенд;</li>
 *   <li>иначе — прямое подключение к PostgreSQL по {@code CYBERCLUB_DB_URL},
 *       {@code CYBERCLUB_DB_USER}, {@code CYBERCLUB_DB_PASSWORD}.</li>
 * </ul>
 */
public final class McpMain {

    private static final Logger log = LoggerFactory.getLogger(McpMain.class);

    private McpMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        // Диагностика строго в stderr: stdout занят протоколом MCP.
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");

        ClubApi api = connect();
        if (args.length > 0 && "--list-tools".equals(args[0])) {
            printToolList(api);
            return;
        }

        // Клиент управляет жизненным циклом сервера. Есть два способа, как он даст понять,
        // что всё: закрыть свой stdin (EOF) или прислать сигнал. Оба ведут к снятию латчи,
        // иначе процесс остаётся висеть в списке после отключения клиента.
        CountDownLatch stopped = new CountDownLatch(1);
        InputStream stdin = new EofWatchingInputStream(System.in, stopped);

        CyberclubMcpServer mcp = new CyberclubMcpServer(api, stdin, System.out);
        log.info("MCP-сервер {} v{} запущен (stdio), инструментов: {}, источник: {}",
                CyberclubMcpServer.SERVER_NAME,
                CyberclubMcpServer.SERVER_VERSION,
                mcp.toolNames().size(),
                api.describe());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Завершение MCP-сервера по сигналу клиента");
            mcp.close();
            stopped.countDown();
        }, "mcp-shutdown"));

        stopped.await();

        // Сюда попадаем по EOF stdin — клиент отключился. Аккуратно закрываем транспорт.
        log.info("Клиент закрыл stdin — завершение MCP-сервера");
        mcp.close();
    }

    /**
     * Выбор источника данных при запуске.
     *
     * <p>Если задана {@code CYBERCLUB_API_URL} — MCP работает через REST API КР3 и базу сам
     * не трогает: бэкенд отвечает за доступность мест, длительность и возраст. Иначе —
     * прямое подключение к PostgreSQL, как было изначально.</p>
     */
    private static ClubApi connect() {
        String apiUrl = RestClubApi.configuredBaseUrl();
        if (!apiUrl.isEmpty()) {
            log.info("MCP подключается к REST API: {}", apiUrl);
            return new RestClubApi(apiUrl);
        }
        try {
            DatabaseConfig config = DatabaseConfig.load();
            DatabaseManager db = new DatabaseManager(config);
            db.checkConnection();
            return new JdbcClubApi(new AppServices(db));
        } catch (AppException e) {
            log.error("MCP-сервер не запущен: нет подключения к данным CyberClub. "
                    + "Задайте CYBERCLUB_API_URL для REST API (KR3-backend) либо проверьте, "
                    + "что PostgreSQL работает и задана CYBERCLUB_DB_PASSWORD.", e);
            System.exit(1);
            throw e;
        }
    }

    /**
     * Обёртка над stdin, которая сообщает {@link CountDownLatch} о закрытии входного потока.
     *
     * <p>Без неё {@link #main} остался бы ждать сигнала в вечном {@code await()}: SDK лишь
     * завершает чтение, но останавливать сам процесс не умеет — это ответственность входной
     * точки. Чтение полностью делегируется настоящему stdin, то есть протокол не затрагивается.</p>
     */
    private static final class EofWatchingInputStream extends FilterInputStream {

        private final CountDownLatch onEof;
        private final java.util.concurrent.atomic.AtomicBoolean fired =
                new java.util.concurrent.atomic.AtomicBoolean();

        private EofWatchingInputStream(InputStream in, CountDownLatch onEof) {
            super(in);
            this.onEof = onEof;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b == -1) {
                signal();
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read == -1) {
                signal();
            }
            return read;
        }

        private void signal() {
            if (fired.compareAndSet(false, true)) {
                onEof.countDown();
            }
        }
    }

    /**
     * Вспомогательный режим для отладки: показывает опубликованные инструменты, не вступая
     * в протокольный диалог. Позволяет убедиться, что сервер собрался и видит базу,
     * ещё до подключения реального MCP-клиента.
     */
    private static void printToolList(ClubApi api) {
        log.info("Инструменты MCP-сервера {} (источник: {}):",
                CyberclubMcpServer.SERVER_NAME, api.describe());
        for (CyberclubTools.Tool tool : CyberclubTools.all(api)) {
            log.info("  {} — {}", tool.name(), tool.description());
        }
    }
}
