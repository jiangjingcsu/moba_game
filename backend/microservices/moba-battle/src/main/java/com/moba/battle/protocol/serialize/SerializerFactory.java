package com.moba.battle.protocol.serialize;

import com.moba.battle.protocol.core.SerializeType;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SerializerFactory {

    private static final Map<SerializeType, MessageSerializer> serializers = new ConcurrentHashMap<>();

    static {
        register(SerializeType.JSON, new JsonMessageSerializer());
    }

    public static void register(SerializeType type, MessageSerializer serializer) {
        serializers.put(type, serializer);
        log.info("Registered serializer: {} -> {}", type, serializer.getClass().getSimpleName());
    }

    public static MessageSerializer getSerializer(SerializeType type) {
        MessageSerializer serializer = serializers.get(type);
        if (serializer == null) {
            log.warn("No serializer for type: {}, falling back to JSON", type);
            serializer = serializers.get(SerializeType.JSON);
        }
        return serializer;
    }

    public static MessageSerializer json() {
        return serializers.get(SerializeType.JSON);
    }
}
