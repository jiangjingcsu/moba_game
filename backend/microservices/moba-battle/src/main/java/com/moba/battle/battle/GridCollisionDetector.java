package com.moba.battle.battle;

import com.moba.battle.model.BattlePlayer;
import com.moba.battle.util.FixedPoint;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class GridCollisionDetector {
    private final int gridWidth;
    private final int gridHeight;
    private final int cellSize;
    private final Map<Long, BattlePlayer> players;
    private final Map<String, List<Long>> grid;

    public GridCollisionDetector(int mapWidth, int mapHeight, int cellSize) {
        this.gridWidth = mapWidth / cellSize;
        this.gridHeight = mapHeight / cellSize;
        this.cellSize = cellSize;
        this.players = new ConcurrentHashMap<>();
        this.grid = new ConcurrentHashMap<>();
    }

    public void updatePlayerPosition(BattlePlayer player) {
        players.put(player.getUserId(), player);
        rebuildGrid();
    }

    public void rebuildGrid() {
        grid.clear();
        for (Map.Entry<Long, BattlePlayer> entry : players.entrySet()) {
            BattlePlayer player = entry.getValue();
            int gridX = Math.floorDiv(player.getPosition().x, cellSize);
            int gridY = Math.floorDiv(player.getPosition().y, cellSize);
            String key = gridX + "_" + gridY;
            grid.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.getKey());
        }
    }

    public List<BattlePlayer> getPlayersInRadius(long userId, float radius) {
        BattlePlayer player = players.get(userId);
        if (player == null) return Collections.emptyList();

        int playerGridX = Math.floorDiv(player.getPosition().x, cellSize);
        int playerGridY = Math.floorDiv(player.getPosition().y, cellSize);

        int gridRadius = (int) Math.ceil(radius / cellSize);
        Set<Long> resultIds = new HashSet<>();

        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dy = -gridRadius; dy <= gridRadius; dy++) {
                int checkX = playerGridX + dx;
                int checkY = playerGridY + dy;
                String key = checkX + "_" + checkY;

                List<Long> cellPlayers = grid.get(key);
                if (cellPlayers != null) {
                    for (Long otherId : cellPlayers) {
                        if (otherId.equals(userId)) continue;

                        BattlePlayer other = players.get(otherId);
                        if (other != null) {
                            float distance = calculateDistance(player, other);
                            if (distance <= radius) {
                                resultIds.add(otherId);
                            }
                        }
                    }
                }
            }
        }

        List<BattlePlayer> result = new ArrayList<>();
        for (Long id : resultIds) {
            result.add(players.get(id));
        }
        return result;
    }

    public List<BattlePlayer> getPlayersInRadius(int x, int y, int radius) {
        int centerGridX = Math.floorDiv(x, cellSize);
        int centerGridY = Math.floorDiv(y, cellSize);

        int gridRadius = (int) Math.ceil(radius / cellSize);
        Set<Long> resultIds = new HashSet<>();

        for (int dx = -gridRadius; dx <= gridRadius; dx++) {
            for (int dy = -gridRadius; dy <= gridRadius; dy++) {
                int checkX = centerGridX + dx;
                int checkY = centerGridY + dy;
                String key = checkX + "_" + checkY;

                List<Long> cellPlayers = grid.get(key);
                if (cellPlayers != null) {
                    for (Long otherId : cellPlayers) {
                        BattlePlayer other = players.get(otherId);
                        if (other != null) {
                            int odx = other.getPosition().x - x;
                            int ody = other.getPosition().y - y;
                            float distance = (float) Math.sqrt(odx * odx + ody * ody);
                            if (distance <= radius) {
                                resultIds.add(otherId);
                            }
                        }
                    }
                }
            }
        }

        List<BattlePlayer> result = new ArrayList<>();
        for (Long id : resultIds) {
            result.add(players.get(id));
        }
        return result;
    }

    public List<BattlePlayer> getPlayersInRect(long userId, float width, float height) {
        BattlePlayer player = players.get(userId);
        if (player == null) return Collections.emptyList();

        int playerGridX = Math.floorDiv(player.getPosition().x, cellSize);
        int playerGridY = Math.floorDiv(player.getPosition().y, cellSize);

        int gridWidthCount = (int) Math.ceil(width / cellSize);
        int gridHeightCount = (int) Math.ceil(height / cellSize);

        Set<Long> resultIds = new HashSet<>();

        for (int dx = -gridWidthCount; dx <= gridWidthCount; dx++) {
            for (int dy = -gridHeightCount; dy <= gridHeightCount; dy++) {
                int checkX = playerGridX + dx;
                int checkY = playerGridY + dy;
                String key = checkX + "_" + checkY;

                List<Long> cellPlayers = grid.get(key);
                if (cellPlayers != null) {
                    resultIds.addAll(cellPlayers);
                }
            }
        }

        resultIds.remove(userId);
        List<BattlePlayer> result = new ArrayList<>();
        for (Long id : resultIds) {
            BattlePlayer other = players.get(id);
            if (other != null) {
                if (isInRect(player, other, width, height)) {
                    result.add(other);
                }
            }
        }
        return result;
    }

    private float calculateDistance(BattlePlayer a, BattlePlayer b) {
        float dx = a.getPosition().x - b.getPosition().x;
        float dy = a.getPosition().y - b.getPosition().y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private boolean isInRect(BattlePlayer center, BattlePlayer target, float width, float height) {
        float dx = Math.abs(target.getPosition().x - center.getPosition().x);
        float dy = Math.abs(target.getPosition().y - center.getPosition().y);
        return dx <= width / 2 && dy <= height / 2;
    }
}
