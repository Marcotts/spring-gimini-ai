package info.bmdb.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very lightweight in-process conversation history.
 * - Keeps the latest N messages per session (user + assistant), default N=20
 * - Thread-safe for concurrent requests
 * - Render method returns a compact textual summary to inject as context
 */
@Service
public class ConversationMemoryService {

    private static final int DEFAULT_MAX_MESSAGES = 20; // total messages (user+assistant)

    private final Map<String, Deque<Message>> sessions = new ConcurrentHashMap<>();
    private volatile int maxMessages = DEFAULT_MAX_MESSAGES;

    public void setMaxMessages(int maxMessages) {
        if (maxMessages > 0) this.maxMessages = maxMessages;
    }

    public void appendUser(String sessionId, String content) {
        append(sessionId, new Message(Role.USER, nvl(content)));
    }

    public void appendAssistant(String sessionId, String content) {
        append(sessionId, new Message(Role.ASSISTANT, nvl(content)));
    }

    public void clear(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        sessions.remove(sessionId);
    }

    public String renderHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return "";
        Deque<Message> dq = sessions.get(sessionId);
        if (dq == null || dq.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Message m : dq) {
            sb.append(m.role.name().toLowerCase()).append(": ")
              .append(m.content)
              .append("\n");
        }
        return sb.toString().trim();
    }

    private void append(String sessionId, Message msg) {
        if (sessionId == null || sessionId.isBlank()) return;
        Objects.requireNonNull(msg);
        Deque<Message> dq = sessions.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (dq) {
            dq.addLast(msg);
            // Trim to the last maxMessages
            while (dq.size() > maxMessages) {
                dq.pollFirst();
            }
        }
    }

    private static String nvl(String s) { return s == null ? "" : s; }

    enum Role { USER, ASSISTANT }

    static final class Message {
        final Role role;
        final String content;
        final long ts;
        Message(Role role, String content) {
            this.role = role;
            this.content = content;
            this.ts = Instant.now().toEpochMilli();
        }
    }
}
