package com.moba.common.service;

import com.moba.common.constant.GameMode;
import com.moba.common.dto.BattleLogDTO;
import com.moba.common.dto.ReplayDTO;

import java.util.List;
import java.util.Optional;

public interface DataService {

    BattleLogDTO saveBattleLog(BattleLogDTO battleLog);

    Optional<BattleLogDTO> getBattleLog(long battleId);

    List<BattleLogDTO> getUserBattleHistory(long userId, int limit);

    ReplayDTO saveReplay(ReplayDTO replay);

    Optional<ReplayDTO> getReplay(long battleId);

    List<BattleLogDTO> getRecentBattles(GameMode gameMode, int limit);
}
