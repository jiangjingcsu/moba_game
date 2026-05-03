package com.moba.battle.network;

import com.moba.battle.manager.BattleManager;
import com.moba.battle.manager.PlayerManager;
import com.moba.netty.protocol.dispatcher.ConnectionLifecycleListener;
import com.moba.netty.session.Session;
import com.moba.netty.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BattleConnectionListener implements ConnectionLifecycleListener {

    private final PlayerManager playerManager;
    private final BattleManager battleManager;

    public BattleConnectionListener(PlayerManager playerManager, BattleManager battleManager) {
        this.playerManager = playerManager;
        this.battleManager = battleManager;
    }

    @Override
    public void onSessionActive(Session session) {
        if (session.isBound()) {
            log.info("玩家连接已认证: userId={}, sessionId={}", session.getUserId(), session.getSessionId());
        } else {
            log.warn("未认证连接: sessionId={}", session.getSessionId());
        }
    }

    @Override
    public void onSessionInactive(Session session) {
        long userId = session.getUserId();
        if (userId > 0) {
            log.info("玩家断开连接: userId={}, sessionId={}", userId, session.getSessionId());
            playerManager.handleDisconnect(userId);
            battleManager.handleDisconnect(userId);
        } else {
            log.info("未认证连接断开: sessionId={}", session.getSessionId());
        }
    }
}
