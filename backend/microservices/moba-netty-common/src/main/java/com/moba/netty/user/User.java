package com.moba.netty.user;

import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.session.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class User {

    private final long userId;
    private final String username;
    private Session session;
    private volatile boolean online;

    public User(long userId, String username) {
        this.userId = userId;
        this.username = username;
        this.online = true;
    }

    public void bindSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("session不能为null");
        }
        if (this.session != null && this.session != session) {
            log.warn("User已绑定其他Session: userId={}, oldSessionId={}, newSessionId={}",
                    userId, this.session.getSessionId(), session.getSessionId());
        }
        this.session = session;
        session.bindUser(userId);
    }

    public void unbindSession() {
        if (this.session != null) {
            this.session.unbindUser();
        }
        this.session = null;
    }

    public void send(MessagePacket packet) {
        if (session == null) {
            log.warn("User没有绑定Session, 无法发送消息: userId={}", userId);
            return;
        }
        session.send(packet);
    }

    public void disconnect() {
        online = false;
        if (session != null) {
            session.markLost();
        }
    }

    public boolean isActive() {
        return online && session != null && session.isActive();
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public Session getSession() { return session; }
    public boolean isOnline() { return online; }

    @Override
    public String toString() {
        return "User{id=" + userId +
                ", name=" + username +
                ", online=" + online +
                ", sessionId=" + (session != null ? session.getSessionId() : "null") + "}";
    }
}
