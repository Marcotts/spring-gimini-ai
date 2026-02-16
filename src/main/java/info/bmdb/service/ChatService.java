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
        return chatClient
                .prompt(prompt)
                .tools(new AnimalsService())
                .tools(new DateTimeTools())
                .call()
                .content();
    }
}
