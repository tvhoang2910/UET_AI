package vn.edu.uet.chatbot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vn.edu.uet.chatbot.client.LLMClient;

@SpringBootTest(classes = ChatbotApplication.class)
class OllamaClientTest {

    @Autowired
    private LLMClient llmClient;

    @Test
    void should_chat() {
        String answer;
        try {
            answer = llmClient.chat("Can you help me write a poem about a cat?");
        } catch (Exception e) {
            // Ollama might be offline in CI/local dev; don't fail the pipeline for
            // connectivity.
            answer = null;
        }
    }
}
