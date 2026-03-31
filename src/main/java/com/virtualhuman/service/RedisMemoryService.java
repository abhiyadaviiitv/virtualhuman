package com.virtualhuman.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RedisMemoryService {

    private static final String KEY_PREFIX = "avatar:";
    private static final String KEY_SUFFIX = ":memory";
    private static final Duration MEMORY_TTL = Duration.ofHours(24);
    private static final DateTimeFormatter TIMESTAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final StringRedisTemplate redisTemplate;

    public RedisMemoryService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Add a memory entry to the Redis sorted set for the given avatar.
     * Score = current epoch millis (for ordering by time).
     * Member = "activity|timestamp" (pipe-delimited for easy parsing).
     */
    public void addMemory(String avatarId, String activity) {
        String key = buildKey(avatarId);
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
        String member = activity + "|" + timestamp;
        double score = System.currentTimeMillis();

        redisTemplate.opsForZSet().add(key, member, score);
        redisTemplate.expire(key, MEMORY_TTL);
    }

    /**
     * Retrieve the most recent N activities for the avatar (newest first).
     * Uses ZREVRANGE on the sorted set.
     */
    public List<String> getRecentActivities(String avatarId, int limit) {
        String key = buildKey(avatarId);
        Set<String> results = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);

        if (results == null || results.isEmpty()) {
            return Collections.emptyList();
        }

        return results.stream()
                .map(entry -> entry.contains("|") ? entry.substring(0, entry.lastIndexOf("|")) : entry)
                .collect(Collectors.toList());
    }

    /**
     * Clear all memories for a specific avatar.
     */
    public void clearMemories(String avatarId) {
        redisTemplate.delete(buildKey(avatarId));
    }

    /**
     * Get the total number of memories stored for an avatar.
     */
    public long getMemoryCount(String avatarId) {
        Long count = redisTemplate.opsForZSet().zCard(buildKey(avatarId));
        return count != null ? count : 0;
    }

    private String buildKey(String avatarId) {
        return KEY_PREFIX + avatarId + KEY_SUFFIX;
    }
}
