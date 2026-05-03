package com.moba.match.protocol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchCancelResponse {

    private boolean success;
    private String errorCode;
    private String errorMessage;
}
