package info.bmdb.controller;

import info.bmdb.service.ChatService;
import info.bmdb.service.PromptLogService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final ChatService chatService;
    private final PromptLogService promptLogService;

    public ChatController(ChatService chatService, PromptLogService promptLogService) {
        this.chatService = chatService;
        this.promptLogService = promptLogService;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String prompt, @RequestParam(name = "log", required = false, defaultValue = "false") boolean log) {
        String response = chatService.ask(prompt);
        // Optional per-prompt persistence (disabled by default)
        if (log) {
            promptLogService.saveInteraction(prompt, response);
        }
        return response;
    }

    /**
     * On-demand session persistence.
     * Expected JSON body:
     * { "title": "...", "startedAt": 123, "finishedAt": 456, "messages": [ {"role":"user|ai","content":"...","ts":123}, ... ] }
     */
    @PostMapping("/sessions")
    public String saveSession(@RequestBody Map<String, Object> payload) {
        String title = String.valueOf(payload.getOrDefault("title", "Session"));
        long startedAt = asLong(payload.get("startedAt"));
        long finishedAt = asLong(payload.get("finishedAt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) payload.getOrDefault("messages", new ArrayList<>());
        promptLogService.saveSession(title, startedAt, finishedAt, messages);
        return "OK";
    }

    private static long asLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(String.valueOf(v)); } catch (NumberFormatException e) { return 0L; }
    }
}
