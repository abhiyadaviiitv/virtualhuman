package com.virtualhuman.model;

import lombok.Data;

@Data
public class EmotionalState {
    private double happiness;
    private double stress;
    private double boredom;
    private double energy;
    private double socialSatisfaction;
    private long lastUpdatedEpochMs;

    /**
     * Create a default "neutral" emotional state.
     * Used as the starting point before personality-based initialization.
     */
    public static EmotionalState neutral() {
        EmotionalState state = new EmotionalState();
        state.setHappiness(0.5);
        state.setStress(0.3);
        state.setBoredom(0.2);
        state.setEnergy(0.8);
        state.setSocialSatisfaction(0.5);
        state.setLastUpdatedEpochMs(System.currentTimeMillis());
        return state;
    }

    /**
     * Create an initial emotional state derived from personality traits.
     * Personality shapes the emotional baseline —
     * e.g., high neuroticism → higher starting stress.
     */
    public static EmotionalState fromPersonality(Personality personality) {
        EmotionalState state = new EmotionalState();

        // High neuroticism → more stressed, less happy
        state.setStress(clamp(0.2 + personality.getNeuroticism() * 0.4));
        state.setHappiness(clamp(0.6 - personality.getNeuroticism() * 0.2));

        // High extraversion → lower social satisfaction (needs people sooner)
        state.setSocialSatisfaction(clamp(0.8 - personality.getExtraversion() * 0.5));

        // High openness → gets bored faster (needs novelty)
        state.setBoredom(clamp(0.1 + personality.getOpenness() * 0.3));

        // High conscientiousness → starts with more energy (disciplined)
        state.setEnergy(clamp(0.6 + personality.getConscientiousness() * 0.3));

        state.setLastUpdatedEpochMs(System.currentTimeMillis());
        return state;
    }

    /**
     * Clamp a value between 0.0 and 1.0.
     */
    public static double clamp(double val) {
        return Math.max(0.0, Math.min(1.0, val));
    }

    /**
     * Convert emotion value to a human-readable label for LLM prompts.
     */
    public static String toLabel(double value) {
        if (value >= 0.8)
            return "VERY HIGH";
        if (value >= 0.6)
            return "HIGH";
        if (value >= 0.4)
            return "MODERATE";
        if (value >= 0.2)
            return "LOW";
        return "VERY LOW";
    }
}
