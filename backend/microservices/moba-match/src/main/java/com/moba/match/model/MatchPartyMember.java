package com.moba.match.model;

import lombok.Data;

@Data
public class MatchPartyMember {

    private long userId;
    private String nickname;
    private int rankScore;
}
