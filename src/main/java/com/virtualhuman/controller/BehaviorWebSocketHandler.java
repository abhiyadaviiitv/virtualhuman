package com.virtualhuman.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualhuman.model.BehaviorRequest;
import com.virtualhuman.model.BehaviorResponse;
import com.virtualhuman.service.BehaviorPlannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BehaviorWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BehaviorWebSocketHandler.class);

    // Track active sessions by Avatar ID (if provided) or session ID
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    private final BehaviorPlannerService behaviorPlannerService;
    private final ObjectMapper objectMapper;

    public BehaviorWebSocketHandler(BehaviorPlannerService behaviorPlannerService) {
        this.behaviorPlannerService = behaviorPlannerService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("New WebSocket connection established: {}", session.getId());
        activeSessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WebSocket connection closed: {} (Status: {})", session.getId(), status);
        activeSessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received WS Message from {}: {}", session.getId(), payload);

        try {
            // Parse Unity's request
            BehaviorRequest request = objectMapper.readValue(payload, BehaviorRequest.class);

            // Tag session with Avatar ID if provided for future push notifications
            if (request.getAvatarId() != null) {
                session.getAttributes().put("avatarId", request.getAvatarId());
            }

            // Run the LLM inference loop
            BehaviorResponse response = behaviorPlannerService.planBehavior(request);

            // Send standard JSON response back down the pipe
            String jsonResponse = objectMapper.writeValueAsString(response);
            session.sendMessage(new TextMessage(jsonResponse));

        } catch (Exception e) {
            log.error("Error processing WebSocket message", e);
            sendErrorResponse(session, "Failed to process behavior request: " + e.getMessage());
        }
    }

    private void sendErrorResponse(WebSocketSession session, String errorMsg) {
        try {
            String errorJson = String.format("{\"error\": \"%s\"}", errorMsg.replace("\"", "\\\""));
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(errorJson));
            }
        } catch (IOException e) {
            log.error("Failed to send error response over WS", e);
        }
    }

    /**
     * Optional method that can be called by internal services (e.g. timers, world
     * events)
     * to push immediate asynchronous behavior to a specific avatar without them
     * asking.
     */
    public void pushEventToAvatar(String targetAvatarId, BehaviorResponse response) {
        for (WebSocketSession session : activeSessions.values()) {
            if (targetAvatarId.equals(session.getAttributes().get("avatarId"))) {
                try {
                    String json = objectMapper.writeValueAsString(response);
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                        log.info("Successfully pushed asynchronous event to avatar: {}", targetAvatarId);
                    }
                } catch (IOException e) {
                    log.error("Failed to push event to {}", targetAvatarId, e);
                }
            }
        }
    }
}
