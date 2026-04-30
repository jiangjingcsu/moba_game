package com.moba.battleserver.manager;

import com.moba.battleserver.model.Player;
import com.moba.battleserver.protocol.LoginRequest;
import com.moba.battleserver.protocol.LoginResponse;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class PlayerManager {
    private final Map<Long, Player> playersById;
    private final Map<String, Player> playersByChannel;
    private final AtomicLong playerIdGenerator;

    public PlayerManager() {
        this.playersById = new ConcurrentHashMap<>();
        this.playersByChannel = new ConcurrentHashMap<>();
        this.playerIdGenerator = new AtomicLong(10000);
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

        log.info("Player created: id={}, name={}, channel={}", playerId, playerName, channelId.substring(0, 8));
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

            log.info("Player context updated: id={}, new channel={}", playerId, newChannelId.substring(0, 8));
        }
    }

    public void handleDisconnect(ChannelHandlerContext ctx) {
        String channelId = ctx.channel().id().asLongText();
        Player player = playersByChannel.remove(channelId);
        if (player != null) {
            log.info("Player disconnected: id={}, name={}", player.getPlayerId(), player.getPlayerName());
            if (player.getState() == Player.PlayerState.IN_BATTLE) {
                player.setReconnecting(true);
                log.info("Player in battle disconnected, waiting for reconnect: id={}", player.getPlayerId());
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
}
