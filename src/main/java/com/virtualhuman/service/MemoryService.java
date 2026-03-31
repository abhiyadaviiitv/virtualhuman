package com.virtualhuman.service;

import com.virtualhuman.model.Memory;
import com.virtualhuman.repository.MemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final MemoryRepository memoryRepository;
    private final RedisMemoryService redisMemoryService;

    public MemoryService(MemoryRepository memoryRepository, RedisMemoryService redisMemoryService) {
        this.memoryRepository = memoryRepository;
        this.redisMemoryService = redisMemoryService;
    }

    /**
     * Add a memory entry. Writes to Redis (primary, fast) and
     * asynchronously writes through to MongoDB (archival, durable).
     */
    public void addMemory(String avatarId, String activity) {
        redisMemoryService.addMemory(avatarId, activity);
        log.info("Memory saved to Redis for avatar={}: {}", avatarId, activity);
        archiveToMongo(avatarId, activity);
    }

    /**
     * Retrieve recent activities. Redis is the primary source.
     * Falls back to MongoDB if Redis is empty (cold start scenario).
     */
    public List<String> getRecentActivities(String avatarId, int limit) {
        List<String> activities = redisMemoryService.getRecentActivities(avatarId, limit);

        if (!activities.isEmpty()) {
            log.debug("Cache HIT — {} activities from Redis for avatar={}", activities.size(), avatarId);
            return activities;
        }

        log.debug("Cache MISS — falling back to MongoDB for avatar={}", avatarId);
        return memoryRepository.findByAvatarIdOrderByTimestampDesc(avatarId).stream()
                .limit(limit)
                .map(Memory::getActivity)
                .toList();
    }

    /**
     * Async write-through: persist memory to MongoDB for long-term archival.
     * This runs in a separate thread so it doesn't slow down the hot path.
     */
    @Async
    public void archiveToMongo(String avatarId, String activity) {
        try {
            Memory memory = new Memory();
            memory.setAvatarId(avatarId);
            memory.setActivity(activity);
            memory.setTimestamp(LocalDateTime.now());
            memoryRepository.save(memory);
            log.debug("Memory archived to MongoDB for avatar={}", avatarId);
        } catch (Exception e) {
            log.warn("Failed to archive memory to MongoDB for avatar={}: {}", avatarId, e.getMessage());
        }
    }

    /**
     * Daily midnight reset — clears MongoDB archive.
     * Redis memories auto-expire via 24h TTL, so no explicit clear needed.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void clearDailyActivities() {
        memoryRepository.deleteAll();
        log.info("Cleared daily MongoDB memory archive at midnight.");
    }
}
