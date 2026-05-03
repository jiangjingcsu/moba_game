package com.moba.match.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moba.common.event.MatchSuccessEvent;
import com.moba.common.model.MatchInfo;
import com.moba.common.util.SnowflakeIdGenerator;
import com.moba.match.discovery.BattleServerInfo;
import com.moba.match.discovery.BattleServiceDiscovery;
import com.moba.match.event.MatchEventProducer;
import com.moba.match.model.MatchRoom;
import com.moba.match.network.MatchChannelManager;
import com.moba.match.protocol.dto.MatchSuccessNotify;
import com.moba.match.repository.MatchRedisRepository;
import com.moba.netty.protocol.MessagePacket;
import com.moba.netty.protocol.ProtocolConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MatchBattleAllocator {

    private final BattleServiceDiscovery battleServiceDiscovery;
    private final MatchEventProducer matchEventProducer;
    private final MatchRedisRepository matchRedisRepository;
    private final MatchChannelManager channelManager;
    private final ObjectMapper objectMapper;

    public MatchBattleAllocator(BattleServiceDiscovery battleServiceDiscovery,
                                MatchEventProducer matchEventProducer,
                                MatchRedisRepository matchRedisRepository,
                                MatchChannelManager channelManager,
                                ObjectMapper objectMapper) {
        this.battleServiceDiscovery = battleServiceDiscovery;
        this.matchEventProducer = matchEventProducer;
        this.matchRedisRepository = matchRedisRepository;
        this.channelManager = channelManager;
        this.objectMapper = objectMapper;
    }

    public boolean allocateAndStart(MatchRoom room) {
        if (room.getState() != MatchInfo.MatchState.READY) return false;

        BattleServerInfo server = allocateBattleServer(room);
        if (server == null) {
            room.setState(MatchInfo.MatchState.FILLING);
            log.warn("匹配 {} 无可用战斗服务器, 重新放回队列", room.getMatchId());
            return false;
        }

        long battleId = SnowflakeIdGenerator.getDefault().nextId();
        List<Long> userIds = room.getAllUserIds();

        persistBattleState(room, battleId, server);
        pushMatchSuccessNotify(room, battleId, server, userIds);
        publishMatchSuccessEvent(room, battleId, server);
        return true;
    }

    private BattleServerInfo allocateBattleServer(MatchRoom room) {
        BattleServerInfo server = battleServiceDiscovery.selectBestBattleServer();
        if (server == null) {
            log.error("没有可用的战斗服务器, 匹配 {} 无法启动", room.getMatchId());
            return null;
        }

        room.setBattleServerIp(server.getIp());
        room.setBattleServerPort(server.getWsPort());
        log.info("匹配 {} 分配到战斗服务器 {}:{} (负载分数={})",
                room.getMatchId(), server.getIp(), server.getWsPort(), server.getLoadScore());
        return server;
    }

    private void persistBattleState(MatchRoom room, long battleId, BattleServerInfo server) {
        matchRedisRepository.bindMatchToBattle(room.getMatchId(), battleId);

        List<Long> userIds = room.getAllUserIds();
        for (Long uid : userIds) {
            matchRedisRepository.saveMatchResult(uid, room.getMatchId(), battleId,
                    server.getIp(), server.getWsPort(), room.getGameMode(), room.getTeamCount());
            matchRedisRepository.bindPlayerBattleRoom(uid, battleId);
        }

        matchRedisRepository.saveBattleRoom(battleId, room, userIds);
    }

    private void pushMatchSuccessNotify(MatchRoom room, long battleId,
                                         BattleServerInfo server, List<Long> userIds) {
        try {
            MatchSuccessNotify notify = MatchSuccessNotify.builder()
                    .matchId(room.getMatchId())
                    .battleId(battleId)
                    .gameMode(room.getGameMode().getCode())
                    .teamCount(room.getTeamCount())
                    .userIds(userIds)
                    .battleServerIp(server.getIp())
                    .battleServerPort(server.getWsPort())
                    .aiMode(room.isAiMode())
                    .aiLevel(room.getAiLevel())
                    .matchTime(System.currentTimeMillis())
                    .build();

            String json = objectMapper.writeValueAsString(notify);
            MessagePacket packet = MessagePacket.of(
                    ProtocolConstants.EXTENSION_MATCH,
                    ProtocolConstants.CMD_MATCH_SUCCESS_NOTIFY,
                    json);

            channelManager.pushToPlayers(userIds, packet);
            log.info("匹配 {} 已推送匹配成功通知给 {} 名玩家, 战斗服务器={}:{}",
                    room.getMatchId(), userIds.size(), server.getIp(), server.getWsPort());
        } catch (Exception e) {
            log.error("推送匹配成功通知失败: matchId={}", room.getMatchId(), e);
        }
    }

    private void publishMatchSuccessEvent(MatchRoom room, long battleId, BattleServerInfo server) {
        try {
            MatchSuccessEvent event = new MatchSuccessEvent();
            event.setEventId(SnowflakeIdGenerator.getDefault().nextIdStr());
            event.setTimestamp(System.currentTimeMillis());
            event.setMatchId(room.getMatchId());
            event.setBattleId(battleId);
            event.setGameMode(room.getGameMode());
            event.setUserIds(new ArrayList<>(room.getAllUserIds()));
            event.setTeamCount(room.getTeamCount());
            event.setAiMode(room.isAiMode());
            if (room.isAiMode()) {
                event.setNeededBots(room.getNeededPlayers() - room.getAllUserIds().size());
                event.setAiLevel(room.getAiLevel());
            } else {
                event.setNeededBots(0);
                event.setAiLevel(0);
            }
            event.setMatchTime(System.currentTimeMillis());
            event.setBattleServerIp(server.getIp());
            event.setBattleServerPort(server.getWsPort());
            matchEventProducer.publishMatchSuccess(event);
            log.info("匹配 {} 已发布匹配成功事件, 战斗服务器={}:{}, aiMode={}, neededBots={}, aiLevel={}",
                    room.getMatchId(), server.getIp(), server.getWsPort(),
                    room.isAiMode(), event.getNeededBots(), event.getAiLevel());
        } catch (Exception e) {
            log.error("为匹配 {} 发布匹配成功事件失败", room.getMatchId(), e);
        }
    }
}
