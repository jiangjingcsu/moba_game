package com.moba.battle.protocol.serialize;

public interface MessageSerializer {
    <T> byte[] serialize(T obj) throws Exception;
    <T> T deserialize(byte[] data, Class<T> clazz) throws Exception;
    String contentType();
}
