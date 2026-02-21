package info.bmdb.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder geminiChatClientBuilder) {
        this.chatClient = geminiChatClientBuilder.build();
    }

    public String ask(String prompt){
        String content = chatClient
                .prompt(prompt)
                .tools(new AnimalsService())
                .tools(new DateTimeTools())
                .call()
                .content();

        // Fallback: if the model returned a tool-call JSON instead of executing it, execute locally for time queries
        if (content != null && content.contains("dateTimeNow")) {
            // Execute unified tool locally and return human-friendly text
            return new DateTimeTools().dateTimeNow(null);
        }
        return content;
    }
}
