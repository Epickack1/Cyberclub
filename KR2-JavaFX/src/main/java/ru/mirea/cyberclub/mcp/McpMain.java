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
 * <p>Конфигурация БД — те же переменные окружения, что и у JavaFX-приложения
 * ({@code CYBERCLUB_DB_URL}, {@code CYBERCLUB_DB_USER}, {@code CYBERCLUB_DB_PASSWORD}).</p>
 */
public final class McpMain {

    private static final Logger log = LoggerFactory.getLogger(McpMain.class);

    private McpMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        // Диагностика строго в stderr: stdout занят протоколом MCP.
        System.setProperty("org.slf4j.simpleLogger.logFile", "System.err");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");

        AppServices services;
        try {
            DatabaseConfig config = DatabaseConfig.load();
            DatabaseManager db = new DatabaseManager(config);
            db.checkConnection();
            services = new AppServices(db);
        } catch (AppException e) {
            log.error("MCP-сервер не запущен: нет подключения к базе данных CyberClub. "
                    + "Проверьте, что PostgreSQL работает, а переменная CYBERCLUB_DB_PASSWORD задана.", e);
            System.exit(1);
            return;
        }

        if (args.length > 0 && "--list-tools".equals(args[0])) {
            printToolList(services);
            return;
        }

        // Клиент управляет жизненным циклом сервера. Есть два способа, как он даст понять,
        // что всё: закрыть свой stdin (EOF) или прислать сигнал. Оба ведут к снятию латчи,
        // иначе процесс остаётся висеть в списке после отключения клиента.
        CountDownLatch stopped = new CountDownLatch(1);
        InputStream stdin = new EofWatchingInputStream(System.in, stopped);

        CyberclubMcpServer mcp = new CyberclubMcpServer(services, stdin, System.out);
        log.info("MCP-сервер {} v{} запущен (stdio), инструментов: {}",
                CyberclubMcpServer.SERVER_NAME,
                CyberclubMcpServer.SERVER_VERSION,
                mcp.toolNames().size());

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
    private static void printToolList(AppServices services) {
        log.info("Инструменты MCP-сервера {}:", CyberclubMcpServer.SERVER_NAME);
        for (CyberclubTools.Tool tool : CyberclubTools.all(services)) {
            log.info("  {} — {}", tool.name(), tool.description());
        }
    }
}
