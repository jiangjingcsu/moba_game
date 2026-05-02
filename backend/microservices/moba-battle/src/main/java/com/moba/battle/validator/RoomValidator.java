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

    private final JedisPool jedisPool;
    private final ServerConfig serverConfig;
    private final int roomKeyTtlSeconds;

    public RoomValidator(ServerConfig serverConfig) {
        this.serverConfig = serverConfig;
        this.roomKeyTtlSeconds = serverConfig.getRoomKeyTtlSeconds();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(serverConfig.getRedisPoolMaxTotal());
        poolConfig.setMaxIdle(serverConfig.getRedisPoolMaxIdle());
        poolConfig.setMinIdle(serverConfig.getRedisPoolMinIdle());
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
                serverConfig.getRedisTimeout(),
                password,
                serverConfig.getRedisDatabase()
        );

        log.info("RoomValidator已初始化, Redis: {}:{}/{}", serverConfig.getRedisHost(), serverConfig.getRedisPort(), serverConfig.getRedisDatabase());
    }

    public RoomValidationResult validate(long playerId, String roomId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String assignedRoomId = jedis.get(PLAYER_ROOM_KEY_PREFIX + playerId);
            if (assignedRoomId == null) {
                log.warn("未找到玩家房间分配: {}", playerId);
                return RoomValidationResult.fail("NO_ROOM_ASSIGNMENT", "玩家未被分配到任何房间");
            }

            if (!assignedRoomId.equals(roomId)) {
                log.warn("玩家{}房间不匹配: 期望={}, 实际={}", playerId, assignedRoomId, roomId);
                return RoomValidationResult.fail("ROOM_MISMATCH", "房间号不匹配，分配的房间为: " + assignedRoomId);
            }

            String roomData = jedis.get(ROOM_KEY_PREFIX + roomId);
            if (roomData == null) {
                log.warn("Redis中未找到房间: {}", roomId);
                return RoomValidationResult.fail("ROOM_NOT_FOUND", "房间不存在或已过期");
            }

            String battleServerAddr = jedis.get(ROOM_KEY_PREFIX + roomId + ":server");
            if (battleServerAddr != null) {
                String currentServer = serverConfig.getHost() + ":" + serverConfig.getPort();
                if (!battleServerAddr.equals(currentServer)) {
                    log.warn("玩家{}连接到错误的服务器: 期望={}, 当前={}", playerId, battleServerAddr, currentServer);
                    return RoomValidationResult.fail("WRONG_SERVER", "请连接正确的战斗服务器: " + battleServerAddr);
                }
            }

            log.info("房间验证通过: player={}, room={}", playerId, roomId);
            return RoomValidationResult.success(roomId, roomData);

        } catch (Exception e) {
            log.error("房间验证Redis异常: player={}, room={}", playerId, roomId, e);
            return RoomValidationResult.fail("REDIS_ERROR", "服务器内部错误，请稍后重试");
        }
    }

    public boolean markPlayerEntered(long playerId, String roomId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(PLAYER_ROOM_KEY_PREFIX + playerId);
            jedis.sadd(ROOM_KEY_PREFIX + roomId + ":players", String.valueOf(playerId));
            return true;
        } catch (Exception e) {
            log.error("标记玩家进入Redis异常: player={}, room={}", playerId, roomId, e);
            return false;
        }
    }

    public boolean isPlayerInRoom(long playerId, String roomId) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(ROOM_KEY_PREFIX + roomId + ":players", String.valueOf(playerId));
        } catch (Exception e) {
            log.error("检查玩家在房间中Redis异常", e);
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
