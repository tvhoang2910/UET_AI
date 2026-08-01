package vn.edu.uet.chatbot.ingest.extractor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.ingest.model.DocumentPageText;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor implements DocumentTextExtractor {

    @Override
    public List<DocumentPageText> extractPages(File file) throws IOException {
        try (PDDocument document = PDDocument.load(file)) {
            List<DocumentPageText> pages = new ArrayList<>(document.getNumberOfPages());

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
                    if (!content.isBlank()) {
                        pages.add(new DocumentPageText(currentPageNum, content));
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document); // 1 pass duy nhất O(N)
            return pages;
        }
    }
}