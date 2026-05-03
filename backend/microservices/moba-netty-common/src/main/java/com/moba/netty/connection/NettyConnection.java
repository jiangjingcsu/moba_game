package com.moba.netty.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class NettyConnection implements Connection {

    private static final AtomicLong CONNECTION_ID_GENERATOR = new AtomicLong(0);

    private final long connectionId;
    private final Channel channel;

    public NettyConnection(Channel channel) {
        this.connectionId = CONNECTION_ID_GENERATOR.incrementAndGet();
        this.channel = channel;
    }

    @Override
    public <T> T attr(AttributeKey<T> key) {
        return channel.attr(key).get();
    }

    @Override
    public <T> void setAttr(AttributeKey<T> key, T value) {
        channel.attr(key).set(value);
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        return channel.attr(key).get() != null;
    }

    @Override
    public SocketAddress localAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public void close() {
        channel.close();
    }

    @Override
    public boolean isWritable() {
        return channel.isWritable();
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public void writeAndFlush(Object msg) {
        channel.writeAndFlush(msg);
    }

    @Override
    public long getConnectionId() {
        return connectionId;
    }

    public Channel getChannel() {
        return channel;
    }

    @Override
    public String toString() {
        return "NettyConnection{id=" + connectionId +
                ", remote=" + (channel.remoteAddress() != null ? channel.remoteAddress() : "unknown") +
                ", active=" + channel.isActive() + "}";
    }
}
