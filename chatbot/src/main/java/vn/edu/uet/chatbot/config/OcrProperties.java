package vn.edu.uet.chatbot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {

    private boolean enabled = true;
    private String dataPath = "./tessdata";
    private String language = "vie+eng+osd";
    private int minTextLengthForOcr = 50;
}
