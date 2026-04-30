package com.moba.data.service.impl;

import com.moba.common.dto.BattleLogDTO;
import com.moba.common.dto.PlayerStatDTO;
import com.moba.common.dto.ReplayDTO;
import com.moba.common.dto.TeamStatDTO;
import com.moba.common.service.DataService;
import com.moba.data.model.BattleLog;
import com.moba.data.model.Replay;
import com.moba.data.repository.BattleLogRepository;
import com.moba.data.repository.ReplayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@DubboService
@RequiredArgsConstructor
@ConditionalOnBean(BattleLogRepository.class)
public class DataServiceImpl implements DataService {

    private final BattleLogRepository battleLogRepository;
    private final ReplayRepository replayRepository;

    @Override
    public BattleLogDTO saveBattleLog(BattleLogDTO dto) {
        BattleLog battleLog = toEntity(dto);
        battleLog = battleLogRepository.save(battleLog);
        DataServiceImpl.log.info("Saved battle log: {}", battleLog.getBattleId());
        return toDTO(battleLog);
    }

    @Override
    public Optional<BattleLogDTO> getBattleLog(String battleId) {
        return battleLogRepository.findByBattleId(battleId).map(this::toDTO);
    }

    @Override
    public List<BattleLogDTO> getPlayerBattleHistory(long playerId, int limit) {
        Page<BattleLog> page = battleLogRepository.findByPlayersPlayerIdOrderByStartTimeDesc(
                playerId, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startTime")));
        return page.getContent().stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public ReplayDTO saveReplay(ReplayDTO dto) {
        Replay replay = toEntity(dto);
        Replay saved = replayRepository.save(replay);
        log.info("Replay saved: {}, frames: {}", saved.getBattleId(), saved.getFrameCount());
        return toDTO(saved);
    }

    @Override
    public Optional<ReplayDTO> getReplay(String battleId) {
        return replayRepository.findByBattleId(battleId).map(this::toDTO);
    }

    @Override
    public List<BattleLogDTO> getRecentBattles(int gameMode, int limit) {
        Page<BattleLog> page = battleLogRepository.findByGameMode(
                gameMode, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "startTime")));
        return page.getContent().stream().map(this::toDTO).collect(Collectors.toList());
    }

    private BattleLog toEntity(BattleLogDTO dto) {
        BattleLog entity = new BattleLog();
        entity.setBattleId(dto.getBattleId());
        entity.setGameMode(dto.getGameMode());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setDuration(dto.getDuration());
        entity.setWinnerTeamId(dto.getWinnerTeamId());

        if (dto.getPlayers() != null) {
            entity.setPlayers(dto.getPlayers().stream().map(p -> {
                BattleLog.PlayerLog pl = new BattleLog.PlayerLog();
                pl.setPlayerId(p.getPlayerId());
                pl.setTeamId(p.getTeamId());
                pl.setHeroId(p.getHeroId());
                pl.setLevel(p.getLevel());
                pl.setKillCount(p.getKillCount());
                pl.setDeathCount(p.getDeathCount());
                pl.setAssistCount(p.getAssistCount());
                pl.setDamageDealt(p.getDamageDealt());
                pl.setDamageTaken(p.getDamageTaken());
                pl.setHealing(p.getHealing());
                pl.setGoldEarned(p.getGoldEarned());
                pl.setExperienceEarned(p.getExperienceEarned());
                pl.setAI(p.isAI());
                pl.setWinner(p.isWinner());
                return pl;
            }).collect(Collectors.toList()));
        }

        if (dto.getTeamStats() != null) {
            entity.setTeamStats(dto.getTeamStats().entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(
                            java.util.Map.Entry::getKey,
                            e -> {
                                BattleLog.TeamStat ts = new BattleLog.TeamStat();
                                ts.setTeamId(e.getValue().getTeamId());
                                ts.setTotalKills(e.getValue().getTotalKills());
                                ts.setTotalDeaths(e.getValue().getTotalDeaths());
                                ts.setTowerDestroyed(e.getValue().getTowerDestroyed());
                                ts.setBarracksDestroyed(e.getValue().getBarracksDestroyed());
                                return ts;
                            }
                    )));
        }

