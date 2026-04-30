package com.moba.battle.manager;
import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.model.FrameInput;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;

import com.moba.battle.model.Player;
import com.moba.battle.storage.BattleLogStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ReconnectManager {
    private final Map<Long, Long> reconnectTimers;
    private static final long RECONNECT_TIMEOUT_MS = 30000;
    private final ScheduledExecutorService scheduler;

    public ReconnectManager() {
        this.reconnectTimers = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(this::checkTimeouts, 5, 5, TimeUnit.SECONDS);
    }

    public static ReconnectManager getInstance() {
        return SpringContextHolder.getBean(ReconnectManager.class);
    }

    public void startReconnectTimer(long playerId) {
        reconnectTimers.put(playerId, System.currentTimeMillis());
        log.info("Reconnect timer started for player {}, timeout={}s", playerId, RECONNECT_TIMEOUT_MS / 1000);
    }

    public void cancelReconnectTimer(long playerId) {
        reconnectTimers.remove(playerId);
        Player player = PlayerManager.getInstance().getPlayerById(playerId).orElse(null);
        if (player != null) {
            player.setReconnecting(false);
        }
        log.info("Reconnect timer cancelled for player {}", playerId);
    }

    public boolean isReconnectValid(long playerId) {
        Long startTime = reconnectTimers.get(playerId);
        if (startTime == null) return false;
        return System.currentTimeMillis() - startTime < RECONNECT_TIMEOUT_MS;
    }

    public long getRemainingTime(long playerId) {
        Long startTime = reconnectTimers.get(playerId);
        if (startTime == null) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, RECONNECT_TIMEOUT_MS - elapsed);
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Long> entry : new ConcurrentHashMap<>(reconnectTimers).entrySet()) {
            long playerId = entry.getKey();
            long startTime = entry.getValue();

            if (now - startTime >= RECONNECT_TIMEOUT_MS) {
                reconnectTimers.remove(playerId);
                handleReconnectTimeout(playerId);
            }
        }
    }

    private void handleReconnectTimeout(long playerId) {
        Player player = PlayerManager.getInstance().getPlayerById(playerId).orElse(null);
        if (player == null) return;

        player.setReconnecting(false);
        BattleRoom room = RoomManager.getInstance().getPlayerRoom(playerId);
        if (room != null) {
            log.warn("Player {} reconnect timeout, leaving battle {}", playerId, room.getBattleId());

            BattleSession session = room.getSession();
            BattlePlayer battlePlayer = session.getPlayer(playerId);
            if (battlePlayer != null) {
                battlePlayer.setDead(true);
                battlePlayer.setCurrentHp(0);
            }

            room.getEngine().submitInput(createLeaveInput(playerId));

            BattleLogStorage.getInstance().submitBattleEvent(
                    room.getBattleId(),
                    "RECONNECT_TIMEOUT",
                    "player=" + playerId
            );
        }

        player.setState(Player.PlayerState.ONLINE);
        player.setCurrentBattleId(0);
    }

    private FrameInput createLeaveInput(long playerId) {
        FrameInput input = new FrameInput();
        input.setPlayerId(playerId);
        input.setType(FrameInput.InputType.MOVE);
        input.setData(("LEAVE|" + playerId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return input;
    }
}

