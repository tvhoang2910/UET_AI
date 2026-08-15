package vn.edu.uet.chatbot.ingest.extractor;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.ingest.model.DocumentPageText;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
@Primary
@RequiredArgsConstructor
public class DelegatingDocumentTextExtractor implements DocumentTextExtractor {

    private final PdfTextExtractor pdfTextExtractor;
    private final WordTextExtractor wordTextExtractor;

    @Override
    public List<DocumentPageText> extractPages(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("File không tồn tại");
        }

        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) {
            return pdfTextExtractor.extractPages(file);
        } else if (name.endsWith(".docx") || name.endsWith(".doc")) {
            return wordTextExtractor.extractPages(file);
        } else {
            throw new IllegalArgumentException("Định dạng tài liệu không được hỗ trợ: " + file.getName());
        }
    }
}
