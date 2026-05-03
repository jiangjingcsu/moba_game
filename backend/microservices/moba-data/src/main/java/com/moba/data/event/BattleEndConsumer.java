package com.moba.data.event;

import com.moba.common.dto.BattleResultDTO;
import com.moba.common.dto.PlayerStatDTO;
import com.moba.common.dto.TeamStatDTO;
import com.moba.common.event.BattleEndEvent;
import com.moba.common.event.EventTopics;
import com.moba.data.model.BattleLog;
import com.moba.data.model.Replay;
import com.moba.data.repository.BattleLogRepository;
import com.moba.data.repository.ReplayRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.name-server", matchIfMissing = false)
@ConditionalOnBean(BattleLogRepository.class)
@RocketMQMessageListener(
        topic = EventTopics.BATTLE_END,
        consumerGroup = "moba-data-consumer"
)
@RequiredArgsConstructor
public class BattleEndConsumer implements RocketMQListener<BattleEndEvent> {

    private final BattleLogRepository battleLogRepository;
    private final ReplayRepository replayRepository;

    @Override
    public void onMessage(BattleEndEvent event) {
        BattleResultDTO result = event.getResult();
        if (result == null) {
            log.warn("收到结果为空的战斗结束事件, 跳过处理");
            return;
        }

        log.info("收到战斗结束事件: battleId={}", result.getBattleId());

        try {
            BattleLog battleLog = new BattleLog();
            battleLog.setBattleId(result.getBattleId());
            battleLog.setGameMode(result.getGameMode() != null ? result.getGameMode().getCode() : 0);
            battleLog.setStartTime(result.getStartTime());
            battleLog.setEndTime(result.getEndTime());
            battleLog.setDuration(result.getDuration());
            battleLog.setWinnerTeamId(result.getWinnerTeamId());

            if (result.getPlayers() != null) {
                battleLog.setPlayers(result.getPlayers().stream().map(p -> {
                    BattleLog.PlayerLog pl = new BattleLog.PlayerLog();
                    pl.setUserId(p.getUserId());
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

            if (result.getTeamStats() != null) {
                battleLog.setTeamStats(result.getTeamStats().entrySet().stream().collect(
                        Collectors.toMap(
                                java.util.Map.Entry::getKey,
                                e -> {
                                    TeamStatDTO val = e.getValue();
                                    BattleLog.TeamStat ts = new BattleLog.TeamStat();
                                    ts.setTeamId(val.getTeamId());
                                    ts.setTotalKills(val.getTotalKills());
                                    ts.setTotalDeaths(val.getTotalDeaths());
                                    ts.setTowerDestroyed(val.getTowerDestroyed());
                                    ts.setBarracksDestroyed(val.getBarracksDestroyed());
                                    return ts;
                                }
                        )));
            }

            battleLogRepository.save(battleLog);
            log.info("事件战斗日志已保存: battleId={}", result.getBattleId());

            if (event.getReplayFrameData() != null) {
                Replay replay = new Replay();
                replay.setBattleId(result.getBattleId());
                replay.setGameMode(result.getGameMode() != null ? result.getGameMode().getCode() : 0);
                replay.setStartTime(result.getStartTime());
                replay.setEndTime(result.getEndTime());
                replay.setWinnerTeamId(result.getWinnerTeamId());
                replay.setFrameData(event.getReplayFrameData());
                replay.setSnapshotData(event.getReplaySnapshotData());

                replayRepository.save(replay);
                log.info("事件回放已保存: battleId={}", result.getBattleId());
            }
        } catch (Exception e) {
            log.error("处理战斗结束事件异常: battleId={}", result.getBattleId(), e);
            throw e;
        }
    }
}
