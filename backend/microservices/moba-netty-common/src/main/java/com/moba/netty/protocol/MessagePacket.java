package com.moba.netty.protocol;

import io.netty.util.AttributeKey;
import lombok.Data;

import java.nio.charset.StandardCharsets;

@Data
public class MessagePacket {

    public static final AttributeKey<Long> ROUTING_KEY_ATTR = AttributeKey.valueOf("routingKey");

    private short extensionId;
    private byte cmdId;
    private int sequenceId;
    private String data;
    private long userId;

    public MessagePacket() {
    }

    public MessagePacket(short extensionId, byte cmdId, String data) {
        this.extensionId = extensionId;
        this.cmdId = cmdId;
        this.data = data;
    }

    public MessagePacket(short extensionId, byte cmdId, int sequenceId, String data) {
        this.extensionId = extensionId;
        this.cmdId = cmdId;
        this.sequenceId = sequenceId;
        this.data = data;
    }

    public byte[] encodeData() {
        if (data == null || data.isEmpty()) {
            return new byte[0];
        }
        return data.getBytes(StandardCharsets.UTF_8);
    }

    public static MessagePacket of(short extensionId, byte cmdId, String data) {
        return new MessagePacket(extensionId, cmdId, data);
    }

    public static MessagePacket of(short extensionId, byte cmdId, int sequenceId, String data) {
        return new MessagePacket(extensionId, cmdId, sequenceId, data);
    }

    public static MessagePacket response(MessagePacket request, String data) {
        MessagePacket response = new MessagePacket(request.getExtensionId(), request.getCmdId(), data);
        response.setSequenceId(request.getSequenceId());
        return response;
    }

    public static int routeKey(short extensionId, byte cmdId) {
        return (extensionId << 8) | (cmdId & 0xFF);
    }

    public int routeKey() {
        return routeKey(extensionId, cmdId);
    }

    public void bindUserIdFromChannel(io.netty.channel.Channel channel) {
        Object attr = channel.attr(AttributeKey.valueOf("userId")).get();
        if (attr instanceof Long) {
            this.userId = (Long) attr;
        } else if (attr instanceof Integer) {
            this.userId = ((Integer) attr).longValue();
        }
    }

    @Override
    public String toString() {
        return "MessagePacket{extId=" + extensionId + ", cmdId=" + cmdId
                + ", seq=" + sequenceId + ", userId=" + userId
                + ", dataLen=" + (data != null ? data.length() : 0) + "}";
    }
}
