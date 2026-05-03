package com.moba.battle.manager;

import com.moba.battle.config.ServerConfig;
import com.moba.battle.model.FrameInput;
import com.moba.battle.model.BattlePlayer;
import com.moba.battle.model.BattleSession;
import com.moba.battle.model.Player;
import com.moba.battle.storage.BattleLogStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ReconnectManager implements DisposableBean {
    private final Map<Long, Long> reconnectTimers;
    private final long reconnectTimeoutMs;
    private final ScheduledExecutorService scheduler;

    private final PlayerManager playerManager;
    private final RoomManager roomManager;
    private final BattleLogStorage battleLogStorage;

    public ReconnectManager(ServerConfig serverConfig,
                            PlayerManager playerManager,
                            RoomManager roomManager,
                            BattleLogStorage battleLogStorage) {
        this.reconnectTimers = new ConcurrentHashMap<>();
        this.reconnectTimeoutMs = serverConfig.getReconnectTimeoutSeconds() * 1000L;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.playerManager = playerManager;
        this.roomManager = roomManager;
        this.battleLogStorage = battleLogStorage;

        scheduler.scheduleAtFixedRate(this::checkTimeouts, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void destroy() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void startReconnectTimer(long userId) {
        reconnectTimers.put(userId, System.currentTimeMillis());
        log.info("玩家{}重连计时器已启动, 超时={}秒", userId, reconnectTimeoutMs / 1000);
    }

    public void cancelReconnectTimer(long userId) {
        reconnectTimers.remove(userId);
        Player player = playerManager.getPlayerById(userId).orElse(null);
        if (player != null) {
            player.setReconnecting(false);
        }
        log.info("玩家{}重连计时器已取消", userId);
    }

    public boolean isReconnectValid(long userId) {
        Long startTime = reconnectTimers.get(userId);
        if (startTime == null) return false;
        return System.currentTimeMillis() - startTime < reconnectTimeoutMs;
    }

    public long getRemainingTime(long userId) {
        Long startTime = reconnectTimers.get(userId);
        if (startTime == null) return 0;
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, reconnectTimeoutMs - elapsed);
    }

    private void checkTimeouts() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Long> entry : new ConcurrentHashMap<>(reconnectTimers).entrySet()) {
            long userId = entry.getKey();
            long startTime = entry.getValue();

            if (now - startTime >= reconnectTimeoutMs) {
                reconnectTimers.remove(userId);
                handleReconnectTimeout(userId);
            }
        }
    }

    private void handleReconnectTimeout(long userId) {
        Player player = playerManager.getPlayerById(userId).orElse(null);
        if (player == null) return;

        player.setReconnecting(false);
        BattleRoom room = roomManager.getPlayerRoom(userId);
        if (room != null) {
            log.warn("玩家{}重连超时, 离开战斗{}", userId, room.getBattleId());

            BattleSession session = room.getSession();
            BattlePlayer battlePlayer = session.getPlayer(userId);
            if (battlePlayer != null) {
                battlePlayer.setDead(true);
                battlePlayer.setCurrentHp(0);
            }

            room.getEngine().submitInput(createLeaveInput(userId));

            battleLogStorage.submitBattleEvent(
                    room.getBattleId(),
                    "RECONNECT_TIMEOUT",
                    "user=" + userId
            );
        }

        player.setState(Player.PlayerState.ONLINE);
        player.setCurrentBattleId(0);
    }

    private FrameInput createLeaveInput(long userId) {
        FrameInput input = new FrameInput();
        input.setUserId(userId);
        input.setType(FrameInput.InputType.MOVE);
        input.setData(("LEAVE|" + userId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return input;
    }
}
