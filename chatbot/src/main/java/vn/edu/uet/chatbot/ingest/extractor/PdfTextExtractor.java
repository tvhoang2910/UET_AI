package vn.edu.uet.chatbot.ingest.extractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.config.OcrProperties;
import vn.edu.uet.chatbot.ingest.model.DocumentPageText;
import vn.edu.uet.chatbot.ingest.ocr.OcrService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PdfTextExtractor implements DocumentTextExtractor {

    private final OcrService ocrService;
    private final OcrProperties ocrProperties;

    @Override
    public List<DocumentPageText> extractPages(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            List<DocumentPageText> pages = new ArrayList<>(document.getNumberOfPages());
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            PDFTextStripper stripper = new PDFTextStripper() {
                private final StringBuilder buffer = new StringBuilder();
                private int currentPageNum = 0;

                @Override
                protected void startPage(PDPage page) throws IOException {
                    super.startPage(page);
                    buffer.setLength(0);
                    currentPageNum++;
                }

                @Override
                protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
                    buffer.append(text);
                }

                @Override
                protected void endPage(PDPage page) throws IOException {
                    super.endPage(page);
                    String content = buffer.toString().trim();

                    if (content.length() < ocrProperties.getMinTextLengthForOcr() && ocrProperties.isEnabled()) {
                        log.info("Trang {} của file '{}' có ít chữ ({} ký tự). Kích hoạt OCR...",
                                currentPageNum, file.getName(), content.length());
                        try {
                            BufferedImage pageImage = pdfRenderer.renderImageWithDPI(currentPageNum - 1, 300);
                            String ocrContent = ocrService.extractText(pageImage);
                            if (!ocrContent.isBlank()) {
                                content = (content.isBlank() ? "" : content + "\n\n") + ocrContent;
                            }
                        } catch (Exception ex) {
                            log.warn("Lỗi khi render ảnh/OCR trang {}: {}", currentPageNum, ex.getMessage());
                        }
                    }

                    if (!content.isBlank()) {
                        pages.add(new DocumentPageText(currentPageNum, content));
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document);
            return pages;
        }
    }
}