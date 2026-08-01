package vn.edu.uet.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    private String baseUrl;

    private String chatModel;

    private String embeddingModel;
}
