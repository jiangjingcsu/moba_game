package com.moba.netty.session;

import com.moba.netty.connection.Connection;
import com.moba.netty.connection.NettyConnection;
import com.moba.netty.user.User;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SessionManager {

    private static final AttributeKey<Session> SESSION_KEY = AttributeKey.valueOf("session");

    private final Map<Long, Session> sessionMap = new ConcurrentHashMap<>();
    private final Map<Long, User> userMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> userToSessionMap = new ConcurrentHashMap<>();

    public Session createSession(Channel channel) {
        Connection connection = new NettyConnection(channel);
        Session session = new Session(connection);
        channel.attr(SESSION_KEY).set(session);
        sessionMap.put(session.getSessionId(), session);
        log.info("Session创建: sessionId={}, remote={}", session.getSessionId(), session.getIp());
        return session;
    }

    public User createUserAndBind(long userId, String username, Session session) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId必须大于0");
        }
        User existingUser = userMap.get(userId);
        if (existingUser != null) {
            log.warn("User已存在, 复用: userId={}, oldSessionId={}", userId,
                    existingUser.getSession() != null ? existingUser.getSession().getSessionId() : "null");
            if (existingUser.getSession() != null && existingUser.getSession() != session) {
                existingUser.getSession().markLost();
            }
            existingUser.bindSession(session);
            userToSessionMap.put(userId, session.getSessionId());
            return existingUser;
        }

        User user = new User(userId, username);
        user.bindSession(session);
        userMap.put(userId, user);
        userToSessionMap.put(userId, session.getSessionId());
        log.info("User创建并绑定: userId={}, username={}, sessionId={}", userId, username, session.getSessionId());
        return user;
    }

    public Session getSession(Channel channel) {
        return channel.attr(SESSION_KEY).get();
    }

    public Session getSession(long sessionId) {
        return sessionMap.get(sessionId);
    }

    public User getUser(long userId) {
        return userMap.get(userId);
    }

    public User getUserByChannel(Channel channel) {
        Session session = getSession(channel);
        if (session == null || !session.isBound()) {
            return null;
        }
        return userMap.get(session.getUserId());
    }

    public void removeSession(Channel channel) {
        Session session = getSession(channel);
        if (session == null) {
            return;
        }
        session.markLost();
        sessionMap.remove(session.getSessionId());

        if (session.isBound()) {
            long userId = session.getUserId();
            User user = userMap.get(userId);
            if (user != null) {
                user.disconnect();
                user.unbindSession();
            }
            userToSessionMap.remove(userId);
            log.info("Session移除, User断开: userId={}, sessionId={}", userId, session.getSessionId());
        } else {
            log.info("Session移除(未绑定User): sessionId={}", session.getSessionId());
        }

        channel.attr(SESSION_KEY).set(null);
    }

    public Collection<Session> getAllSessions() {
        return Collections.unmodifiableCollection(sessionMap.values());
    }

    public Collection<User> getAllUsers() {
        return Collections.unmodifiableCollection(userMap.values());
    }

    public int getSessionCount() {
        return sessionMap.size();
    }

    public int getUserCount() {
        return userMap.size();
    }
}
