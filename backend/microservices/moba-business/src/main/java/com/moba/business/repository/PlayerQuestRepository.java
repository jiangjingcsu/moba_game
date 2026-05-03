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

    List<PlayerQuest> findByUserIdAndQuestTypeOrderByCreateTimeAsc(long userId, QuestType questType);

    List<PlayerQuest> findByUserIdAndStateOrderByCreateTimeAsc(long userId, QuestState state);

    List<PlayerQuest> findByUserIdAndQuestTypeAndStateOrderByCreateTimeAsc(long userId, QuestType questType, QuestState state);

    Optional<PlayerQuest> findByUserIdAndQuestCode(long userId, String questCode);

    List<PlayerQuest> findByUserIdAndQuestTypeAndStateIn(long userId, QuestType questType, List<QuestState> states);

    @Query("SELECT pq FROM PlayerQuest pq WHERE pq.userId = :userId AND pq.questType = :questType AND pq.state = :state AND pq.expireAt IS NOT NULL AND pq.expireAt < :now")
    List<PlayerQuest> findExpiredQuests(@Param("userId") long userId, @Param("questType") QuestType questType, @Param("state") QuestState state, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE PlayerQuest pq SET pq.state = :expiredState WHERE pq.userId = :userId AND pq.questType IN :questTypes AND pq.state = :activeState AND pq.expireAt IS NOT NULL AND pq.expireAt < :now")
    int expireQuests(@Param("userId") long userId, @Param("questTypes") List<QuestType> questTypes, @Param("activeState") QuestState activeState, @Param("expiredState") QuestState expiredState, @Param("now") LocalDateTime now);

    @Query("SELECT pq FROM PlayerQuest pq WHERE pq.userId = :userId AND pq.category = :category AND pq.state = :state")
    List<PlayerQuest> findByUserIdAndCategoryAndState(@Param("userId") long userId, @Param("category") QuestCategory category, @Param("state") QuestState state);

    boolean existsByUserIdAndQuestCode(long userId, String questCode);

    void deleteByUserIdAndQuestTypeAndState(long userId, QuestType questType, QuestState state);
}
