package info.bmdb.service;

import org.springaicommunity.agent.tools.FileSystemTools;
import org.springaicommunity.agent.tools.GlobTool;
import org.springaicommunity.agent.tools.GrepTool;
import org.springaicommunity.agent.tools.ShellTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Service
public class ChatService {

    private final ChatClient chatClient;
    private final ConversationMemoryService memoryService;

    public ChatService(ChatClient.Builder geminiChatClientBuilder, ConversationMemoryService memoryService) {
        this.chatClient = geminiChatClientBuilder
                .defaultSystem("""
                    You are a helpful coding assistant. You have access to tools 
                    for reading files, searching code, running shell commands, 
                    and editing files. Use them to help the user with their codebase.
                    
                    Current directory: %s
                    """.formatted(System.getProperty("user.dir")))
                .defaultTools(
                        FileSystemTools.builder().build(),
                        GrepTool.builder().build(),
                        GlobTool.builder().build(),
                        ShellTools.builder().build()
                )
                .defaultAdvisors(
                        ToolCallAdvisor.builder().conversationHistoryEnabled(false).build(),
                        MessageChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder().maxMessages(50).build()
                        ).build()
                )
                .build();
        this.memoryService = memoryService;
    }

    public String ask(String prompt){
        return ask(prompt, null);
    }

    public String ask(String prompt, String sessionId){
        String userMsg = (prompt == null) ? "" : prompt;
        StringBuilder capability = new StringBuilder();
        capability.append("[SYSTEM CAPABILITIES]\n")
                .append("- Tu peux utiliser les OUTILS déclarés (Spring AI @Tool) pour agir sur le système local.\n")
                .append("- Accès shell: sur Windows utilise 'cmd.exe /c <commande>'; sur Linux/Mac utilise '/bin/sh -c <commande>'.\n")
                .append("- Tous les outils de 'CommandTools' sont autorisés: systemInfo, exec (pour commandes DOS/Unix), list (explore FS), readFile (lire fichiers texte), readPropertiesValue (lire .properties).\n")
                .append("- Python est disponible si 'systemInfo' le détecte; tu peux l'appeler via l'outil 'exec' (ex: 'python -c \"print(1)\"').\n")
                .append("- Objectif: réponds d'abord avec les outils. Si une commande est nécessaire, propose-la puis exécute-la avec l'outil 'exec'.\n")
                .append("[INSTRUCTIONS]\nToujours privilégier: 1) list -> 2) readFile/readPropertiesValue -> 3) exec pour parsing/grep/Python.\n")
                .append("N'invente pas d'impossibilité: si tu vois un chemin, essaie de le lire avec 'readFile' avant de dire que tu ne peux pas.\n---\n");

        String effectivePrompt = capability + userMsg;
        if (sessionId != null && !sessionId.isBlank()) {
            String history = memoryService.renderHistory(sessionId);
            if (!history.isBlank()) {
                effectivePrompt = capability + "Contexte de la conversation (résumé des derniers messages):\n" + history +
                        "\n---\nMessage utilisateur actuel:\n" + userMsg;
            }
        }

        String content = chatClient
                .prompt(effectivePrompt)
                .tools(new AnimalsService())
                .tools(new DateTimeTools())
                .tools(new CommandTools())
                .tools(new CodeAnalysisTools())
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
