package com.moba.netty.session;

import com.moba.netty.connection.Connection;
import com.moba.netty.protocol.MessagePacket;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class Session {

    private static final AtomicLong SESSION_ID_GENERATOR = new AtomicLong(0);

    private final long sessionId;
    private final long createTime;
    private final Connection connection;
    private volatile boolean lost;
    private volatile long userId;
    private final AtomicInteger curMsgSeqNo;
    private final AtomicInteger nextMsgSeqNo;
    private final String ip;

    public Session(Connection connection) {
        this.sessionId = SESSION_ID_GENERATOR.incrementAndGet();
        this.createTime = System.currentTimeMillis();
        this.connection = connection;
        this.lost = false;
        this.userId = 0;
        this.curMsgSeqNo = new AtomicInteger(0);
        this.nextMsgSeqNo = new AtomicInteger(1);
        this.ip = extractIp(connection);
    }

    private String extractIp(Connection connection) {
        SocketAddress remote = connection.remoteAddress();
        if (remote != null) {
            String addr = remote.toString();
            if (addr.startsWith("/")) {
                return addr.substring(1);
            }
            return addr;
        }
        return "unknown";
    }

    public void send(MessagePacket packet) {
        if (lost) {
            log.warn("Session已断开, 无法发送消息: sessionId={}, userId={}", sessionId, userId);
            return;
        }
        if (!connection.isActive()) {
            log.warn("Connection已断开, 无法发送消息: sessionId={}, userId={}", sessionId, userId);
            return;
        }
        connection.writeAndFlush(packet);
    }

    public void markLost() {
        this.lost = true;
    }

    public boolean isLost() {
        return lost;
    }

    public boolean isBound() {
        return userId > 0;
    }

    public void bindUser(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId必须大于0");
        }
        if (this.userId > 0 && this.userId != userId) {
            throw new IllegalStateException("Session已绑定userId=" + this.userId + ", 不能重复绑定为" + userId);
        }
        this.userId = userId;
    }

    public void unbindUser() {
        this.userId = 0;
    }

    public int getAndIncrementMsgSeqNo() {
        curMsgSeqNo.set(nextMsgSeqNo.get());
        return nextMsgSeqNo.getAndIncrement();
    }

    public void close() {
        markLost();
        connection.close();
    }

    public long getSessionId() { return sessionId; }
    public long getCreateTime() { return createTime; }
    public Connection getConnection() { return connection; }
    public long getUserId() { return userId; }
    public String getIp() { return ip; }
    public int getCurMsgSeqNo() { return curMsgSeqNo.get(); }
    public boolean isActive() { return !lost && connection.isActive(); }

    @Override
    public String toString() {
        return "Session{id=" + sessionId +
                ", userId=" + userId +
                ", ip=" + ip +
                ", lost=" + lost +
                ", createTime=" + createTime + "}";
    }
}
