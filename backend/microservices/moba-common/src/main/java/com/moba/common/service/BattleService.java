package com.moba.common.service;

import com.moba.common.dto.CreateBattleRequest;
import com.moba.common.dto.CreateBattleResponse;
import com.moba.common.dto.BattleResultDTO;

public interface BattleService {

    CreateBattleResponse createBattle(CreateBattleRequest request);

    BattleResultDTO getBattleResult(String battleId);

    boolean isBattleRunning(String battleId);

    int getRoomCount();

    int getTotalPlayers();
}
