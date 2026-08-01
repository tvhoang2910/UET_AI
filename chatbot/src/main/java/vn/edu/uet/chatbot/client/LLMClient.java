package vn.edu.uet.chatbot.client;

import vn.edu.uet.chatbot.dto.ollama.Message;

import java.util.List;

public interface LLMClient {

    String chat(String prompt);

    String chat(List<Message> messages);

}
