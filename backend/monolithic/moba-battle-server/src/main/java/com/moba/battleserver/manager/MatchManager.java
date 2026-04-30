package com.moba.battleserver.manager;

import com.moba.battleserver.ServiceLocator;
import com.moba.battleserver.model.MatchmakingPool;
import com.moba.battleserver.model.MatchmakingPool.MatchType;
import com.moba.battleserver.model.Player;
import com.moba.battleserver.protocol.MatchResponse;
import com.moba.battleserver.service.BattleCreator;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class MatchManager {
    private final PlayerManager playerManager;
    private final BattleCreator battleCreator;
    private final RoomManager roomManager;
    private final Map<Long, MatchmakingPool> playerPools;
    private final Map<String, MatchmakingPool> poolsById;
    private final ScheduledExecutorService scheduler;
    private final AtomicLong poolIdGenerator;
    private final long MATCH_TIMEOUT_MS;

    private boolean aiEnabled;
    private int aiLevel;
    private int botsPerTeam;

    public MatchManager(PlayerManager playerManager, BattleCreator battleCreator, RoomManager roomManager) {
        this.playerManager = playerManager;
        this.battleCreator = battleCreator;
        this.roomManager = roomManager;
        this.playerPools = new ConcurrentHashMap<>();
        this.poolsById = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.poolIdGenerator = new AtomicLong(1);
        this.MATCH_TIMEOUT_MS = 60000;
        this.aiEnabled = true;
        this.aiLevel = 5;
        this.botsPerTeam = 2;

        scheduler.scheduleAtFixedRate(this::checkMatchTimeouts, 5, 5, TimeUnit.SECONDS);
    }

    public MatchResponse joinMatch(ChannelHandlerContext ctx, com.moba.battleserver.protocol.MatchRequest request) {
        Optional<Player> playerOpt = playerManager.getPlayerByChannel(ctx);
        if (playerOpt.isEmpty()) {
            return MatchResponse.failure("Player not found");
        }

        Player player = playerOpt.get();
        if (player.isMatching()) {
            return MatchResponse.failure("Already in matchmaking");
        }
        if (player.isInBattle()) {
            return MatchResponse.failure("Already in battle");
        }

        long playerId = player.getPlayerId();
        MatchType matchType = MatchType.THREE_VS_THREE_VS_THREE;

        MatchmakingPool pool = findSuitablePool(matchType);
        if (pool == null) {
            String poolId = "POOL_" + poolIdGenerator.incrementAndGet();
            pool = new MatchmakingPool(poolId, matchType);
            poolsById.put(poolId, pool);
        }

        pool.addPlayer(playerId);
        playerPools.put(playerId, pool);
        player.setState(Player.PlayerState.MATCHING);

        log.info("Player {} joined matchmaking pool {} (current: {}/{})",
                playerId, pool.getPoolId(), pool.getWaitingCount(), pool.getRequiredPlayerCount());

        if (pool.isReady()) {
            startBattle(pool);
            return MatchResponse.success(pool.getPoolId().replace("POOL", "BATTLE"));
        }

        return MatchResponse.waiting(0);
    }

    public void cancelMatch(ChannelHandlerContext ctx) {
        Optional<Player> playerOpt = playerManager.getPlayerByChannel(ctx);
        if (playerOpt.isEmpty()) {
            return;
        }

        Player player = playerOpt.get();
        long playerId = player.getPlayerId();

        MatchmakingPool pool = playerPools.remove(playerId);
        if (pool != null) {
            pool.removePlayer(playerId);
            if (pool.getWaitingCount() == 0) {
                poolsById.remove(pool.getPoolId());
            }
            player.setState(Player.PlayerState.ONLINE);
            log.info("Player {} cancelled matchmaking", playerId);
        }
    }

    private MatchmakingPool findSuitablePool(MatchType matchType) {
        for (MatchmakingPool pool : poolsById.values()) {
            if (pool.getRequiredPlayerCount() == matchType.getPlayerCount()
                    && pool.getWaitingCount() < pool.getRequiredPlayerCount()) {
                return pool;
            }
        }
        return null;
    }

    private void startBattle(MatchmakingPool pool) {
        List<Long> players = new ArrayList<>(pool.getWaitingPlayers());
        String battleId = "BATTLE_" + System.currentTimeMillis();

        int humanCount = countHumans(players);
        int neededBots = 0;

        if (aiEnabled && humanCount < 9) {
            neededBots = Math.max(0, 9 - humanCount);
            neededBots = Math.min(neededBots, botsPerTeam * 3);
        }

        log.info("Starting battle {} with {} human players, {} AI bots needed",
                battleId, humanCount, neededBots);

        for (Long playerId : players) {
            playerPools.remove(playerId);
            Optional<Player> playerOpt = playerManager.getPlayerById(playerId);
            playerOpt.ifPresent(p -> {
                p.setState(Player.PlayerState.IN_BATTLE);
                p.setCurrentBattleId(Long.parseLong(battleId.replace("BATTLE_", "")));
            });
        }

        poolsById.remove(pool.getPoolId());

        BattleManager bm = ServiceLocator.getInstance().getBattleManager();
        bm.createBattle(battleId, players, 3, neededBots, aiLevel);
    }

    private int countHumans(List<Long> players) {
        int count = 0;
        for (Long playerId : players) {
            if (playerId > 0) {
                count++;
            }
        }
        return count;
    }

    private void checkMatchTimeouts() {
        List<String> expiredPools = new ArrayList<>();
        for (MatchmakingPool pool : poolsById.values()) {
            if (pool.isTimeout(MATCH_TIMEOUT_MS)) {
                expiredPools.add(pool.getPoolId());
            }
        }

        for (String poolId : expiredPools) {
            MatchmakingPool pool = poolsById.remove(poolId);
            if (pool != null) {
                if (pool.isFlexibleReady()) {
                    startBattle(pool);
                } else {
                    for (Long playerId : pool.getWaitingPlayers()) {
                        playerPools.remove(playerId);
                        Optional<Player> playerOpt = playerManager.getPlayerById(playerId);
                        playerOpt.ifPresent(p -> p.setState(Player.PlayerState.ONLINE));
                    }
                    log.info("Matchmaking pool {} expired with {} players, cancelled",
                            poolId, pool.getWaitingCount());
                }
            }
        }
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        cancelMatch(ctx);
    }
}
