package com.moba.business.repository;

import com.moba.business.entity.quest.QuestTemplate;
import com.moba.business.entity.quest.QuestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuestTemplateRepository extends JpaRepository<QuestTemplate, Long> {

    Optional<QuestTemplate> findByQuestCode(String questCode);

    List<QuestTemplate> findByQuestTypeAndEnabledTrueOrderBySortOrderAsc(QuestType questType);

    List<QuestTemplate> findByEnabledTrueOrderByQuestTypeAscSortOrderAsc();

    List<QuestTemplate> findByQuestTypeOrderBySortOrderAsc(QuestType questType);

    boolean existsByQuestCode(String questCode);
}
