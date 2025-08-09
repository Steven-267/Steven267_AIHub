package com.steven.ai.controller;

import com.steven.ai.repository.ChatHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai")
public class TravelAgentController {

    private final ChatClient travelAgentChatClient;
    private final ChatHistoryRepository chatHistoryRepository;

    @RequestMapping(value = "/travel", produces = "text/html;charset=utf-8")
    public Flux<String> travel(String prompt, String chatId) {
        chatHistoryRepository.save("travel", chatId);
        return travelAgentChatClient
                .prompt()
                .user(prompt)
                .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                .stream()
                .content();
    }
}



