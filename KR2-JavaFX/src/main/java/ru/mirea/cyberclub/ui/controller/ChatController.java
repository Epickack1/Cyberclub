package ru.mirea.cyberclub.ui.controller;

import java.util.List;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.ai.ChatService;
import ru.mirea.cyberclub.ai.OpenRouterClient;
import ru.mirea.cyberclub.ai.OpenRouterConfig;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.ui.util.Dialogs;
import ru.mirea.cyberclub.ui.util.FxTasks;

/**
 * Вкладка «ИИ-ассистент»: диалог с моделью через OpenRouter.
 *
 * <p>Сетевой вызов идёт в фоновом потоке через {@link FxTasks} — окно не должно
 * замерзать на несколько секунд ожидания ответа модели. Изменять элементы
 * интерфейса разрешено только из JavaFX Application Thread, поэтому результат
 * и ошибка возвращаются в колбэках этого помощника.</p>
 *
 * <p>Важная деталь согласованности: {@link ChatService} при ошибке откатывает
 * реплику пользователя из своей истории, иначе повтор отправил бы её второй раз.
 * Контроллер делает то же самое с уже показанным «пузырём» — иначе диалог на экране
 * расходился бы с тем, что реально ушло модели.</p>
 */
public class ChatController implements RefreshableTab {

    private static final int BUBBLE_MAX_WIDTH = 620;

    @FXML
    private ScrollPane scroll;
    @FXML
    private VBox messagesBox;
    @FXML
    private TextArea input;
    @FXML
    private Button sendButton;
    @FXML
    private Label modelLabel;
    @FXML
    private Label hintLabel;
    @FXML
    private Label errorLabel;

    private final AppContext context;
    private ChatService chat;
    private OpenRouterConfig config;
    private boolean busy;

    public ChatController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        config = OpenRouterConfig.load();
        chat = new ChatService(new OpenRouterClient(config));
        chat.setClubContextSupplier(this::clubSnapshot);

        // Фильтр, а не обработчик: сработает раньше, чем TextArea успеет вставить перевод строки.
        input.addEventFilter(KeyEvent.KEY_PRESSED, this::onInputKeyPressed);

        if (config.hasApiKey()) {
            modelLabel.setText(config.configuredModel().orElse("бесплатная модель"));
            hintLabel.setText("Ассистент видит снимок текущих данных клуба и отвечает по-русски.");
        } else {
            modelLabel.setText("ключ не задан");
            hintLabel.setText("Чат не подключён: задайте переменную окружения OPENROUTER_API_KEY "
                    + "(ключ берётся бесплатно на openrouter.ai/keys) и перезапустите приложение.");
            sendButton.setDisable(true);
        }

