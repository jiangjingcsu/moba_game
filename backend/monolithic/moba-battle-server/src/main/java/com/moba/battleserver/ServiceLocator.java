package com.moba.battleserver;

import com.moba.battleserver.anticheat.AntiCheatValidator;
import com.moba.battleserver.ai.AIController;
import com.moba.battleserver.battle.GridCollisionDetector;
import com.moba.battleserver.battle.SkillCollisionSystem;
import com.moba.battleserver.config.ServerConfig;
import com.moba.battleserver.manager.*;
import com.moba.battleserver.monitor.ServerMonitor;
import com.moba.battleserver.replay.ReplaySystem;
import com.moba.battleserver.service.BattleCreator;
import com.moba.battleserver.service.BattleEndHandler;
import com.moba.battleserver.service.BattleInputHandler;
import com.moba.battleserver.service.BattleReconnectHandler;
import com.moba.battleserver.service.BattleStateBroadcaster;
import com.moba.battleserver.service.MessageDispatchService;
import com.moba.battleserver.storage.BattleLogStorage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ServiceLocator {

    private static ServiceLocator instance;

    private final ServerConfig serverConfig;
    private final PlayerManager playerManager;
    private final BattleLogStorage battleLogStorage;
    private final ServerMonitor serverMonitor;
    private final AntiCheatValidator antiCheatValidator;
    private final ReplaySystem replaySystem;
    private final GridCollisionDetector collisionDetector;
    private final AIController aiController;
    private final MapManager mapManager;
    private final SpectatorManager spectatorManager;
    private final SettlementSystem settlementSystem;
    private final ReconnectManager reconnectManager;
    private final MatchManager matchManager;
    private final RoomManager roomManager;
    private final BattleCreator battleCreator;
    private final BattleInputHandler battleInputHandler;
    private final BattleStateBroadcaster battleStateBroadcaster;
    private final BattleReconnectHandler battleReconnectHandler;
    private final BattleEndHandler battleEndHandler;
    private final BattleManager battleManager;
    private final MessageDispatchService messageDispatchService;

    private ServiceLocator(ServerConfig config) {
        this.serverConfig = config;

        this.battleLogStorage = new BattleLogStorage();
        this.serverMonitor = ServerMonitor.getInstance();
        this.antiCheatValidator = AntiCheatValidator.getInstance();
        this.replaySystem = new ReplaySystem();
        this.collisionDetector = new GridCollisionDetector(16000, 16000, 200);
        this.aiController = AIController.getInstance();

        this.playerManager = new PlayerManager();
        this.mapManager = new MapManager();
        this.spectatorManager = new SpectatorManager();
        this.settlementSystem = new SettlementSystem();
        this.reconnectManager = new ReconnectManager(playerManager);

        this.battleCreator = new BattleCreator(playerManager, mapManager, aiController, replaySystem, collisionDetector);
        this.battleInputHandler = new BattleInputHandler(antiCheatValidator);
        this.battleStateBroadcaster = new BattleStateBroadcaster(playerManager);
        this.battleReconnectHandler = new BattleReconnectHandler(playerManager, mapManager);
        this.battleEndHandler = new BattleEndHandler(playerManager, settlementSystem, replaySystem, battleLogStorage, spectatorManager);

        this.roomManager = new RoomManager(battleStateBroadcaster, mapManager, serverMonitor);
        this.matchManager = new MatchManager(playerManager, battleCreator, roomManager);
        this.battleManager = new BattleManager(playerManager, roomManager, mapManager, reconnectManager,
                battleCreator, battleInputHandler, battleStateBroadcaster, battleReconnectHandler, battleEndHandler);

        this.messageDispatchService = new MessageDispatchService(playerManager, matchManager, battleManager);

        log.info("ServiceLocator initialized with all services");
    }

    public static synchronized void initialize(ServerConfig config) {
        if (instance != null) {
            throw new IllegalStateException("ServiceLocator already initialized");
        }
        instance = new ServiceLocator(config);
    }

    public static ServiceLocator getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ServiceLocator not initialized. Call initialize() first.");
        }
        return instance;
    }

    public ServerConfig getServerConfig() { return serverConfig; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public BattleLogStorage getBattleLogStorage() { return battleLogStorage; }
    public ServerMonitor getServerMonitor() { return serverMonitor; }
    public AntiCheatValidator getAntiCheatValidator() { return antiCheatValidator; }
    public ReplaySystem getReplaySystem() { return replaySystem; }
    public GridCollisionDetector getCollisionDetector() { return collisionDetector; }
    public AIController getAiController() { return aiController; }
    public MapManager getMapManager() { return mapManager; }
    public SpectatorManager getSpectatorManager() { return spectatorManager; }
    public SettlementSystem getSettlementSystem() { return settlementSystem; }
    public ReconnectManager getReconnectManager() { return reconnectManager; }
    public MatchManager getMatchManager() { return matchManager; }
    public RoomManager getRoomManager() { return roomManager; }
    public BattleCreator getBattleCreator() { return battleCreator; }
    public BattleInputHandler getBattleInputHandler() { return battleInputHandler; }
    public BattleStateBroadcaster getBattleStateBroadcaster() { return battleStateBroadcaster; }
    public BattleReconnectHandler getBattleReconnectHandler() { return battleReconnectHandler; }
    public BattleEndHandler getBattleEndHandler() { return battleEndHandler; }
    public BattleManager getBattleManager() { return battleManager; }
    public MessageDispatchService getMessageDispatchService() { return messageDispatchService; }
}
