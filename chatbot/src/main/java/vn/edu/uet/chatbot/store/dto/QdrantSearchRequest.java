package vn.edu.uet.chatbot.store.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QdrantSearchRequest(
        List<Double> vector,
        int limit,
        Object filter,
        @JsonProperty("with_payload") Object withPayload,
        @JsonProperty("score_threshold") Double scoreThreshold) {

    public QdrantSearchRequest(List<Double> vector, int limit, Object filter, Object withPayload) {
        this(vector, limit, filter, withPayload, null);
    }
}