        appendBubble("bubble-system",
                "Я — ассистент CyberClub. Могу пояснить, как устроены брони, клиенты и статистика, "
                        + "и подсказать, что посмотреть во вкладках программы.\n"
                        + "Список броней, клиентов и мест я вижу живьём из базы данных.");
    }

    // ------------------------------------------------------------------- отправка

    @FXML
    private void onSend() {
        if (busy) {
            return;
        }
        String text = input.getText();
        if (text == null || text.isBlank()) {
            return;
        }
        if (!config.hasApiKey()) {
            showError("API-ключ OpenRouter не задан — сообщение не отправлено.");
            return;
        }
        clearError();

        // Пузырь показываем сразу: без обратной связи приложение выглядит зависшим.
        Node pendingRow = appendBubble("bubble-user", text.trim());
        input.clear();
        setBusy(true);
        hintLabel.setText("Модель отвечает…");

        FxTasks.run(() -> chat.ask(text),
                reply -> {
                    appendBubble("bubble-assistant", reply);
                    modelLabel.setText(chat.model());
                    hintLabel.setText("Готово. Задайте следующий вопрос.");
                    setBusy(false);
                    input.requestFocus();
                },
                error -> {
                    // Откатываем и на экране, и в истории ChatService — сообщение не ушло.
                    messagesBox.getChildren().remove(pendingRow);
                    appendBubble("bubble-system", "Не удалось отправить сообщение: "
                            + Dialogs.friendly(error));
                    input.setText(text.trim());
                    hintLabel.setText("Проверьте подключение к интернету и повторите отправку.");
                    setBusy(false);
                    input.requestFocus();
                });
    }

    @FXML
    private void onClear() {
        chat.clear();
        messagesBox.getChildren().clear();
        clearError();
        appendBubble("bubble-system", "Диалог очищен. Начните заново — я всё ещё вижу данные клуба.");
    }

    private void onInputKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            event.consume();
            onSend();
        }
    }

    private void setBusy(boolean value) {
        busy = value;
        sendButton.setDisable(value || !config.hasApiKey());
    }

    // ------------------------------------------------------------------- отрисовка

    /**
     * Добавляет реплику в конец ленты и прокручивает её вниз.
     *
     * <p>Текст реплики — не {@code Label}, а нередактируемый {@code TextArea}: только он
     * в JavaFX даёт выделение мышью, {@code Ctrl+C} и контекстное меню «Копировать».
     * Надпись скопировать нельзя, а переписку с ИИ обычно хочется перенести в отчёт.</p>
     *
     * @return узел строки — его можно убрать, если сообщение не дойдёт до модели
     */
    private Node appendBubble(String styleClass, String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setFocusTraversable(false);
        area.getStyleClass().addAll("bubble-text", "bubble-textarea");
        area.setPrefWidth(BUBBLE_MAX_WIDTH);
        area.setMaxWidth(BUBBLE_MAX_WIDTH);

        VBox bubble = new VBox(area);
        bubble.getStyleClass().add(styleClass);
        bubble.setMaxWidth(BUBBLE_MAX_WIDTH + 24);

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-row");
        row.setAlignment(styleClass.equals("bubble-user") ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        messagesBox.getChildren().add(row);
        fitBubbleHeight(area);
        // Шрифт из CSS применяется уже после добавления в сцену — пересчитываем высоту ещё раз.
        Platform.runLater(() -> fitBubbleHeight(area));
        scrollToBottom();
        return row;
    }

    /**
     * Высота под текст: {@code TextArea} сам под содержимое не растёт, а полоса прокрутки
     * внутри пузыря выглядела бы как ошибка вёрстки. Ширину переноса измеряем тем же
     * шрифтом, что и у поля, поэтому число строк совпадает с настоящим.
     */
    private void fitBubbleHeight(TextArea area) {
        double width = area.getWidth() > 0 ? area.getWidth() : BUBBLE_MAX_WIDTH;
        Text probe = new Text(area.getText());
        probe.setFont(area.getFont());
        probe.setWrappingWidth(Math.max(40, width));
        double height = Math.ceil(probe.getLayoutBounds().getHeight()) + 6;
        if (Math.abs(area.getPrefHeight() - height) > 0.5) {
            area.setPrefHeight(height);
            area.setMinHeight(height);
            area.setMaxHeight(height);
        }
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scroll.setVvalue(1));
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void clearError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // ------------------------------------------------------------------- контекст

    @Override
    public void refresh() {
        // Данные клуба модель получает свежими при каждом запросе (см. clubSnapshot),
        // здесь достаточно убрать сообщение об ошибке, оставшееся от прошлого раза.
        clearError();
    }

    /**
     * Снимок данных клуба для системного промпта. Без него модель отвечала бы
     * «у меня нет доступа к вашим данным» на любые вопросы о бронях.
     *
     * <p>Вызывается в фоновом потоке внутри {@link ChatService#ask}, поэтому обращение
     * к базе не блокирует интерфейс.</p>
     */
    private String clubSnapshot() {
        try {
            StringBuilder out = new StringBuilder();
            var stats = context.statistics().collect();
            for (var ind : context.statistics().toIndicators(stats)) {
                out.append("- ").append(ind.name()).append(": ").append(ind.value()).append('\n');
            }

            // Тарифы нужны отдельно: без них модель честно отвечает «нет данных»
            // на обычный вопрос «сколько стоит VIP в час».
            out.append("- Игровые места и тарифы:\n");
            for (var station : context.stations().getAll()) {
                out.append("  ").append(station.getName())
                        .append(" [").append(station.getType().getTitle()).append("] ")
                        .append(station.getHourlyRate()).append(" руб./ч")
                        .append(station.isActive() ? "" : " — на обслуживании")
                        .append('\n');
            }

            List<Booking> upcoming = context.bookings().getUpcoming().stream().limit(5).toList();
            if (!upcoming.isEmpty()) {
                out.append("- Ближайшие брони:\n");
                for (Booking b : upcoming) {
                    out.append("  #").append(b.getId())
                            .append(' ').append(b.getStartTime())
                            .append(" → ").append(b.getEndTime())
                            .append(", ").append(nullToDash(b.getClientNickname()))
                            .append(", ").append(nullToDash(b.getStationName()))
                            .append(", ").append(b.getStatus().getTitle())
                            .append(", ").append(b.getTotalPrice()).append(" руб.\n");
                }
            }
            return out.toString();
        } catch (RuntimeException e) {
            // Проблема с БД не должна ронять чат: просто отдадим модель без снимка.
            return "";
        }
    }

    private static String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}
