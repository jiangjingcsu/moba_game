package com.moba.data.repository;

import com.moba.data.model.BattleLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BattleLogRepository extends MongoRepository<BattleLog, String> {
    Optional<BattleLog> findByBattleId(long battleId);
    Page<BattleLog> findByGameMode(int gameMode, Pageable pageable);
    Page<BattleLog> findByWinnerTeamId(int winnerTeamId, Pageable pageable);
    Page<BattleLog> findByStartTimeBetween(long start, long end, Pageable pageable);
    Page<BattleLog> findByPlayersUserIdOrderByStartTimeDesc(long userId, Pageable pageable);
    List<BattleLog> findByPlayersUserIdOrderByStartTimeDesc(long userId);
}
