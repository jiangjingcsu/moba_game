package com.moba.match.network;

import com.moba.netty.protocol.dispatcher.ConnectionLifecycleListener;
import com.moba.netty.session.Session;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MatchChannelEventHandler implements ConnectionLifecycleListener {

    @Override
    public void onSessionActive(Session session) {
        if (session.isBound()) {
            log.info("匹配服务器: 玩家连接已认证, userId={}, sessionId={}", session.getUserId(), session.getSessionId());
        }
    }

    @Override
    public void onSessionInactive(Session session) {
        if (session.isBound()) {
            log.info("匹配服务器: 玩家断开连接, userId={}, sessionId={}", session.getUserId(), session.getSessionId());
        } else {
            log.info("匹配服务器: 未认证连接断开, sessionId={}", session.getSessionId());
        }
    }
}
