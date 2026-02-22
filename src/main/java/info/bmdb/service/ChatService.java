package info.bmdb.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationMemoryService memoryService;

    public ChatService(ChatClient.Builder geminiChatClientBuilder, ConversationMemoryService memoryService) {
        this.chatClient = geminiChatClientBuilder.build();
        this.memoryService = memoryService;
    }

    public String ask(String prompt){
        return ask(prompt, null);
    }

    public String ask(String prompt, String sessionId){
        String effectivePrompt = prompt;
        if (sessionId != null && !sessionId.isBlank()) {
            String history = memoryService.renderHistory(sessionId);
            if (!history.isBlank()) {
                effectivePrompt = "Contexte de la conversation (résumé des derniers messages):\n" + history +
                        "\n---\nMessage utilisateur actuel:\n" + (prompt == null ? "" : prompt);
            }
        }

        String content = chatClient
                .prompt(effectivePrompt)
                .tools(new AnimalsService())
                .tools(new DateTimeTools())
                .tools(new CommandTools())
                .call()
                .content();

        // Fallback: if the model returned a tool-call JSON instead of executing it, execute locally for time queries
        if (content != null && content.contains("dateTimeNow")) {
            content = new DateTimeTools().dateTimeNow(null);
        }

        // Simple heuristic fallbacks when the model didn't execute the CommandTools
        String lp = (prompt == null) ? "" : prompt.toLowerCase(Locale.ROOT);

        // 1) System info
        if (lp.contains("info système") || lp.contains("infos système") || lp.contains("infos systeme") || lp.contains("system info")) {
            content = new CommandTools().systemInfo();
        } else {
            // 2) Directory listing
            boolean askedList = (lp.contains("liste") || lp.contains("list")) &&
                    (lp.contains("fichier") || lp.contains("fichiers") || lp.contains("répertoire") || lp.contains("repertoire") || lp.contains("dossier") || lp.contains("directories") || lp.contains("files"));
            boolean headerOnly = false;
            if (content != null) {
                String lc = content.toLowerCase(Locale.ROOT);
                headerOnly = lc.contains("voici la liste") && !(lc.contains("[d]") || lc.contains("[f]") || lc.contains("base:"));
            }
            if (askedList || headerOnly) {
                content = new CommandTools().list(null, 2, 200);
            } else {
                // 3) Execute command if backticks are present
                String cmd = extractBetweenBackticks(prompt);
                if (cmd != null && (lp.contains("exécute") || lp.contains("execute") || lp.contains("run ") || lp.contains("lance ") || lp.contains("launch "))) {
                    content = new CommandTools().exec(cmd, null, null);
                }
            }
        }

        // Save into in-app memory if session is provided
        if (sessionId != null && !sessionId.isBlank()) {
            memoryService.appendUser(sessionId, prompt == null ? "" : prompt);
            memoryService.appendAssistant(sessionId, content == null ? "" : content);
        }

        return content;
    }

    private static String extractBetweenBackticks(String text) {
        if (text == null) return null;
        int start = text.indexOf('`');
        if (start < 0) return null;
        int end = text.indexOf('`', start + 1);
        if (end < 0) return null;
        String inner = text.substring(start + 1, end).trim();
        return inner.isEmpty() ? null : inner;
    }
}
