package com.moba.netty.protocol.exception;

public class MessageHandlerException extends RuntimeException {

    private final String errorCode;

    public MessageHandlerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MessageHandlerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
