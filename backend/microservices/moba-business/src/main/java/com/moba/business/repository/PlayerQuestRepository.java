package com.moba.business.repository;

import com.moba.business.entity.quest.PlayerQuest;
import com.moba.business.entity.quest.QuestCategory;
import com.moba.business.entity.quest.QuestState;
import com.moba.business.entity.quest.QuestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerQuestRepository extends JpaRepository<PlayerQuest, Long> {

    List<PlayerQuest> findByPlayerIdAndQuestTypeOrderByCreateTimeAsc(Long playerId, QuestType questType);

    List<PlayerQuest> findByPlayerIdAndStateOrderByCreateTimeAsc(Long playerId, QuestState state);

    List<PlayerQuest> findByPlayerIdAndQuestTypeAndStateOrderByCreateTimeAsc(Long playerId, QuestType questType, QuestState state);

    Optional<PlayerQuest> findByPlayerIdAndQuestCode(Long playerId, String questCode);

    List<PlayerQuest> findByPlayerIdAndQuestTypeAndStateIn(Long playerId, QuestType questType, List<QuestState> states);

    @Query("SELECT pq FROM PlayerQuest pq WHERE pq.playerId = :playerId AND pq.questType = :questType AND pq.state = :state AND pq.expireAt IS NOT NULL AND pq.expireAt < :now")
    List<PlayerQuest> findExpiredQuests(@Param("playerId") Long playerId, @Param("questType") QuestType questType, @Param("state") QuestState state, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE PlayerQuest pq SET pq.state = :expiredState WHERE pq.playerId = :playerId AND pq.questType IN :questTypes AND pq.state = :activeState AND pq.expireAt IS NOT NULL AND pq.expireAt < :now")
    int expireQuests(@Param("playerId") Long playerId, @Param("questTypes") List<QuestType> questTypes, @Param("activeState") QuestState activeState, @Param("expiredState") QuestState expiredState, @Param("now") LocalDateTime now);

    @Query("SELECT pq FROM PlayerQuest pq WHERE pq.playerId = :playerId AND pq.category = :category AND pq.state = :state")
    List<PlayerQuest> findByPlayerIdAndCategoryAndState(@Param("playerId") Long playerId, @Param("category") QuestCategory category, @Param("state") QuestState state);

    boolean existsByPlayerIdAndQuestCode(Long playerId, String questCode);

    void deleteByPlayerIdAndQuestTypeAndState(Long playerId, QuestType questType, QuestState state);
}
