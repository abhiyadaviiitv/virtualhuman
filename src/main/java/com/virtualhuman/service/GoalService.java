package com.virtualhuman.service;

import com.virtualhuman.model.Goal;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoalService {

    // For testing and prototyping, we use an in-memory map.
    // In Phase 4+, these could be stored in MongoDB.
    private final Map<String, List<Goal>> activeGoals = new ConcurrentHashMap<>();

    public GoalService() {
        // Pre-seed default goals for testing
        activeGoals.put("avatar-001", List.of(
                new Goal("g1", "Career Growth", "Work hard to excel at current software projects", 0.3,
                        Goal.Priority.HIGH),
                new Goal("g2", "Healthy Lifestyle", "Exercise regularly and eat well", 0.6, Goal.Priority.MEDIUM)));
    }

    /**
     * Gets all active goals for the avatar to prioritize their behavior.
     */
    public List<Goal> getActiveGoals(String avatarId) {
        // Fallback to "avatar-001" seed data for testing if missing
        return activeGoals.getOrDefault(avatarId, activeGoals.get("avatar-001"));
    }

    /**
     * Formats overarching goals into a readable string block for the LLM prompt.
     */
    public String buildPromptContext(String avatarId) {
        List<Goal> goals = getActiveGoals(avatarId);

        StringBuilder sb = new StringBuilder();
        sb.append("Current Life Goals:\n");
        if (goals.isEmpty()) {
            sb.append("- None\n");
        } else {
            for (Goal g : goals) {
                sb.append(String.format("- %s (%s Priority): %s\n", g.getTitle(), g.getPriority(), g.getDescription()));
            }
        }

        sb.append(
                "\nINSTRUCTION TO LLM: The NPC is COMPLETELY AUTONOMOUS. Decide how they should spend their time right now to balance these long-term goals against their immediate emotional needs.");

        return sb.toString();
    }
}
