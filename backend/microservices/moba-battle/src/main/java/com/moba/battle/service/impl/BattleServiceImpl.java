package com.moba.battle.service.impl;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.BattleRoom;
import com.moba.common.dto.BattleResultDTO;
import com.moba.common.dto.CreateBattleRequest;
import com.moba.common.dto.CreateBattleResponse;
import com.moba.common.service.BattleService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.UUID;

@Slf4j
@DubboService(parameters = {"serialization", "hessian2"})
public class BattleServiceImpl implements BattleService {

    @Override
    public CreateBattleResponse createBattle(CreateBattleRequest request) {
        try {
            String battleId = request.getBattleId();
            if (battleId == null || battleId.isEmpty()) {
                battleId = "BATTLE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            }

            BattleRoom room = BattleManager.getInstance().createBattle(
                    battleId,
                    request.getPlayerIds(),
                    request.getTeamCount(),
                    request.getNeededBots(),
                    request.getAiLevel()
            );

            if (room != null) {
                log.info("Battle created via Dubbo: {}, players: {}", battleId, request.getPlayerIds().size());
                return CreateBattleResponse.ok(battleId);
            } else {
                return CreateBattleResponse.fail("Failed to create battle room");
            }
        } catch (Exception e) {
            log.error("Error creating battle", e);
            return CreateBattleResponse.fail(e.getMessage());
        }
    }

    @Override
    public BattleResultDTO getBattleResult(String battleId) {
        BattleRoom room = BattleManager.getInstance().getBattleRoom(battleId);
        if (room == null || room.getSession() == null) {
            return null;
        }

        BattleResultDTO result = new BattleResultDTO();
        result.setBattleId(battleId);
        result.setGameMode(room.getGameMode() != null ? room.getGameMode().ordinal() : 0);
        result.setStartTime(room.getSession().getStartTime());
        result.setEndTime(room.getSession().getEndTime());
        result.setDuration(room.getSession().getEndTime() - room.getSession().getStartTime());
        result.setWinnerTeamId(-1);
        return result;
    }

    @Override
    public boolean isBattleRunning(String battleId) {
        BattleRoom room = BattleManager.getInstance().getBattleRoom(battleId);
        return room != null && room.isRunning();
    }

    @Override
    public int getRoomCount() {
        return BattleManager.getInstance().getRoomCount();
    }

    @Override
    public int getTotalPlayers() {
        return BattleManager.getInstance().getTotalPlayers();
    }
}
