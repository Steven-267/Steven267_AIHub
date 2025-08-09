package com.steven.ai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.steven.ai.entity.po.Msg;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryChatHistoryRepository implements ChatHistoryRepository {

    private Map<String, List<String>> chatHistory;

    private final ObjectMapper objectMapper;

    private final ChatMemory chatMemory;

    @Override
    public void save(String type, String chatId) {
        /*if (!chatHistory.containsKey(type)) {
            chatHistory.put(type, new ArrayList<>());
        }
        List<String> chatIds = chatHistory.get(type);*/
        List<String> chatIds = chatHistory.computeIfAbsent(type, k -> new ArrayList<>());
        if (chatIds.contains(chatId)) {
            return;
        }
        chatIds.add(chatId);
    }

    @Override
    public List<String> getChatIds(String type) {
        /*List<String> chatIds = chatHistory.get(type);
        return chatIds == null ? List.of() : chatIds;*/
        return chatHistory.getOrDefault(type, List.of());
    }

    @Override
    public void delete(String type, String chatId) {
        List<String> chatIds = chatHistory.get(type);
        if (chatIds != null) {
            chatIds.remove(chatId);
        }
        // 同时清空该 chatId 的内存消息
        removeFromMemory(chatId);
    }

    @Override
    public void clear(String type) {
        List<String> chatIds = chatHistory.get(type);
        if (chatIds != null) {
            // 清空所有相关 chatId 的内存消息
            for (String id : new ArrayList<>(chatIds)) {
                removeFromMemory(id);
            }
            chatIds.clear();
        }
    }

    private void removeFromMemory(String chatId) {
        // 通过反射删除 InMemoryChatMemory 中的会话
        try {
            Class<InMemoryChatMemory> clazz = InMemoryChatMemory.class;
            Field field = clazz.getDeclaredField("conversationHistory");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, List<Message>> memory = (Map<String, List<Message>>) field.get(chatMemory);
            if (memory != null) {
                memory.remove(chatId);
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    @PostConstruct
    private void init() {
        // 1.初始化会话历史记录
        this.chatHistory = new HashMap<>();
        // 2.读取本地会话历史和会话记忆
        FileSystemResource historyResource = new FileSystemResource("chat-history.json");
        FileSystemResource memoryResource = new FileSystemResource("chat-memory.json");
        if (!historyResource.exists()) {
            return;
        }
        try {
            // 会话历史
            Map<String, List<String>> chatIds = this.objectMapper.readValue(historyResource.getInputStream(), new TypeReference<>() {
            });
            if (chatIds != null) {
                this.chatHistory = chatIds;
            }
            // 会话记忆
            Map<String, List<Msg>> memory = this.objectMapper.readValue(memoryResource.getInputStream(), new TypeReference<>() {
            });
            if (memory != null) {
                memory.forEach(this::convertMsgToMessage);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void convertMsgToMessage(String chatId, List<Msg> messages) {
        this.chatMemory.add(chatId, messages.stream().map(Msg::toMessage).toList());
    }

    @PreDestroy
    private void persistent() {
        String history = toJsonString(this.chatHistory);
        String memory = getMemoryJsonString();
        FileSystemResource historyResource = new FileSystemResource("chat-history.json");
        FileSystemResource memoryResource = new FileSystemResource("chat-memory.json");
        try (
                PrintWriter historyWriter = new PrintWriter(historyResource.getOutputStream(), true, StandardCharsets.UTF_8);
                PrintWriter memoryWriter = new PrintWriter(memoryResource.getOutputStream(), true, StandardCharsets.UTF_8)
        ) {
            historyWriter.write(history);
            memoryWriter.write(memory);
        } catch (IOException ex) {
            log.error("IOException occurred while saving vector store file.", ex);
            throw new RuntimeException(ex);
        } catch (SecurityException ex) {
            log.error("SecurityException occurred while saving vector store file.", ex);
            throw new RuntimeException(ex);
        } catch (NullPointerException ex) {
            log.error("NullPointerException occurred while saving vector store file.", ex);
            throw new RuntimeException(ex);
        }
    }

    private String getMemoryJsonString() {
        Class<InMemoryChatMemory> clazz = InMemoryChatMemory.class;
        try {
            Field field = clazz.getDeclaredField("conversationHistory");
            field.setAccessible(true);
            Map<String, List<Message>> memory = (Map<String, List<Message>>) field.get(chatMemory);
            Map<String, List<Msg>> memoryToSave = new HashMap<>();
            memory.forEach((chatId, messages) -> memoryToSave.put(chatId, messages.stream().map(Msg::new).toList()));
            return toJsonString(memoryToSave);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private String toJsonString(Object object) {
        ObjectWriter objectWriter = this.objectMapper.writerWithDefaultPrettyPrinter();
        try {
            return objectWriter.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializing documentMap to JSON.", e);
        }
    }
}
