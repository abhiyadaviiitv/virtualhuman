package com.virtualhuman.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualhuman.model.BehaviorRequest;
import com.virtualhuman.model.BehaviorResponse;
import com.virtualhuman.model.EmotionalState;
import com.virtualhuman.model.ReasoningCondition;
import com.virtualhuman.model.WorldState;
import com.virtualhuman.prompt.PromptTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class BehaviorPlannerService {

    private static final Logger log = LoggerFactory.getLogger(BehaviorPlannerService.class);

    private final GroqService groqService;
    private final MemoryService memoryService;
    private final EmotionService emotionService;
    private final GoalService goalService;
    private final SemanticMemoryService semanticMemoryService;
    private final PromptTemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    public BehaviorPlannerService(GroqService groqService,
            MemoryService memoryService,
            EmotionService emotionService,
            GoalService goalService,
            SemanticMemoryService semanticMemoryService,
            PromptTemplateEngine templateEngine) {
        this.groqService = groqService;
        this.memoryService = memoryService;
        this.emotionService = emotionService;
        this.goalService = goalService;
        this.semanticMemoryService = semanticMemoryService;
        this.templateEngine = templateEngine;
        this.objectMapper = new ObjectMapper();
    }

    public BehaviorResponse planBehavior(BehaviorRequest request) {
        WorldState state = request.getWorldState();
        if (state == null) {
            throw new IllegalArgumentException("Invalid payload: 'worldState' object is missing.");
        }

        if (state.getCompletedActivities() == null || state.getCompletedActivities().isEmpty()) {
            state.setCompletedActivities(memoryService.getRecentActivities(request.getAvatarId(), 10));
        }

        EmotionalState emotionalState = emotionService.getCurrentState(
                request.getAvatarId(), state.getPersonality());

        String goals = goalService.buildPromptContext(
                request.getAvatarId());

        String semanticMemories = semanticMemoryService.buildPromptContext(
                request.getAvatarId());

        // Condition Reasoning (Stage 1) — Analytical, low temperature
        ReasoningCondition condition = inferConditions(state, emotionalState, goals, semanticMemories);

        // Behavior Sampling (Stage 2) — Creative, higher temperature
        BehaviorResponse response = sampleBehavior(state, condition, emotionalState, goals,
                semanticMemories);

        if (response != null && response.getActivity() != null) {
            String memoryEntry = response.getActivity() + " with " + response.getObject();
            memoryService.addMemory(request.getAvatarId(), memoryEntry);
            emotionService.applyEventUpdate(request.getAvatarId(), emotionalState, response);
        }

        return response;
    }

    private ReasoningCondition inferConditions(WorldState state, EmotionalState emotionalState,
            String goals, String semanticMemories) {
        String systemMsg = templateEngine.getTemplate("system_message.txt");
        String userPrompt = buildStage1Prompt(state, emotionalState, goals, semanticMemories);

        // Temperature 0.3 for analytical reasoning
        String jsonResponse = groqService.sendPrompt(systemMsg, userPrompt, 0.3);
        log.info("STAGE 1 RAW RESPONSE:\n{}", jsonResponse);

        try {
            String cleanJson = extractJson(jsonResponse);
            log.debug("STAGE 1 CLEAN JSON:\n{}", cleanJson);
            return objectMapper.readValue(cleanJson, ReasoningCondition.class);
        } catch (Exception e) {
            log.error("Stage 1 parsing failed, using fallback", e);
            ReasoningCondition fallback = new ReasoningCondition();
            fallback.setDominantNeed("Social");
            fallback.setUrgencyLevel("Low");
            fallback.setBehaviorStyle("Neutral");
            fallback.setContextSummary("Fallback context: " + state.getScene().getSceneDescription());
            return fallback;
        }
    }

    private BehaviorResponse sampleBehavior(WorldState state, ReasoningCondition condition,
            EmotionalState emotionalState, String goals, String semanticMemories) {
        String systemMsg = templateEngine.getTemplate("system_message.txt");
        String userPrompt = buildStage2Prompt(state, condition, emotionalState, goals, semanticMemories);

        // Temperature 0.8 for creative behavior selection
        String jsonResponse = groqService.sendPrompt(systemMsg, userPrompt, 0.8);
        log.info("STAGE 2 RAW RESPONSE:\n{}", jsonResponse);

        try {
            String cleanJson = extractJson(jsonResponse);
            log.debug("STAGE 2 CLEAN JSON:\n{}", cleanJson);
            return objectMapper.readValue(cleanJson, BehaviorResponse.class);
        } catch (Exception e) {
            log.error("Stage 2 parsing failed, using fallback", e);
            BehaviorResponse fallback = new BehaviorResponse();
            fallback.setNeed(condition.getDominantNeed());
            fallback.setTask("Repose");
            fallback.setActivity("sit");
            fallback.setObject("chair");
            fallback.setAnimation("idle");
            fallback.setDialogue("");
            return fallback;
        }
    }

    private String buildStage1Prompt(WorldState state, EmotionalState emotionalState, String goals,
            String semanticMemories) {
        Map<String, String> vars = new HashMap<>();
        try {
            vars.put("personality", objectMapper.writeValueAsString(state.getPersonality()));
            vars.put("attributes", objectMapper.writeValueAsString(state.getAttributes()));
            vars.put("time", state.getTime());
            vars.put("scene", objectMapper.writeValueAsString(state.getScene()));
            vars.put("completedActivities", String.join(", ", state.getCompletedActivities()));
            vars.put("emotionalState", emotionService.buildPromptContext(emotionalState));
            vars.put("goals", goals);
            vars.put("semanticMemories", semanticMemories);
        } catch (Exception e) {
            log.error("Failed to serialize state data for Stage 1", e);
        }
        return templateEngine.buildPrompt("stage1_condition.txt", vars);
    }

    private String buildStage2Prompt(WorldState state, ReasoningCondition condition, EmotionalState emotionalState,
            String goals, String semanticMemories) {
        Map<String, String> vars = new HashMap<>();
        try {
            vars.put("dominantNeed", condition.getDominantNeed());
            vars.put("urgencyLevel", condition.getUrgencyLevel());
            vars.put("behaviorStyle", condition.getBehaviorStyle());
            vars.put("contextSummary", condition.getContextSummary());
            vars.put("time", state.getTime());
            vars.put("scene", objectMapper.writeValueAsString(state.getScene()));
            vars.put("emotionalState", emotionService.buildPromptContext(emotionalState));
            vars.put("goals", goals);
            vars.put("semanticMemories", semanticMemories);
        } catch (Exception e) {
            log.error("Failed to serialize state data for Stage 2", e);
        }
        return templateEngine.buildPrompt("stage2_behavior.txt", vars);
    }

    private String extractJson(String text) {
        int start = text.indexOf("{");
        int end = text.lastIndexOf("}");
        if (start != -1 && end != -1 && start <= end) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }
}
