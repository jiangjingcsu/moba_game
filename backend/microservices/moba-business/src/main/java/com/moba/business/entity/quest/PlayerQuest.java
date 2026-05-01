package com.moba.business.entity.quest;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_player_quest")
@EntityListeners(AuditingEntityListener.class)
public class PlayerQuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private Long questTemplateId;

    @Column(nullable = false, length = 50)
    private String questCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestType questType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestCategory category;

    @Column(nullable = false)
    private Integer currentValue = 0;

    @Column(nullable = false)
    private Integer targetValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestState state = QuestState.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestRewardType rewardType;

    @Column(nullable = false)
    private Integer rewardAmount;

    private LocalDateTime completedAt;

    private LocalDateTime claimedAt;

    private LocalDateTime expireAt;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
