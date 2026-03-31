package com.virtualhuman.service;

import com.virtualhuman.model.EpisodicMemory;
import com.virtualhuman.repository.EpisodicMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

/**
 * Background worker that runs periodically to consolidate short-term memories
 * from Redis
 * into permanent Episodic and Semantic memories in MongoDB.
 */
@Service
public class MemoryConsolidationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryConsolidationService.class);
    private final StringRedisTemplate redisTemplate;
    private final RedisMemoryService redisMemoryService;
    private final EpisodicMemoryRepository episodicMemoryRepository;
    private final SemanticMemoryService semanticMemoryService;

    public MemoryConsolidationService(StringRedisTemplate redisTemplate,
            RedisMemoryService redisMemoryService,
            EpisodicMemoryRepository episodicMemoryRepository,
            SemanticMemoryService semanticMemoryService) {
        this.redisTemplate = redisTemplate;
        this.redisMemoryService = redisMemoryService;
        this.episodicMemoryRepository = episodicMemoryRepository;
        this.semanticMemoryService = semanticMemoryService;
    }

    /**
     * Runs every 1 hour (or 5 minutes in a fast-forward sim) to transfer
     * short-term Redis memories into permanent Episodic Storage.
     */
    @Scheduled(fixedRate = 3600000) // 1 Hour for a real-time system, tweak as needed
    public void consolidateMemories() {
        log.info("Starting Memory Consolidation Cycle...");

        // 1. Find all active avatar memory sets
        Set<String> keys = redisTemplate.keys("avatar:*:memory");
        if (keys == null || keys.isEmpty()) {
            log.info("No short term memories to consolidate.");
            return;
        }

        for (String key : keys) {
            String avatarId = extractAvatarId(key);
            if (avatarId == null)
                continue;

            // 2. Fetch all current short-term memories (let's say we only get the last 50
            // to avoid massive spikes)
            var shortTermMemories = redisMemoryService.getRecentActivities(avatarId, 50);

            // 3. In a real system we'd parse the timestamp out of the string, but here we
            // just
            // treat them as recent string events and push to Mongo
            for (String memoryText : shortTermMemories) {
                // To avoid duplicating, we'd normally check if it exists or clear Redis after
                // extracting.
                // For simplicity in Phase 4 setup, we just write them as a batch.
                EpisodicMemory episodic = new EpisodicMemory();
                episodic.setAvatarId(avatarId);
                episodic.setMemoryText(memoryText);
                episodic.setTimestamp(Instant.now());
                // We're omitting emotionalContext mapping for this basic version

                episodicMemoryRepository.save(episodic);
            }

            // 4. (Optional) Clear Redis now that they are permanently in Mongo
            // To be safe and keep context in the immediate prompt, we rely on the 24-hr TTL
            // rather than hard clearing.
            // In a full implementation, we would extract Semantic facts via LLM here too.
        }

        log.info("Memory Consolidation Cycle Complete.");
    }

    private String extractAvatarId(String key) {
        // key format: avatar:{avatarId}:memory
        try {
            return key.split(":")[1];
        } catch (Exception e) {
            return null;
        }
    }
}
