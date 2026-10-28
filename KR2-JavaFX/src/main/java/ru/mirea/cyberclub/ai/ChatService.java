package ru.mirea.cyberclub.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Диалог с моделью: хранит историю, добавляет системный промпт и отправляет запросы
 * через {@link OpenRouterClient}. Один экземпляр — один диалог.
 *
 * <p>История синхронизируется на самом объекте, поэтому вызывать {@link #ask(String)}
 * можно из фонового потока JavaFX не боясь гонки с {@link #clear()}.</p>
 */
public class ChatService {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Ты — встроенный ассистент десктопного приложения "CyberClub" киберспортивного клуба \
            (Курсовая работа КР2, JavaFX + PostgreSQL).
            Отвечай по-русски, коротко и по делу: два-три предложения, без вступлений вроде "Конечно".

            Что ты умеешь — только объяснять и подсказывать по данным клуба:
            • брони: список, статусы (создано, подтверждено, активно, завершено, отменено), \
            стоимость и занятость;
            • клиенты: карточки, контакты, возрастные ограничения;
            • игровые места: зоны, тарифы в час, исправность;
            • статистика: выручка, загрузка, популярные места и клиенты;
            • интерфейс: в какой вкладке и какой кнопкой выполняется действие.

            Чего ты НЕ умеешь: у тебя нет инструментов, ты не создаёшь, не изменяешь и не \
            отменяешь записи, не принимаешь оплату, не отправляешь письма и не бронируешь \
            места. Никогда не обещай таких действий и не говори, что уже что-то сделал. \
            Если просят действие — объясни, в какой вкладке его выполнить и какие поля нужны.
            На вопрос "что ты умеешь" перечисляй ровно перечисленное выше, без оплат и \
            любых выдуманных возможностей.

            Жёсткие правила по данным:
            • Чтобы добавить клиента, нужны ВСЕ поля: ник (3–20 символов, только латиница, \
            цифры и подчёркивание), ФИО, телефон в формате +7XXXXXXXXXX, email и дата \
            рождения. Если пользователь просит добавить клиента и чего-то не хватает — \
            перечисли недостающие поля и попроси их, ничего не придумывая.
            • Никогда не выдумывай телефон, email, дату рождения, ник, номер брони или сумму.
            • Чтобы создать бронь, нужны клиент, игровое место, дата, время начала и время \
            окончания. Не хватает хотя бы одного — уточни, а не подставляй значение наугад.
            • Стоимость брони — тариф места в час, умноженный на длительность; иных сборов нет.

            Конфликты занятости: если время занято, предложи другое игровое место или другое \
            время, объяснив, как проверить занятость (вкладка "Бронирования" с фильтром по \
            дате и месту). Не советуй отменять чужую бронь, чтобы освободить место.

            Никогда не выдумывай конкретные брони, имена или суммы: если данных нет, прямо скажи об этом.
            """;

    private final OpenRouterClient client;
    private final List<ChatMessage> history = new ArrayList<>();
    private String systemPrompt = DEFAULT_SYSTEM_PROMPT;
    private Supplier<String> clubContextSupplier;

    public ChatService(OpenRouterClient client) {
        this.client = client;
    }

    /** Задаёт системный промпт полностью. */
    public void setSystemPrompt(String systemPrompt) {
        synchronized (this) {
            this.systemPrompt = systemPrompt;
        }
    }

    /**
     * Подключает поставщика снимка данных клуба: его текст подставляется в системный промпт
     * перед каждым запросом, иначе модель отвечала бы "у меня нет данных" на вопросы
     * о текущих бронях. Поставщик вызывается на потоке запроса, поэтому обращение к БД
     * не блокирует поток UI.
     */
    public void setClubContextSupplier(Supplier<String> clubContextSupplier) {
        synchronized (this) {
            this.clubContextSupplier = clubContextSupplier;
        }
    }

    /**
     * Отправляет реплику пользователя и возвращает ответ модели.
     * История пополняется обеими репликами, так что диалог продолжается.
     *
     * @param userText текст, введённый пользователем
     * @return ответ ассистента
     */
    public String ask(String userText) {
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("Пустое сообщение — отправлять нечего.");
        }
        String trimmed = userText.trim();
        ChatMessage mine = ChatMessage.user(trimmed);
        List<ChatMessage> outgoing;
        synchronized (this) {
            history.add(mine);
            outgoing = snapshot();
        }

        String reply;
        try {
            reply = client.chat(outgoing);
        } catch (RuntimeException e) {
            // Неудачный запрос не должен оставлять «висячую» реплику пользователя:
            // иначе следующий повтор отправил бы её второй раз и диалог поехал бы.
            synchronized (this) {
                int last = history.size() - 1;
                if (last >= 0 && history.get(last).equals(mine)) {
                    history.remove(last);
                }
            }
            throw e;
        }

        synchronized (this) {
            history.add(ChatMessage.assistant(reply));
        }
        return reply;
    }

    /** Полный список реплик для отрисовки в интерфейсе. */
    public List<ChatMessage> history() {
        synchronized (this) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    /** Очищает диалог, но оставляет системный промпт. */
    public void clear() {
        synchronized (this) {
            history.clear();
        }
    }

    /** Текущая модель — для отображения в интерфейсе. */
    public String model() {
        return client.resolveModel();
    }

    /** Проверка настроек до старта диалога: бросает понятное исключение вместо ошибки HTTP. */
    public void checkConfigured() {
        client.config().requireApiKey();
    }

    /** Системный промпт со снимком данных клуба, если он подключён. */
    private String systemPromptWithContext() {
        Supplier<String> supplier;
        synchronized (this) {
            supplier = clubContextSupplier;
        }
        if (supplier == null) {
            return systemPrompt;
        }
        String context;
        try {
            context = supplier.get();
        } catch (RuntimeException e) {
            return systemPrompt;
        }
        if (context == null || context.isBlank()) {
            return systemPrompt;
        }
        return systemPrompt + "\nСнимок данных клуба на текущий момент:\n" + context;
    }

    /** Снимок истории: системный промпт первым + все реплики. */
    private synchronized List<ChatMessage> snapshot() {
        List<ChatMessage> out = new ArrayList<>(history.size() + 1);
        out.add(ChatMessage.system(systemPromptWithContext()));
        out.addAll(history);
        return out;
    }
}
