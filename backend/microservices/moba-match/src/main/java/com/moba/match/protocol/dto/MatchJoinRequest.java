package com.moba.match.protocol.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MatchJoinRequest {

    private int gameMode;
    private String nickname;
    private int rankScore;
}
