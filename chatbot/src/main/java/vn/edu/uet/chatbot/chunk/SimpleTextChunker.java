package vn.edu.uet.chatbot.chunk;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.config.RagProperties;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SimpleTextChunker implements TextChunker {

    private final RagProperties ragProperties;

    @Override
    public List<ChunkSegment> chunk(String text, int pageNumber) {
        List<ChunkSegment> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int chunkSize = ragProperties.getChunkSize();
        int overlap = ragProperties.getChunkOverlap();
        if (chunkSize <= 0) {
            throw new IllegalStateException("rag.chunk-size must be greater than 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalStateException("rag.chunk-overlap must be >= 0 and smaller than chunk-size");
        }

        List<String> blocks = splitIntoBlocks(text);
        StringBuilder current = new StringBuilder();
        String carryOver = "";

        for (String block : blocks) {
            String remaining = block.strip();
            while (!remaining.isBlank()) {
                if (current.length() == 0 && !carryOver.isBlank()) {
                    current.append(carryOver);
                    carryOver = "";
                }

                String separator = current.length() == 0 ? "" : "\n\n";
                int available = chunkSize - current.length() - separator.length();
                if (available <= 0) {
                    flushCurrent(chunks, current, pageNumber, overlap);
                    carryOver = chunks.isEmpty() ? "" : overlapTail(chunks.getLast().content(), overlap);
                    continue;
                }

                if (remaining.length() <= available) {
                    if (!separator.isEmpty()) {
                        current.append(separator);
                    }
                    current.append(remaining);
                    remaining = "";
                    continue;
                }

                String segment = takeSoftSegment(remaining, available);
                if (segment.isBlank()) {
                    segment = remaining.substring(0, Math.min(available, remaining.length()));
                }

                if (!separator.isEmpty()) {
                    current.append(separator);
                }
                current.append(segment);
                remaining = remaining.substring(Math.min(segment.length(), remaining.length())).stripLeading();
                flushCurrent(chunks, current, pageNumber, overlap);
                carryOver = chunks.isEmpty() ? "" : overlapTail(chunks.getLast().content(), overlap);
            }
        }

        if (current.length() > 0) {
            flushCurrent(chunks, current, pageNumber, overlap);
        }

        return chunks;
    }

    private void flushCurrent(List<ChunkSegment> chunks, StringBuilder current, int pageNumber, int overlap) {
        if (current.length() == 0) {
            return;
        }
        String chunkText = current.toString().strip();
        if (!chunkText.isBlank()) {
            chunks.add(new ChunkSegment(chunkText, pageNumber));
        }
        current.setLength(0);
    }

    private List<String> splitIntoBlocks(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n");
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isBlank()) {
                addBlock(blocks, current);
                continue;
            }

            if (isHeadingLine(trimmed)) {
                addBlock(blocks, current);
                blocks.add(trimmed);
                continue;
            }

            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(trimmed);
        }

        addBlock(blocks, current);
        return blocks;
    }

    private void addBlock(List<String> blocks, StringBuilder current) {
        if (current.isEmpty()) {
            return;
        }
        String block = current.toString().strip();
        if (!block.isBlank()) {
            blocks.add(block);
        }
        current.setLength(0);
    }

    private boolean isHeadingLine(String line) {
        if (line.startsWith("#")) {
            return true;
        }
        if (line.endsWith(":") && line.length() <= 120) {
            return true;
        }

        boolean hasLetter = false;
        boolean allUpperCase = true;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isLetter(ch)) {
                hasLetter = true;
                if (Character.toLowerCase(ch) == ch) {
                    allUpperCase = false;
                    break;
                }
            }
        }

        return hasLetter && allUpperCase && line.length() <= 80;
    }

    private String takeSoftSegment(String text, int maxLength) {
        int limit = Math.min(maxLength, text.length());
        if (limit <= 0) {
            return "";
        }

        int cut = findBestCutIndex(text, limit);
        if (cut <= 0) {
            cut = limit;
        }
        return text.substring(0, cut);
    }

    private int findBestCutIndex(String text, int limit) {
        int cut = -1;
        cut = Math.max(cut, lastIndexAfter(text, limit, "\n\n"));
        cut = Math.max(cut, lastIndexAfter(text, limit, "\n"));
        cut = Math.max(cut, lastSentenceBoundary(text, limit));
        cut = Math.max(cut, lastSpaceAfter(text, limit));
        return cut;
    }

    private int lastIndexAfter(String text, int limit, String boundary) {
        int index = text.lastIndexOf(boundary, limit - 1);
        if (index < 0) {
            return -1;
        }
        return index + boundary.length();
    }

    private int lastSentenceBoundary(String text, int limit) {
        int boundary = -1;
        int upperBound = Math.min(limit, text.length());
        for (int i = 0; i < upperBound; i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '។' || c == '。' || c == '！' || c == '？') {
                boundary = i + 1;
            }
        }
        return boundary;
    }

    private int lastSpaceAfter(String text, int limit) {
        int index = text.lastIndexOf(' ', limit - 1);
        if (index < 0) {
            return -1;
        }
        return index + 1;
    }

    private String overlapTail(String text, int overlap) {
        if (overlap <= 0 || text == null || text.isBlank())
            return "";
        int start = Math.max(0, text.length() - overlap);
        String tail = text.substring(start);
        // dịch về khoảng trắng gần nhất để không cắt đôi từ
        int space = tail.indexOf(' ');
        if (space > 0 && space < tail.length() - 1) {
            return tail.substring(space + 1).stripLeading();
        }
        return tail.stripLeading();
    }
}
