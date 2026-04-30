package com.moba.battleserver.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonProtocol {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static ObjectMapper getMapper() {
        return mapper;
    }

    public static byte[] toBytes(Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.error("Failed to serialize object", e);
            return new byte[0];
        }
    }

    public static <T> T fromBytes(byte[] data, Class<T> clazz) {
        try {
            return mapper.readValue(data, clazz);
        } catch (Exception e) {
            log.error("Failed to deserialize {}", clazz.getSimpleName(), e);
            return null;
        }
    }

    public static ObjectNode createObjectNode() {
        return mapper.createObjectNode();
    }
}
