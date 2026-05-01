package com.moba.business.entity.quest;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_quest_template")
@EntityListeners(AuditingEntityListener.class)
public class QuestTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String questCode;

    @Column(nullable = false, length = 100)
    private String questName;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestType questType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestCategory category;

    @Column(nullable = false)
    private Integer targetValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestRewardType rewardType;

    @Column(nullable = false)
    private Integer rewardAmount;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column
    private Integer requiredLevel = 0;

    @Column
    private Integer requiredQuestOrder = -1;

    @Column(length = 50)
    private String gameModeRestriction;

    @Column(length = 50)
    private String heroRestriction;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
