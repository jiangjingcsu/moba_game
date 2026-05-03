package com.moba.netty.protocol.exception;

public class MessageDecodeException extends RuntimeException {

    private final short extensionId;
    private final byte cmdId;

    public MessageDecodeException(short extensionId, byte cmdId, String message, Throwable cause) {
        super(message, cause);
        this.extensionId = extensionId;
        this.cmdId = cmdId;
    }

    public MessageDecodeException(short extensionId, byte cmdId, String message) {
        super(message);
        this.extensionId = extensionId;
        this.cmdId = cmdId;
    }

    public short getExtensionId() { return extensionId; }
    public byte getCmdId() { return cmdId; }
}
