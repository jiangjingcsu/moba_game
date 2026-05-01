package com.moba.battle.validator;

import com.moba.battle.config.ServerConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Slf4j
@Component
public class RoomValidator {

    private static final String ROOM_KEY_PREFIX = "moba:battle:room:";
    private static final String PLAYER_ROOM_KEY_PREFIX = "moba:battle:player_room:";
    private static final int ROOM_KEY_TTL_SECONDS = 3600;

    private final JedisPool jedisPool;

    public RoomValidator(ServerConfig serverConfig) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        String password = serverConfig.getRedisPassword();
        if (password != null && password.isEmpty()) {
            password = null;
        }

        this.jedisPool = new JedisPool(
                poolConfig,
                serverConfig.getRedisHost(),
                serverConfig.getRedisPort(),
                3000,
                password,
                serverConfig.getRedisDatabase()
        );

        log.info("RoomValidator initialized, Redis: {}:{}/{}", serverConfig.getRedisHost(), serverConfig.getRedisPort(), serverConfig.getRedisDatabase());
    }

    public RoomValidationResult validate(long playerId, String roomId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String assignedRoomId = jedis.get(PLAYER_ROOM_KEY_PREFIX + playerId);
            if (assignedRoomId == null) {
                log.warn("No room assignment found for player: {}", playerId);
                return RoomValidationResult.fail("NO_ROOM_ASSIGNMENT", "玩家未被分配到任何房间");
            }

            if (!assignedRoomId.equals(roomId)) {
                log.warn("Room mismatch for player {}: expected={}, got={}", playerId, assignedRoomId, roomId);
                return RoomValidationResult.fail("ROOM_MISMATCH", "房间号不匹配，分配的房间为: " + assignedRoomId);
            }

            String roomData = jedis.get(ROOM_KEY_PREFIX + roomId);
            if (roomData == null) {
                log.warn("Room not found in Redis: {}", roomId);
                return RoomValidationResult.fail("ROOM_NOT_FOUND", "房间不存在或已过期");
            }

            String battleServerAddr = jedis.get(ROOM_KEY_PREFIX + roomId + ":server");
            if (battleServerAddr != null) {
                String currentServer = serverConfig.getHost() + ":" + serverConfig.getPort();
                if (!battleServerAddr.equals(currentServer)) {
                    log.warn("Player {} connected to wrong server: expected={}, current={}", playerId, battleServerAddr, currentServer);
                    return RoomValidationResult.fail("WRONG_SERVER", "请连接正确的战斗服务器: " + battleServerAddr);
                }
            }

            log.info("Room validation passed: player={}, room={}", playerId, roomId);
            return RoomValidationResult.success(roomId, roomData);

        } catch (Exception e) {
            log.error("Redis error during room validation: player={}, room={}", playerId, roomId, e);
            return RoomValidationResult.fail("REDIS_ERROR", "服务器内部错误，请稍后重试");
        }
    }

    public boolean markPlayerEntered(long playerId, String roomId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(PLAYER_ROOM_KEY_PREFIX + playerId);
            jedis.sadd(ROOM_KEY_PREFIX + roomId + ":players", String.valueOf(playerId));
            return true;
        } catch (Exception e) {
            log.error("Redis error marking player entered: player={}, room={}", playerId, roomId, e);
            return false;
        }
    }

    public boolean isPlayerInRoom(long playerId, String roomId) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(ROOM_KEY_PREFIX + roomId + ":players", String.valueOf(playerId));
        } catch (Exception e) {
            log.error("Redis error checking player in room", e);
            return false;
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor(staticName = "of")
    @lombok.NoArgsConstructor
    public static class RoomValidationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;
        private String roomId;
        private String roomData;

        public static RoomValidationResult success(String roomId, String roomData) {
            RoomValidationResult r = new RoomValidationResult();
            r.setValid(true);
            r.setRoomId(roomId);
            r.setRoomData(roomData);
            return r;
        }

        public static RoomValidationResult fail(String errorCode, String errorMessage) {
            RoomValidationResult r = new RoomValidationResult();
            r.setValid(false);
            r.setErrorCode(errorCode);
            r.setErrorMessage(errorMessage);
            return r;
        }
    }
}