        return entity;
    }

    private BattleLogDTO toDTO(BattleLog entity) {
        BattleLogDTO dto = new BattleLogDTO();
        dto.setBattleId(entity.getBattleId());
        dto.setGameMode(entity.getGameMode());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setDuration(entity.getDuration());
        dto.setWinnerTeamId(entity.getWinnerTeamId());

        if (entity.getPlayers() != null) {
            dto.setPlayers(entity.getPlayers().stream().map(p -> {
                PlayerStatDTO pl = new PlayerStatDTO();
                pl.setPlayerId(p.getPlayerId());
                pl.setTeamId(p.getTeamId());
                pl.setHeroId(p.getHeroId());
                pl.setLevel(p.getLevel());
                pl.setKillCount(p.getKillCount());
                pl.setDeathCount(p.getDeathCount());
                pl.setAssistCount(p.getAssistCount());
                pl.setDamageDealt(p.getDamageDealt());
                pl.setDamageTaken(p.getDamageTaken());
                pl.setHealing(p.getHealing());
                pl.setGoldEarned(p.getGoldEarned());
                pl.setExperienceEarned(p.getExperienceEarned());
                pl.setAI(p.isAI());
                pl.setWinner(p.isWinner());
                return pl;
            }).collect(Collectors.toList()));
        }

        if (entity.getTeamStats() != null) {
            dto.setTeamStats(entity.getTeamStats().entrySet().stream().collect(
                    Collectors.toMap(
                            java.util.Map.Entry::getKey,
                            e -> {
                                TeamStatDTO ts = new TeamStatDTO();
                                ts.setTeamId(e.getValue().getTeamId());
                                ts.setTotalKills(e.getValue().getTotalKills());
                                ts.setTotalDeaths(e.getValue().getTotalDeaths());
                                ts.setTowerDestroyed(e.getValue().getTowerDestroyed());
                                ts.setBarracksDestroyed(e.getValue().getBarracksDestroyed());
                                return ts;
                            }
                    )));
        }

        return dto;
    }

    private Replay toEntity(ReplayDTO dto) {
        Replay entity = new Replay();
        entity.setBattleId(dto.getBattleId());
        entity.setGameMode(dto.getGameMode());
        entity.setStartTime(dto.getStartTime());
        entity.setEndTime(dto.getEndTime());
        entity.setWinnerTeamId(dto.getWinnerTeamId());
        entity.setFrameCount(dto.getFrameCount());
        entity.setRandomSeed(dto.getRandomSeed());
        entity.setFrameData(dto.getFrameData());
        entity.setSnapshotData(dto.getSnapshotData());

        if (dto.getPlayers() != null) {
            entity.setPlayers(dto.getPlayers().stream().map(p -> {
                Replay.PlayerInfo pi = new Replay.PlayerInfo();
                pi.setPlayerId(p.getPlayerId());
                pi.setNickname(p.getNickname());
                pi.setTeamId(p.getTeamId());
                pi.setHeroId(p.getHeroId());
                pi.setFinalKillCount(p.getFinalKillCount());
                pi.setFinalDeathCount(p.getFinalDeathCount());
                pi.setFinalAssistCount(p.getFinalAssistCount());
                return pi;
            }).collect(Collectors.toList()));
        }

        return entity;
    }

    private ReplayDTO toDTO(Replay entity) {
        ReplayDTO dto = new ReplayDTO();
        dto.setBattleId(entity.getBattleId());
        dto.setGameMode(entity.getGameMode());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setWinnerTeamId(entity.getWinnerTeamId());
        dto.setFrameCount(entity.getFrameCount());
        dto.setRandomSeed(entity.getRandomSeed());
        dto.setFrameData(entity.getFrameData());
        dto.setSnapshotData(entity.getSnapshotData());

        if (entity.getPlayers() != null) {
            dto.setPlayers(entity.getPlayers().stream().map(p -> {
                ReplayDTO.PlayerInfoDTO pi = new ReplayDTO.PlayerInfoDTO();
                pi.setPlayerId(p.getPlayerId());
                pi.setNickname(p.getNickname());
                pi.setTeamId(p.getTeamId());
                pi.setHeroId(p.getHeroId());
                pi.setFinalKillCount(p.getFinalKillCount());
                pi.setFinalDeathCount(p.getFinalDeathCount());
                pi.setFinalAssistCount(p.getFinalAssistCount());
                return pi;
            }).collect(Collectors.toList()));
        }

        return dto;
    }
}
