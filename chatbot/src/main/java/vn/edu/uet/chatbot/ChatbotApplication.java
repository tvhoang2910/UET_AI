package vn.edu.uet.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import vn.edu.uet.chatbot.config.MinioProperties;
import vn.edu.uet.chatbot.config.OcrProperties;
import vn.edu.uet.chatbot.config.RagProperties;
import vn.edu.uet.chatbot.config.QdrantProperties;
import vn.edu.uet.chatbot.config.RateLimitProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({ QdrantProperties.class, RagProperties.class,
		RateLimitProperties.class, OcrProperties.class, MinioProperties.class })
public class ChatbotApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatbotApplication.class, args);
	}

}
