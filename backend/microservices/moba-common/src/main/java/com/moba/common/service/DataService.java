package com.moba.common.service;

import com.moba.common.dto.BattleLogDTO;
import com.moba.common.dto.ReplayDTO;

import java.util.List;
import java.util.Optional;

public interface DataService {

    BattleLogDTO saveBattleLog(BattleLogDTO battleLog);

    Optional<BattleLogDTO> getBattleLog(String battleId);

    List<BattleLogDTO> getPlayerBattleHistory(long playerId, int limit);

    ReplayDTO saveReplay(ReplayDTO replay);

    Optional<ReplayDTO> getReplay(String battleId);

    List<BattleLogDTO> getRecentBattles(int gameMode, int limit);
}
