package com.virtualhuman.service;

import com.virtualhuman.model.BehaviorResponse;
import com.virtualhuman.model.EmotionalState;
import com.virtualhuman.model.Personality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.virtualhuman.model.EmotionalState.clamp;

@Service
public class EmotionService {

    private static final Logger log = LoggerFactory.getLogger(EmotionService.class);
    private static final String KEY_PREFIX = "avatar:";
    private static final String KEY_SUFFIX = ":emotion";

    private final StringRedisTemplate redisTemplate;

    public EmotionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Get the current emotional state for an avatar.
     * If none exists in Redis (first request), initialize from personality traits.
     * Automatically applies time-based drift before returning.
     */
    public EmotionalState getCurrentState(String avatarId, Personality personality) {
        String key = buildKey(avatarId);
        Map<Object, Object> data = redisTemplate.opsForHash().entries(key);

        EmotionalState state;
        if (data.isEmpty()) {
            log.info("No emotional state in Redis for avatar={}. Initializing from personality.", avatarId);
            state = EmotionalState.fromPersonality(personality);
            saveToRedis(avatarId, state);
            return state;
        }

        state = fromRedisMap(data);
        state = applyTimeDrift(state);
        log.debug("Emotional state loaded for avatar={}: happiness={}, stress={}, boredom={}, energy={}, social={}",
                avatarId, fmt(state.getHappiness()), fmt(state.getStress()),
                fmt(state.getBoredom()), fmt(state.getEnergy()), fmt(state.getSocialSatisfaction()));

        return state;
    }

    /**
     * Apply time-based passive drift.
     * Emotions naturally move toward their baselines over time:
     * - Happiness → 0.5 (neutral)
     * - Boredom → 1.0 (rises when idle)
     * - Energy → 0.2 (depletes)
     * - Stress → 0.3 (relaxes slowly)
     * - Social satisfaction → 0.0 (loneliness grows)
     */
    public EmotionalState applyTimeDrift(EmotionalState state) {
        long now = System.currentTimeMillis();
        long elapsedMs = now - state.getLastUpdatedEpochMs();
        double minutesElapsed = elapsedMs / 60000.0;

        if (minutesElapsed < 0.5) {
            return state;
        }

        double decayFactor = Math.exp(-0.005 * minutesElapsed);

        state.setHappiness(clamp(0.5 + (state.getHappiness() - 0.5) * decayFactor));
        state.setBoredom(clamp(1.0 - (1.0 - state.getBoredom()) * decayFactor));
        state.setEnergy(clamp(0.2 + (state.getEnergy() - 0.2) * decayFactor));
        state.setStress(clamp(0.3 + (state.getStress() - 0.3) * decayFactor));
        state.setSocialSatisfaction(clamp(state.getSocialSatisfaction() * decayFactor));

        state.setLastUpdatedEpochMs(now);

        log.debug("Time drift applied: {} min elapsed, decayFactor={}", fmt(minutesElapsed), fmt(decayFactor));
        return state;
    }

    /**
     * Apply event-driven emotional updates based on the chosen behavior.
     * Maps the selected activity to emotional changes using a rule table.
     * Saves updated state to Redis.
     */
    public EmotionalState applyEventUpdate(String avatarId, EmotionalState state, BehaviorResponse behavior) {
        if (behavior == null || behavior.getActivity() == null) {
            return state;
        }

        String activity = behavior.getActivity().toLowerCase();
        String task = behavior.getTask() != null ? behavior.getTask().toLowerCase() : "";

        // --- Activity-to-Emotion Mapping Rules ---

        if (matches(activity, task, "eat", "nourish", "drink", "cook")) {
            state.setHappiness(clamp(state.getHappiness() + 0.10));
            state.setStress(clamp(state.getStress() - 0.05));
            state.setBoredom(clamp(state.getBoredom() - 0.05));
            state.setEnergy(clamp(state.getEnergy() + 0.15));
        } else if (matches(activity, task, "sit", "rest", "sleep", "nap", "repose", "relax")) {
            state.setHappiness(clamp(state.getHappiness() + 0.05));
            state.setStress(clamp(state.getStress() - 0.15));
            state.setBoredom(clamp(state.getBoredom() + 0.10));
            state.setEnergy(clamp(state.getEnergy() + 0.25));
        } else if (matches(activity, task, "exercise", "walk", "run", "jog", "stretch")) {
            state.setHappiness(clamp(state.getHappiness() + 0.15));
            state.setStress(clamp(state.getStress() - 0.10));
            state.setBoredom(clamp(state.getBoredom() - 0.20));
            state.setEnergy(clamp(state.getEnergy() - 0.15));
        } else if (matches(activity, task, "talk", "socialize", "chat", "greet", "ask", "discuss")) {
            state.setHappiness(clamp(state.getHappiness() + 0.20));
            state.setStress(clamp(state.getStress() - 0.05));
            state.setBoredom(clamp(state.getBoredom() - 0.25));
            state.setEnergy(clamp(state.getEnergy() - 0.05));
            state.setSocialSatisfaction(clamp(state.getSocialSatisfaction() + 0.30));
        } else if (matches(activity, task, "work", "study", "code", "write", "focus")) {
            state.setHappiness(clamp(state.getHappiness() - 0.05));
            state.setStress(clamp(state.getStress() + 0.10));
            state.setBoredom(clamp(state.getBoredom() - 0.10));
            state.setEnergy(clamp(state.getEnergy() - 0.10));
        } else if (matches(activity, task, "read", "use_laptop", "browse", "use laptop")) {
            state.setHappiness(clamp(state.getHappiness() + 0.05));
            state.setStress(clamp(state.getStress() + 0.05));
            state.setBoredom(clamp(state.getBoredom() - 0.15));
            state.setEnergy(clamp(state.getEnergy() - 0.05));
        } else if (matches(activity, task, "play", "game", "entertain", "watch", "listen", "music")) {
            state.setHappiness(clamp(state.getHappiness() + 0.20));
            state.setStress(clamp(state.getStress() - 0.15));
            state.setBoredom(clamp(state.getBoredom() - 0.30));
            state.setEnergy(clamp(state.getEnergy() - 0.10));
        } else {
            // Unknown activity — small neutral effect
            state.setBoredom(clamp(state.getBoredom() - 0.05));
            state.setEnergy(clamp(state.getEnergy() - 0.03));
        }

        state.setLastUpdatedEpochMs(System.currentTimeMillis());
        saveToRedis(avatarId, state);

        log.info(
                "Emotion updated for avatar={} after activity='{}': happiness={}, stress={}, boredom={}, energy={}, social={}",
                avatarId, activity, fmt(state.getHappiness()), fmt(state.getStress()),
                fmt(state.getBoredom()), fmt(state.getEnergy()), fmt(state.getSocialSatisfaction()));

        return state;
    }

