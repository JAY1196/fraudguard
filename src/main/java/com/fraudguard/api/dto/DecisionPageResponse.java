package com.fraudguard.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Paginated risk decisions response")
public record DecisionPageResponse(
        List<DecisionSummaryResponse> decisions,

        @JsonProperty("next_cursor")
        String nextCursor,

        @JsonProperty("has_more")
        boolean hasMore
) {
    public DecisionPageResponse {
        decisions = List.copyOf(decisions);
    }
}