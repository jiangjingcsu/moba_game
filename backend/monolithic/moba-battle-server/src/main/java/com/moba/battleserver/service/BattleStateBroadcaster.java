package com.moba.battleserver.service;

import com.moba.battleserver.manager.BattleRoom;
import com.moba.battleserver.manager.PlayerManager;
import com.moba.battleserver.model.BattlePlayer;
import com.moba.battleserver.model.BattleSession;
import com.moba.battleserver.model.Player;
import com.moba.battleserver.network.codec.GameMessage;
import com.moba.battleserver.protocol.JsonProtocol;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
public class BattleStateBroadcaster {
    private final PlayerManager playerManager;

    public BattleStateBroadcaster(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public void broadcastFrameState(BattleRoom room) {
        if (!room.isRunning()) return;

        BattleSession session = room.getSession();
        int currentFrame = room.getEngine().getCurrentFrame();
        long gameTime = session.getStartTime() > 0 ?
                (System.currentTimeMillis() - session.getStartTime()) / 1000 : 0;

        String stateJson = serializeFrameState(currentFrame, gameTime, session);
        byte[] body = stateJson.getBytes(StandardCharsets.UTF_8);

        for (Long playerId : session.getBattlePlayers().keySet()) {
            Player player = playerManager.getPlayerById(playerId).orElse(null);
            if (player != null && player.getCtx() != null && player.getCtx().channel().isActive()) {
                GameMessage msg = new GameMessage();
                msg.setMessageId(GameMessage.BATTLE_STATE_UPDATE);
                msg.setBody(body);
                player.getCtx().writeAndFlush(msg);
            }
        }
    }

    public void notifyPlayersLoading(BattleRoom room) {
        String battleId = room.getBattleId();
        long randomSeed = room.getEngine().getRandomSeed();
        int playerCount = room.getPlayerCount();

        String notifyData = "NOTIFY|LOADING|" + battleId + "|seed=" + randomSeed + "|players=" + playerCount;
        byte[] body = notifyData.getBytes(StandardCharsets.UTF_8);

        for (Long playerId : room.getSession().getBattlePlayers().keySet()) {
            Player player = playerManager.getPlayerById(playerId).orElse(null);
            if (player != null && player.getCtx() != null && player.getCtx().channel().isActive()) {
                GameMessage msg = new GameMessage();
                msg.setMessageId(GameMessage.BATTLE_ENTER_RESPONSE);
                msg.setBody(body);
                player.getCtx().writeAndFlush(msg);
            }
        }
    }

    public void broadcastBattleEnd(BattleRoom room) {
        BattleSession session = room.getSession();
        StringBuilder sb = new StringBuilder();
        sb.append("NOTIFY|GAME_OVER|").append(room.getBattleId());
        sb.append("|duration=").append(session.getEndTime() - session.getStartTime());

        for (Map.Entry<Integer, BattleSession.Team> entry : session.getTeams().entrySet()) {
            BattleSession.Team team = entry.getValue();
            sb.append("|team").append(entry.getKey()).append("=");
            sb.append("hp=").append(team.getBaseHp());
            sb.append(",towers=").append(team.getTowerCount());
            sb.append(",defeated=").append(team.isDefeated());
        }

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);

        for (Long playerId : session.getBattlePlayers().keySet()) {
            Player player = playerManager.getPlayerById(playerId).orElse(null);
            if (player != null && player.getCtx() != null && player.getCtx().channel().isActive()) {
                GameMessage msg = new GameMessage();
                msg.setMessageId(GameMessage.BATTLE_END_NOTIFY);
                msg.setBody(body);
                player.getCtx().writeAndFlush(msg);
            }
        }
    }

    private String serializeFrameState(int frame, long gameTime, BattleSession session) {
        StringBuilder heroesJson = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Long, BattlePlayer> entry : session.getBattlePlayers().entrySet()) {
            BattlePlayer bp = entry.getValue();
            if (!first) heroesJson.append(",");
            first = false;
            heroesJson.append("{");
            heroesJson.append("\"id\":\"").append(bp.getPlayerId()).append("\"");
            heroesJson.append(",\"heroId\":").append(bp.getHeroId());
            heroesJson.append(",\"teamId\":").append(bp.getTeamId());
            heroesJson.append(",\"position\":{\"x\":").append(bp.getPosition().x)
                    .append(",\"y\":").append(bp.getPosition().y).append("}");
            heroesJson.append(",\"hp\":").append(bp.getCurrentHp());
            heroesJson.append(",\"maxHp\":").append(bp.getMaxHp());
            heroesJson.append(",\"mp\":").append(bp.getCurrentMp());
            heroesJson.append(",\"maxMp\":").append(bp.getMaxMp());
            heroesJson.append(",\"level\":").append(bp.getLevel());
            heroesJson.append(",\"isAlive\":").append(!bp.isDead());
            heroesJson.append(",\"gold\":").append(bp.getGold());
            heroesJson.append(",\"kills\":").append(bp.getKillCount());
            heroesJson.append(",\"deaths\":").append(bp.getDeathCount());
            heroesJson.append(",\"assists\":").append(bp.getAssistCount());
            heroesJson.append("}");
        }
        heroesJson.append("]");

        return "{\"frame\":" + frame
                + ",\"gameTime\":" + gameTime
                + ",\"heroes\":" + heroesJson
                + ",\"towers\":[],\"minions\":[],\"monsters\":[]}";
    }
}
