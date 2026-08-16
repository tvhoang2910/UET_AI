package vn.edu.uet.chatbot.ingest.extractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import vn.edu.uet.chatbot.config.OcrProperties;
import vn.edu.uet.chatbot.ingest.model.DocumentPageText;
import vn.edu.uet.chatbot.ingest.ocr.OcrService;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class PdfTextExtractor implements DocumentTextExtractor {

    private static final Pattern SCHEDULE_ROW_PATTERN = Pattern.compile(
            "^\\s*(\\d{1,3})\\s+(\\d{1,2}/\\d{1,2}/\\d{4}(?:\\s*(?:-|–|đến)\\s*\\d{1,2}/\\d{1,2}/\\d{4})?)\\s+(.+)$");

    private static final Pattern NOISE_LINE_PATTERN = Pattern.compile(
            "^\\s*(?:Trang\\s+\\d+|\\d+|UBND\\s+.*|ĐẠI HỌC\\s+.*|TRƯỜNG\\s+.*)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private final OcrService ocrService;
    private final OcrProperties ocrProperties;
    private final VietnameseTextCleaner vietnameseTextCleaner;

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

                    String rawContent = cleanAndNormalizeUnicode(buffer.toString());
                    String finalContent = vietnameseTextCleaner.clean(rawContent);
                    boolean fontCorrupted = isFontCorruptedOrSparse(rawContent);

                    if (fontCorrupted && ocrProperties.isEnabled()) {
                        log.info(
                                "Trang {} file '{}' phát hiện lỗi font/CMap hoặc quá ít chữ. Kích hoạt OCR trực quan...",
                                currentPageNum, file.getName());
                        try {
                            BufferedImage pageImage = pdfRenderer.renderImageWithDPI(currentPageNum - 1, 300);
                            String ocrContent = vietnameseTextCleaner.clean(
                                    cleanAndNormalizeUnicode(ocrService.extractText(pageImage)));
                            if (!ocrContent.isBlank()) {
                                finalContent = ocrContent;
                            }
                        } catch (Exception ex) {
                            log.warn("Lỗi khi OCR trang {}: {}", currentPageNum, ex.getMessage());
                        }
                    }

                    String markdownTables = extractTablesFromPage(document, currentPageNum);
                    if (markdownTables.isBlank()) {
                        markdownTables = tryParseScheduleTableWithMultiLine(finalContent, currentPageNum);
                    }
                    if (!markdownTables.isBlank()) {
                        finalContent = finalContent + "\n\n" + markdownTables;
                    }

                    if (!finalContent.isBlank()) {
                        pages.add(new DocumentPageText(currentPageNum, finalContent));
                    }
                }
            };

            stripper.setSortByPosition(true);
            stripper.getText(document);
            return pages;
        }
    }

    private boolean isFontCorruptedOrSparse(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }

        if (text.length() < ocrProperties.getMinTextLengthForOcr()) {
            return true;
        }

        long corruptedChars = text.chars().filter(ch -> ch == '?' || ch == '\uFFFD').count();
        double corruptionRatio = (double) corruptedChars / text.length();
        return corruptionRatio > 0.06;
    }

    private String extractTablesFromPage(PDDocument document, int pageNumber) {
        StringBuilder sb = new StringBuilder();
        try {
            ObjectExtractor objectExtractor = new ObjectExtractor(document);
            Page page = objectExtractor.extract(pageNumber);

            List<Table> tables = new SpreadsheetExtractionAlgorithm().extract(page);
            if (tables.isEmpty()) {
                tables = new BasicExtractionAlgorithm().extract(page);
            }

            for (Table table : tables) {
                String markdownTable = convertTabulaTableToMarkdown(table);
                if (!markdownTable.isBlank()) {
                    sb.append("\n[Bảng dữ liệu trích xuất trang ")
                            .append(pageNumber)
                            .append("]:\n")
                            .append(markdownTable)
                            .append("\n");
                }
            }
        } catch (Exception ex) {
            log.debug("Tabula không tìm thấy bảng trang {}: {}", pageNumber, ex.getMessage());
        }
        return sb.toString();
    }

    private String tryParseScheduleTableWithMultiLine(String text, int pageNumber) {
        if (text == null || text.isBlank()) {
            return "";
        }

        List<String[]> rows = new ArrayList<>();
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || NOISE_LINE_PATTERN.matcher(trimmed).matches()) {
                continue;
            }

            Matcher matcher = SCHEDULE_ROW_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                rows.add(new String[] {
                        matcher.group(1).trim(),
                        matcher.group(2).trim(),
                        matcher.group(3).trim()
                });
            } else if (!rows.isEmpty()) {
                String[] lastRow = rows.get(rows.size() - 1);
                lastRow[2] = lastRow[2] + " " + trimmed;
            }
        }

        if (rows.size() >= 3) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n[Bảng kế hoạch / Lịch trình trang ")
                    .append(pageNumber)
                    .append("]:\n")
                    .append("| STT | Thời gian | Nội dung công việc / Kế hoạch |\n")
                    .append("| --- | --- | --- |\n");
            for (String[] row : rows) {
                sb.append("| ")
                        .append(row[0])
                        .append(" | ")
                        .append(row[1])
                        .append(" | ")
                        .append(row[2].replace("|", "\\|"))
                        .append(" |\n");
            }
            return sb.toString();
        }

        return "";
    }

    @SuppressWarnings("rawtypes")
    private String convertTabulaTableToMarkdown(Table table) {
        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows == null || rows.size() < 2) {
            return "";
        }

        int maxCols = 0;
        for (List<RectangularTextContainer> row : rows) {
            maxCols = Math.max(maxCols, row.size());
        }
        if (maxCols < 2) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        List<RectangularTextContainer> headerRow = rows.get(0);
        sb.append("| ");
        for (RectangularTextContainer cell : headerRow) {
            sb.append(cleanCellText(cell.getText())).append(" | ");
        }
        for (int i = headerRow.size(); i < maxCols; i++) {
            sb.append(" | ");
        }
        sb.append("\n| ");
        for (int i = 0; i < maxCols; i++) {
            sb.append("--- | ");
        }
        sb.append("\n");

        boolean hasValidData = false;
        for (int i = 1; i < rows.size(); i++) {
            List<RectangularTextContainer> row = rows.get(i);
            sb.append("| ");
            for (RectangularTextContainer cell : row) {
                String text = cleanCellText(cell.getText());
                if (!text.isBlank()) {
                    hasValidData = true;
                }
                sb.append(text).append(" | ");
            }
            for (int j = row.size(); j < maxCols; j++) {
                sb.append(" | ");
            }
            sb.append("\n");
        }

        return hasValidData ? sb.toString().trim() : "";
    }

    private String cleanAndNormalizeUnicode(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC);
        return normalized.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private String cleanCellText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = cleanAndNormalizeUnicode(text);
        return normalized.replace("\n", " ")
                .replace("|", "\\|")
                .replaceAll("\\s+", " ")
                .trim();
    }
}