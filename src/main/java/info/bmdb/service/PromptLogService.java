package info.bmdb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for persisting prompts and AI responses into the local runtime directory
 * under ./promptlog. Each interaction is saved in a timestamped file. Additionally,
 * an append-only log file interactions.log is maintained for easy tailing.
 */
@Service
public class PromptLogService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final Lock IO_LOCK = new ReentrantLock();

    private final Path baseDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PromptLogService() {
        this.baseDir = Path.of("promptlog");
        ensureDir();
    }

    private void ensureDir() {
        try {
            if (Files.notExists(baseDir)) {
                Files.createDirectories(baseDir);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create promptlog directory at " + baseDir.toAbsolutePath(), e);
        }
    }

    public void saveInteraction(String prompt, String response) {
        LocalDateTime now = LocalDateTime.now();
        String ts = FILE_TS.format(now);
        String safePrefix = "interaction_" + ts;
        String content = "=== PROMPT ===\n" + nvl(prompt) + "\n\n=== RESPONSE ===\n" + nvl(response) + "\n";

        IO_LOCK.lock();
        try {
            // Write individual file
            Path file = baseDir.resolve(safePrefix + ".txt");
            Files.writeString(
                    file,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

            // Append to rolling log
            Path rolling = baseDir.resolve("interactions.log");
            String header = "\n----- " + now + " -----\n";
            Files.writeString(
                    rolling,
                    header + content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            // Don't break the chat flow because of logging errors; surface minimal info
            System.err.println("[PromptLogService] Failed to persist interaction: " + e.getMessage());
        } finally {
            IO_LOCK.unlock();
        }
    }

    /**
     * Persist a full chat session provided by the frontend (on-demand save).
     * Writes a human-readable TXT with a JSON block for programmatic reuse,
     * and appends a short line into sessions.log.
     */
    public void saveSession(String title, long startedAt, long finishedAt, List<Map<String, Object>> messages) {
        LocalDateTime now = LocalDateTime.now();
        String ts = FILE_TS.format(now);
        String safePrefix = "session_" + ts;

        // Build readable transcript
        StringBuilder sb = new StringBuilder();
        sb.append("=== SESSION ===\n");
        sb.append("Title: ").append(nvl(title)).append('\n');
        sb.append("StartedAt: ").append(formatInstant(startedAt)).append(" (epoch ").append(startedAt).append(")\n");
        sb.append("FinishedAt: ").append(formatInstant(finishedAt)).append(" (epoch ").append(finishedAt).append(")\n");
        sb.append("Messages: ").append(messages == null ? 0 : messages.size()).append("\n\n");
        if (messages != null) {
            for (Map<String, Object> m : messages) {
                String role = String.valueOf(m.getOrDefault("role", "user"));
                String content = String.valueOf(m.getOrDefault("content", ""));
                long tsEpoch = 0L;
                try { tsEpoch = Long.parseLong(String.valueOf(m.getOrDefault("ts", 0))); } catch (NumberFormatException ignored) {}
                sb.append("--- ").append(role.toUpperCase()).append(" @ ").append(formatInstant(tsEpoch)).append(" ---\n");
                sb.append(content).append("\n\n");
            }
        }
        sb.append("=== RAW JSON ===\n");
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                    "title", nvl(title),
                    "startedAt", startedAt,
                    "finishedAt", finishedAt,
                    "messages", messages
            ));
            sb.append(json).append('\n');
        } catch (Exception e) {
            sb.append("{\n  \"error\": \"failed to serialize session json\"\n}\n");
        }

        IO_LOCK.lock();
        try {
            Path file = baseDir.resolve(safePrefix + ".txt");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            Path rolling = baseDir.resolve("sessions.log");
            String line = String.format("%s | %s | messages=%d\n", now, nvl(title), messages == null ? 0 : messages.size());
            Files.writeString(rolling, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[PromptLogService] Failed to persist session: " + e.getMessage());
        } finally {
            IO_LOCK.unlock();
        }
    }

    private static String formatInstant(long epochMillis) {
        if (epochMillis <= 0) return "n/a";
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).toString();
        } catch (Exception e) {
            return "n/a";
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
