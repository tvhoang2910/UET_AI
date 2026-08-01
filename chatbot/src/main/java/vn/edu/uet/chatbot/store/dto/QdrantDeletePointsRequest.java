package vn.edu.uet.chatbot.store.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record QdrantDeletePointsRequest(
        QdrantFilter filter) {

    public record QdrantFilter(
            List<QdrantCondition> must) {
    }

    public record QdrantCondition(
            String key,
            QdrantMatch match) {
    }

    public record QdrantMatch(
            Object value) {
    }

    @JsonProperty("filter")
    @Override
    public QdrantFilter filter() {
        return filter;
    }
}
