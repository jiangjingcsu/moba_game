package com.moba.battle.manager;

import com.moba.battle.model.Player;
import com.moba.netty.connection.Connection;
import com.moba.netty.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PlayerManager {
    private final Map<Long, Player> playersById = new ConcurrentHashMap<>();

    public Player createPlayer(User user, String playerName) {
        long userId = user.getUserId();
        Player player = new Player();
        player.setUserId(userId);
        player.setPlayerName(playerName);
        player.setLevel(1);
        player.setRank(1);
        player.setRankScore(1000);
        player.setUser(user);
        player.setState(Player.PlayerState.ONLINE);
        player.setLastHeartbeatTime(System.currentTimeMillis());

        playersById.put(userId, player);
        log.info("玩家已创建: id={}, 名称={}", userId, playerName);
        return player;
    }

    public Optional<Player> getPlayerById(long userId) {
        return Optional.ofNullable(playersById.get(userId));
    }

    public void handleDisconnect(long userId) {
        Player player = playersById.get(userId);
        if (player != null) {
            log.info("玩家断开连接: id={}, 名称={}", player.getUserId(), player.getPlayerName());
            if (player.getState() == Player.PlayerState.IN_BATTLE) {
                player.setReconnecting(true);
                log.info("战斗中玩家断开连接, 等待重连: id={}", player.getUserId());
            } else {
                player.setState(Player.PlayerState.OFFLINE);
                playersById.remove(userId);
            }
        }
    }

    public Map<Long, Player> getAllPlayers() {
        return playersById;
    }

    public int getOnlinePlayerCount() {
        return (int) playersById.values().stream()
                .filter(p -> p.getState() != Player.PlayerState.OFFLINE)
                .count();
    }

    public Player registerPlayerFromToken(Connection connection, long userId, String username) {
        Optional<Player> existing = getPlayerById(userId);
        if (existing.isPresent()) {
            Player player = existing.get();
            player.setState(Player.PlayerState.ONLINE);
            player.setReconnecting(false);
            player.setLastHeartbeatTime(System.currentTimeMillis());
            log.info("玩家通过令牌重连: id={}, 名称={}", userId, username);
            return player;
        }

        Player player = new Player();
        player.setUserId(userId);
        player.setPlayerName(username);
        player.setLevel(1);
        player.setRank(1);
        player.setRankScore(1000);
        player.setState(Player.PlayerState.ONLINE);
        player.setLastHeartbeatTime(System.currentTimeMillis());

        playersById.put(userId, player);
        log.info("玩家通过令牌注册: id={}, 名称={}", userId, username);
        return player;
    }
}
