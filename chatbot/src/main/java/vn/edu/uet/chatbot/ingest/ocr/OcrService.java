package vn.edu.uet.chatbot.ingest.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Service;
import vn.edu.uet.chatbot.config.OcrProperties;

import java.awt.image.BufferedImage;
import java.io.File;

@Service
@Slf4j
@RequiredArgsConstructor
public class OcrService {

    private final OcrProperties ocrProperties;

    public String extractText(BufferedImage image) {
        if (!ocrProperties.isEnabled() || image == null) {
            return "";
        }
        try {
            ITesseract tesseract = createTesseractInstance();
            String result = tesseract.doOCR(image);
            return result != null ? result.trim() : "";
        } catch (Throwable ex) {
            log.warn("Lỗi khi thực hiện OCR trên ảnh: {}", ex.getMessage());
            return "";
        }
    }

    public String extractText(File imageFile) {
        if (!ocrProperties.isEnabled() || imageFile == null || !imageFile.exists()) {
            return "";
        }
        try {
            ITesseract tesseract = createTesseractInstance();
            String result = tesseract.doOCR(imageFile);
            return result != null ? result.trim() : "";
        } catch (Throwable ex) {
            log.warn("Lỗi khi thực hiện OCR trên file '{}': {}", imageFile.getName(), ex.getMessage());
            return "";
        }
    }

    private ITesseract createTesseractInstance() {
        ITesseract tesseract = new Tesseract();
        if (ocrProperties.getDataPath() != null && !ocrProperties.getDataPath().isBlank()) {
            tesseract.setDatapath(ocrProperties.getDataPath());
        }
        tesseract.setLanguage(ocrProperties.getLanguage());
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);
        return tesseract;
    }
}
