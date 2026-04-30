package com.moba.battleserver.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {
    @JsonProperty("success")
    private boolean success;
    @JsonProperty("matchStatus")
    private int matchStatus;
    @JsonProperty("waitTime")
    private long waitTime;
    @JsonProperty("battleId")
    private String battleId;
    @JsonProperty("errorMessage")
    private String errorMessage;

    public static final int STATUS_WAITING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_CANCELLED = 2;
    public static final int STATUS_TIMEOUT = 3;

    public static MatchResponse waiting(long waitTime) {
        return new MatchResponse(true, STATUS_WAITING, waitTime, "", "");
    }

    public static MatchResponse success(String battleId) {
        return new MatchResponse(true, STATUS_SUCCESS, 0, battleId, "");
    }

    public static MatchResponse cancelled() {
        return new MatchResponse(false, STATUS_CANCELLED, 0, "", "");
    }

    public static MatchResponse failure(String errorMessage) {
        return new MatchResponse(false, STATUS_TIMEOUT, 0, "", errorMessage);
    }
}
