package vn.edu.uet.chatbot.ingest.extractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.ingest.model.DocumentPageText;
import vn.edu.uet.chatbot.ingest.ocr.OcrService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class WordTextExtractor implements DocumentTextExtractor {

    private final OcrService ocrService;
    private static final int ESTIMATED_PAGE_CHAR_LIMIT = 1500;

    @Override
    public List<DocumentPageText> extractPages(File file) throws IOException {
        String filename = file.getName().toLowerCase();
        if (filename.endsWith(".docx")) {
            return extractDocxPages(file);
        } else if (filename.endsWith(".doc")) {
            return extractDocPages(file);
        } else {
            throw new IllegalArgumentException("Không hỗ trợ định dạng Word: " + filename);
        }
    }

    private List<DocumentPageText> extractDocxPages(File file) throws IOException {
        List<DocumentPageText> pages = new ArrayList<>();
        StringBuilder currentPageBuffer = new StringBuilder();
        int currentPageNumber = 1;

        try (FileInputStream fis = new FileInputStream(file);
                XWPFDocument document = new XWPFDocument(fis)) {

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText().trim();
                    if (!text.isEmpty()) {
                        currentPageBuffer.append(text).append("\n\n");
                    }
                    if (hasPageBreak(paragraph)) {
                        flushPage(pages, currentPageBuffer, currentPageNumber++);
                    }
                } else if (element instanceof XWPFTable table) {
                    String markdownTable = convertTableToMarkdown(table);
                    if (!markdownTable.isBlank()) {
                        currentPageBuffer.append(markdownTable).append("\n\n");
                    }
                }

                if (currentPageBuffer.length() >= ESTIMATED_PAGE_CHAR_LIMIT) {
                    flushPage(pages, currentPageBuffer, currentPageNumber++);
                }
            }

            for (XWPFPictureData picture : document.getAllPictures()) {
                byte[] data = picture.getData();
                try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
                    BufferedImage img = ImageIO.read(bais);
                    if (img != null) {
                        String ocrText = ocrService.extractText(img);
                        if (!ocrText.isBlank()) {
                            currentPageBuffer.append("\n[Chữ từ hình ảnh trong tài liệu]:\n")
                                    .append(ocrText)
                                    .append("\n\n");
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Không thể OCR ảnh nhúng trong docx: {}", ex.getMessage());
                }
            }

            flushPage(pages, currentPageBuffer, currentPageNumber);
        }

        return pages;
    }

    private List<DocumentPageText> extractDocPages(File file) throws IOException {
        List<DocumentPageText> pages = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
                HWPFDocument document = new HWPFDocument(fis);
                WordExtractor extractor = new WordExtractor(document)) {

            String[] paragraphs = extractor.getParagraphText();
            StringBuilder buffer = new StringBuilder();
            int pageNum = 1;

            for (String paragraph : paragraphs) {
                String trimmed = paragraph.trim();
                if (!trimmed.isEmpty()) {
                    buffer.append(trimmed).append("\n\n");
                }
                if (buffer.length() >= ESTIMATED_PAGE_CHAR_LIMIT) {
                    flushPage(pages, buffer, pageNum++);
                }
            }
            flushPage(pages, buffer, pageNum);
        }
        return pages;
    }

    private String convertTableToMarkdown(XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        XWPFTableRow headerRow = rows.getFirst();
        sb.append("| ");
        for (XWPFTableCell cell : headerRow.getTableCells()) {
            sb.append(cell.getText().replace("\n", " ").trim()).append(" | ");
        }
        sb.append("\n| ");
        for (int i = 0; i < headerRow.getTableCells().size(); i++) {
            sb.append("--- | ");
        }
        sb.append("\n");

        for (int i = 1; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            sb.append("| ");
            for (XWPFTableCell cell : row.getTableCells()) {
                sb.append(cell.getText().replace("\n", " ").trim()).append(" | ");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private boolean hasPageBreak(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.getCTR() != null && run.getCTR().getLastRenderedPageBreakList() != null
                    && !run.getCTR().getLastRenderedPageBreakList().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void flushPage(List<DocumentPageText> pages, StringBuilder buffer, int pageNum) {
        String content = buffer.toString().trim();
        if (!content.isBlank()) {
            pages.add(new DocumentPageText(pageNum, content));
        }
        buffer.setLength(0);
    }
}
