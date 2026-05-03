package com.moba.data.repository;

import com.moba.data.model.Replay;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReplayRepository extends MongoRepository<Replay, String> {
    Optional<Replay> findByBattleId(long battleId);
}
