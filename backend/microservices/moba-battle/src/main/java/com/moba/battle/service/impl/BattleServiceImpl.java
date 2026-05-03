package com.moba.battle.service.impl;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.BattleRoom;
import com.moba.common.constant.GameMode;
import com.moba.common.dto.BattleResultDTO;
import com.moba.common.dto.CreateBattleRequest;
import com.moba.common.dto.CreateBattleResponse;
import com.moba.common.service.BattleService;
import com.moba.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BattleServiceImpl implements BattleService {
    private final BattleManager battleManager;

    public BattleServiceImpl(BattleManager battleManager) {
        this.battleManager = battleManager;
    }

    @Override
    public CreateBattleResponse createBattle(CreateBattleRequest request) {
        try {
            long battleId = request.getBattleId();
            if (battleId <= 0) {
                battleId = SnowflakeIdGenerator.getDefault().nextId();
            }

            BattleRoom room = battleManager.createBattle(
                    battleId,
                    request.getUserIds(),
                    request.getTeamCount(),
                    request.getNeededBots(),
                    request.getAiLevel(),
                    request.isAiMode()
            );

            if (room != null) {
                log.info("创建战斗: {}, 玩家: {}", battleId, request.getUserIds().size());
                return CreateBattleResponse.ok(battleId);
            } else {
                return CreateBattleResponse.fail("创建战斗房间失败");
            }
        } catch (Exception e) {
            log.error("创建战斗异常", e);
            return CreateBattleResponse.fail(e.getMessage());
        }
    }

    @Override
    public BattleResultDTO getBattleResult(long battleId) {
        BattleRoom room = battleManager.getBattleRoom(battleId);
        if (room == null || room.getSession() == null) {
            return null;
        }

        BattleResultDTO result = new BattleResultDTO();
        result.setBattleId(battleId);
        result.setGameMode(room.getGameMode() != null ? room.getGameMode() : GameMode.MODE_5V5);
        result.setStartTime(room.getSession().getStartTime());
        result.setEndTime(room.getSession().getEndTime());
        result.setDuration(room.getSession().getEndTime() - room.getSession().getStartTime());
        result.setWinnerTeamId(-1);
        return result;
    }

    @Override
    public boolean isBattleRunning(long battleId) {
        BattleRoom room = battleManager.getBattleRoom(battleId);
        return room != null && room.isRunning();
    }

    @Override
    public int getRoomCount() {
        return battleManager.getRoomCount();
    }

    @Override
    public int getTotalPlayers() {
        return battleManager.getTotalPlayers();
    }
}
