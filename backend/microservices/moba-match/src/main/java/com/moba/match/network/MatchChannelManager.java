package com.moba.match.network;

import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.session.Session;
import com.moba.netty.session.SessionManager;
import com.moba.netty.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class MatchChannelManager {

    private final SessionManager sessionManager;

    public MatchChannelManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void pushToPlayer(long userId, MessagePacket packet) {
        User user = sessionManager.getUser(userId);
        if (user != null && user.isActive()) {
            user.send(packet);
        } else {
            log.warn("玩家 {} 不在线, 推送失败", userId);
        }
    }

    public void pushToPlayers(List<Long> userIds, MessagePacket packet) {
        int success = 0;
        for (long userId : userIds) {
            User user = sessionManager.getUser(userId);
            if (user != null && user.isActive()) {
                user.send(packet);
                success++;
            } else {
                log.warn("玩家 {} 不在线, 推送跳过", userId);
            }
        }
        log.info("推送完成: 成功={}/{}", success, userIds.size());
    }

    public boolean isOnline(long userId) {
        User user = sessionManager.getUser(userId);
        return user != null && user.isActive();
    }

    public int getOnlineCount() {
        return sessionManager.getUserCount();
    }
}
