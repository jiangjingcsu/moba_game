package com.moba.battle.controller;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.BattleRoom;
import com.moba.common.dto.BattleResultDTO;
import com.moba.common.dto.CreateBattleRequest;
import com.moba.common.dto.CreateBattleResponse;
import com.moba.common.protocol.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/internal/battle")
public class BattleInternalController {

    @PostMapping("/create")
    public ApiResponse<CreateBattleResponse> createBattle(@RequestBody CreateBattleRequest request) {
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
                log.info("Battle created via REST: {}, players: {}", battleId, request.getPlayerIds().size());
                return ApiResponse.success(CreateBattleResponse.ok(battleId));
            } else {
                return ApiResponse.success(CreateBattleResponse.fail("Failed to create battle room"));
            }
        } catch (Exception e) {
            log.error("Error creating battle via REST", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/result/{battleId}")
    public ApiResponse<BattleResultDTO> getBattleResult(@PathVariable("battleId") String battleId) {
        try {
            BattleRoom room = BattleManager.getInstance().getBattleRoom(battleId);
            if (room == null || room.getSession() == null) {
                return ApiResponse.success(null);
            }

            BattleResultDTO result = new BattleResultDTO();
            result.setBattleId(battleId);
            result.setGameMode(room.getGameMode() != null ? room.getGameMode().ordinal() : 0);
            result.setStartTime(room.getSession().getStartTime());
            result.setEndTime(room.getSession().getEndTime());
            result.setDuration(room.getSession().getEndTime() - room.getSession().getStartTime());
            result.setWinnerTeamId(-1);
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("Error getting battle result via REST", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/running/{battleId}")
    public ApiResponse<Boolean> isBattleRunning(@PathVariable("battleId") String battleId) {
        try {
            BattleRoom room = BattleManager.getInstance().getBattleRoom(battleId);
            return ApiResponse.success(room != null && room.isRunning());
        } catch (Exception e) {
            log.error("Error checking battle running via REST", e);
            return ApiResponse.error(500, e.getMessage());
        }
    }

    @GetMapping("/roomCount")
    public ApiResponse<Integer> getRoomCount() {
        return ApiResponse.success(BattleManager.getInstance().getRoomCount());
    }

    @GetMapping("/totalPlayers")
    public ApiResponse<Integer> getTotalPlayers() {
        return ApiResponse.success(BattleManager.getInstance().getTotalPlayers());
    }
}