    /**
     * Build a human-readable emotional context block for LLM prompts.
     */
    public String buildPromptContext(EmotionalState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Emotional State:\n");
        sb.append(String.format("- Happiness: %.2f (%s)\n", state.getHappiness(),
                EmotionalState.toLabel(state.getHappiness())));
        sb.append(String.format("- Stress: %.2f (%s)\n", state.getStress(), EmotionalState.toLabel(state.getStress())));
        sb.append(String.format("- Boredom: %.2f (%s)\n", state.getBoredom(),
                EmotionalState.toLabel(state.getBoredom())));
        sb.append(String.format("- Energy: %.2f (%s)\n", state.getEnergy(), EmotionalState.toLabel(state.getEnergy())));
        sb.append(String.format("- Social Satisfaction: %.2f (%s)\n", state.getSocialSatisfaction(),
                EmotionalState.toLabel(state.getSocialSatisfaction())));
        sb.append("\nConsider these emotional states when inferring the dominant need.\n");
        sb.append(
                "A bored NPC with low energy is unlikely to choose work. A lonely NPC may seek social interaction.\n");
        return sb.toString();
    }

    // --- Redis Persistence ---

    private void saveToRedis(String avatarId, EmotionalState state) {
        String key = buildKey(avatarId);
        Map<String, String> map = Map.of(
                "happiness", String.valueOf(state.getHappiness()),
                "stress", String.valueOf(state.getStress()),
                "boredom", String.valueOf(state.getBoredom()),
                "energy", String.valueOf(state.getEnergy()),
                "socialSatisfaction", String.valueOf(state.getSocialSatisfaction()),
                "lastUpdatedEpochMs", String.valueOf(state.getLastUpdatedEpochMs()));
        redisTemplate.opsForHash().putAll(key, map);
    }

    private EmotionalState fromRedisMap(Map<Object, Object> data) {
        EmotionalState state = new EmotionalState();
        state.setHappiness(parseDouble(data, "happiness", 0.5));
        state.setStress(parseDouble(data, "stress", 0.3));
        state.setBoredom(parseDouble(data, "boredom", 0.2));
        state.setEnergy(parseDouble(data, "energy", 0.8));
        state.setSocialSatisfaction(parseDouble(data, "socialSatisfaction", 0.5));
        state.setLastUpdatedEpochMs(parseLong(data, "lastUpdatedEpochMs", System.currentTimeMillis()));
        return state;
    }

    // --- Helpers ---

    private boolean matches(String activity, String task, String... keywords) {
        for (String kw : keywords) {
            if (activity.contains(kw) || task.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private double parseDouble(Map<Object, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val == null)
            return defaultVal;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private long parseLong(Map<Object, Object> map, String key, long defaultVal) {
        Object val = map.get(key);
        if (val == null)
            return defaultVal;
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private String fmt(double val) {
        return String.format("%.2f", val);
    }

    private String buildKey(String avatarId) {
        return KEY_PREFIX + avatarId + KEY_SUFFIX;
    }
}
