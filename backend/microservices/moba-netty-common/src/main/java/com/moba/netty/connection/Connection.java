package com.moba.netty.connection;

import io.netty.util.AttributeKey;

import java.net.SocketAddress;

public interface Connection {

    <T> T attr(AttributeKey<T> key);

    <T> void setAttr(AttributeKey<T> key, T value);

    <T> boolean hasAttr(AttributeKey<T> key);

    SocketAddress localAddress();

    SocketAddress remoteAddress();

    void close();

    boolean isWritable();

    boolean isActive();

    void writeAndFlush(Object msg);

    long getConnectionId();
}
