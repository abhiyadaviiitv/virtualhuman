package com.virtualhuman.repository;

import com.virtualhuman.model.EpisodicMemory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EpisodicMemoryRepository extends MongoRepository<EpisodicMemory, String> {

    // Find memories for a specific avatar that happened within a certain time range
    List<EpisodicMemory> findByAvatarIdAndTimestampBetweenOrderByTimestampDesc(String avatarId, Instant start,
            Instant end);

    // Get the most recent N memories overall
    List<EpisodicMemory> findTop50ByAvatarIdOrderByTimestampDesc(String avatarId);
}
