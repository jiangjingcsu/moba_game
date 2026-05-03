package com.moba.match.repository;

import com.moba.common.constant.GameMode;
import com.moba.match.model.MatchRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
public class MatchRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${match.redisKeyExpireMinutes}")
    private long keyExpireMinutes;

    public MatchRedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void bindPlayerToMatch(long userId, long matchId) {
        redisTemplate.opsForValue().set(
                MatchRedisKeys.playerMatch(userId), matchId,
                keyExpireMinutes, TimeUnit.MINUTES);
    }

    public Long getMatchIdByPlayer(long userId) {
        Object val = redisTemplate.opsForValue().get(MatchRedisKeys.playerMatch(userId));
        if (val == null) return null;
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            log.warn("解析playerMatch的matchId失败: userId={}, val={}", userId, val);
            return null;
        }
    }

    public void unbindPlayerMatch(long userId) {
        redisTemplate.delete(MatchRedisKeys.playerMatch(userId));
    }

    public void saveMatchResult(long userId, long matchId, long battleId,
                                String battleServerIp, int battleServerPort,
                                GameMode gameMode, int teamCount) {
        Map<String, String> data = new HashMap<>();
        data.put("matchId", String.valueOf(matchId));
        data.put("battleId", String.valueOf(battleId));
        data.put("battleServerIp", battleServerIp);
        data.put("battleServerPort", String.valueOf(battleServerPort));
        data.put("gameMode", String.valueOf(gameMode.getCode()));
        data.put("teamCount", String.valueOf(teamCount));
        redisTemplate.opsForValue().set(
                MatchRedisKeys.matchResult(userId), data,
                keyExpireMinutes, TimeUnit.MINUTES);
    }

    public void deleteMatchResult(long userId) {
        redisTemplate.delete(MatchRedisKeys.matchResult(userId));
    }

    public void bindMatchToBattle(long matchId, long battleId) {
        redisTemplate.opsForValue().set(
                MatchRedisKeys.matchBattleId(matchId), battleId,
                keyExpireMinutes, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(
                MatchRedisKeys.battleMatchId(battleId), matchId,
                keyExpireMinutes, TimeUnit.MINUTES);
    }

    public Long getBattleIdByMatchId(long matchId) {
        if (matchId <= 0) return null;
        try {
            Object val = redisTemplate.opsForValue().get(MatchRedisKeys.matchBattleId(matchId));
            if (val != null) return Long.parseLong(val.toString());
            return null;
        } catch (Exception e) {
            log.error("查询matchId到battleId映射失败: matchId={}", matchId, e);
            return null;
        }
    }

    public void bindPlayerBattleRoom(long userId, long battleId) {
        redisTemplate.opsForValue().set(
                MatchRedisKeys.playerBattleRoom(userId), battleId,
                keyExpireMinutes, TimeUnit.MINUTES);
    }

    public void saveBattleRoom(long battleId, MatchRoom room, List<Long> userIds) {
        Map<String, String> roomData = new HashMap<>();
        roomData.put("matchId", String.valueOf(room.getMatchId()));
        roomData.put("battleId", String.valueOf(battleId));
        roomData.put("gameMode", String.valueOf(room.getGameMode().getCode()));
        roomData.put("teamCount", String.valueOf(room.getTeamCount()));
        roomData.put("playerCount", String.valueOf(userIds.size()));
        redisTemplate.opsForValue().set(
                MatchRedisKeys.battleRoom(battleId), roomData,
                keyExpireMinutes, TimeUnit.MINUTES);

        redisTemplate.opsForValue().set(
                MatchRedisKeys.battleRoomServer(battleId),
                room.getBattleServerIp() + ":" + room.getBattleServerPort(),
                keyExpireMinutes, TimeUnit.MINUTES);
    }

    public void cleanupPlayerState(long userId) {
        redisTemplate.delete(MatchRedisKeys.playerMatch(userId));
        redisTemplate.delete(MatchRedisKeys.matchResult(userId));
        redisTemplate.delete(MatchRedisKeys.playerBattleRoom(userId));
    }

    public void cleanupMatchState(long matchId, long battleId) {
        redisTemplate.delete(MatchRedisKeys.matchBattleId(matchId));
        if (battleId != 0) {
            redisTemplate.delete(MatchRedisKeys.battleMatchId(battleId));
        }
    }
}
