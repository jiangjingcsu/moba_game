package com.moba.battle.manager;

import com.moba.battle.config.SpringContextHolder;
import com.moba.battle.model.Player;
import com.moba.battle.protocol.request.LoginRequest;
import com.moba.battle.protocol.response.LoginResponse;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class PlayerManager {
    private final Map<Long, Player> playersById;
    private final Map<String, Player> playersByChannel;
    private final AtomicLong playerIdGenerator;

    public PlayerManager() {
        this.playersById = new ConcurrentHashMap<>();
        this.playersByChannel = new ConcurrentHashMap<>();
        this.playerIdGenerator = new AtomicLong(10000);
    }

    public static PlayerManager getInstance() {
        return SpringContextHolder.getBean(PlayerManager.class);
    }

    public Player createPlayer(ChannelHandlerContext ctx, String playerName) {
        long playerId = playerIdGenerator.incrementAndGet();
        Player player = new Player();
        player.setPlayerId(playerId);
        player.setPlayerName(playerName);
        player.setLevel(1);
        player.setRank(1);
        player.setRankScore(1000);
        player.setCtx(ctx);
        player.setState(Player.PlayerState.ONLINE);
        player.setLastHeartbeatTime(System.currentTimeMillis());

        String channelId = ctx.channel().id().asLongText();
        playersById.put(playerId, player);
        playersByChannel.put(channelId, player);

        log.info("玩家已创建: id={}, 名称={}, 通道={}", playerId, playerName, channelId.substring(0, 8));
        return player;
    }

    public Optional<Player> getPlayerById(long playerId) {
        return Optional.ofNullable(playersById.get(playerId));
    }

    public Optional<Player> getPlayerByChannel(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        return Optional.ofNullable(playersByChannel.get(channelId));
    }

    public void updatePlayerContext(ChannelHandlerContext ctx, long playerId) {
        Optional<Player> playerOpt = getPlayerById(playerId);
        if (playerOpt.isPresent()) {
            Player player = playerOpt.get();
            String oldChannelId = player.getCtx().channel().id().asLongText();
            String newChannelId = ctx.channel().id().asLongText();

            player.setCtx(ctx);
            playersByChannel.remove(oldChannelId);
            playersByChannel.put(newChannelId, player);

            log.info("玩家上下文已更新: id={}, 新通道={}", playerId, newChannelId.substring(0, 8));
        }
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        Player player = playersByChannel.remove(channelId);
        if (player != null) {
            log.info("玩家断开连接: id={}, 名称={}", player.getPlayerId(), player.getPlayerName());
            if (player.getState() == Player.PlayerState.IN_BATTLE) {
                player.setReconnecting(true);
                log.info("战斗中玩家断开连接, 等待重连: id={}", player.getPlayerId());
            } else {
                player.setState(Player.PlayerState.OFFLINE);
                playersById.remove(player.getPlayerId());
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

    public LoginResponse handleLogin(ChannelHandlerContext ctx, LoginRequest request) {
        Player player = createPlayer(ctx, request.getPlayerName());
        return LoginResponse.success(
                player.getPlayerId(),
                player.getPlayerName(),
                player.getRank(),
                player.getRankScore()
        );
    }

    public Player registerPlayerFromToken(ChannelHandlerContext ctx, long playerId, String username) {
        Optional<Player> existing = getPlayerById(playerId);
        if (existing.isPresent()) {
            Player player = existing.get();
            String oldChannelId = player.getCtx() != null ? player.getCtx().channel().id().asLongText() : null;
            String newChannelId = ctx.channel().id().asLongText();
            player.setCtx(ctx);
            player.setState(Player.PlayerState.ONLINE);
            player.setReconnecting(false);
            player.setLastHeartbeatTime(System.currentTimeMillis());
            if (oldChannelId != null) {
                playersByChannel.remove(oldChannelId);
            }
            playersByChannel.put(newChannelId, player);
            log.info("玩家通过令牌重连: id={}, 名称={}", playerId, username);
            return player;
        }

        Player player = new Player();
        player.setPlayerId(playerId);
        player.setPlayerName(username);
        player.setLevel(1);
        player.setRank(1);
        player.setRankScore(1000);
        player.setCtx(ctx);
        player.setState(Player.PlayerState.ONLINE);
        player.setLastHeartbeatTime(System.currentTimeMillis());

        String channelId = ctx.channel().id().asLongText();
        playersById.put(playerId, player);
        playersByChannel.put(channelId, player);
        log.info("玩家通过令牌注册: id={}, 名称={}, 通道={}", playerId, username, channelId.substring(0, 8));
        return player;
    }
}
