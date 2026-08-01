package vn.edu.uet.chatbot.chunk;

import java.util.List;

public interface TextChunker {
    List<ChunkSegment> chunk(String text, int pageNumber);

    default List<String> chunk(String text) {
        return chunk(text, -1).stream()
                .map(ChunkSegment::content)
                .toList();
    }
}
