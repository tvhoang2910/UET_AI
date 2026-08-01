package vn.edu.uet.chatbot.ingest.extractor;

import vn.edu.uet.chatbot.ingest.model.DocumentPageText;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public interface DocumentTextExtractor {
    List<DocumentPageText> extractPages(File file) throws IOException;

    default String extract(File file) throws IOException {
        return extractPages(file).stream()
                .map(DocumentPageText::content)
                .collect(Collectors.joining("\n\n"));
    }
}
