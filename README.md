# Autonomous Cognitive NPC Architecture (Backend + Unity integration)

A production-ready Java Spring Boot backend and Unity VR integration suite that empowers Virtual Human NPCs with deep cognitive autonomy. The system implements a two-stage LLM inference pipeline powered by Groq, calculating emotional decay, life goals, short-term memory (Redis), and permanent semantic identity (MongoDB) to generate high-quality emergent behaviors.

## Features Built in the 7-Phase Upgradation
*   **Dual-Stage Inference Engine**: Splits analysis (needs/emotions) from execution (action generation) using discrete temperatures via the Groq LLaMA-based API.
*   **Mathematical Emotion Engine**: Tracks Stress, Restedness, and Boredom, applying linear time-decay and action-based adjustments.
*   **Advanced Memory System**: 
    *   **Short-Term Working Memory** (Redis): Lightning-fast retrieval of the last N completed actions.
    *   **Semantic / Episodic Memory** (MongoDB): Permanent identity traits and consolidated life events.
*   **Goal-Oriented Autonomy**: NPCs balance rigid long-term goals against immediate physiological limits (e.g., getting burnt out from working too much and autonomously taking a nap).
*   **Real-Time VR Integration**: Replaces HTTP polling with a persistent Raw TCP WebSocket (`ws://localhost:8080/ws/behavior`) bridging the brain directly to Unity `NavMeshAgent` and `Animator` components.

---

## 🚀 Quick Setup (Backend)

### 1. Prerequisites
*   **Java 21** and Maven
*   **MongoDB**: Running on `localhost:27017`
*   **Redis**: Running on `localhost:6379` (`docker run -p 6379:6379 -d redis`)
*   **Groq API Key**: Obtain a free key from console.groq.com.

### 2. Configuration
Open `src/main/resources/application.properties` and add your Groq key:
```properties
groq.api.key=YOUR_GROQ_API_KEY
```

### 3. Run the Brain
Fire up the Spring Boot backend:
```bash
./mvnw spring-boot:run
```

---

## 🎮 Unity VR Integration

The Java Brain comes bundled with 13 C# scripts designed to handle real-time spatial awareness and robotic execution in Unity.

1. Locate the `UnityScripts/` folder inside the project root.
2. Drag the entire folder into your Unity `Assets/Scripts/` directory.
3. Open `UnityScripts/UnitySetupGuide.md` directly inside Unity for step-by-step instructions on wiring up the `SensorySphere`, baking your `NavMesh`, and connecting the WebSocket client.

---

## 🔌 API Documentation

### 🟢 WebSocket `ws://localhost:8080/ws/behavior` (Recommended)
Connect your VR Client (Unity/Unreal) to the raw TCP WebSocket. 
1. Send the `BehaviorRequest` JSON as a text frame to trigger an LLM thought cycle.
2. The server will respond over the same socket with the `BehaviorResponse` JSON containing the `NavMesh` target, `Animator` trigger, and spoken `dialogue`.

### HTTP POST `/behavior/generate`
Standard HTTP polling endpoint for behavior generation (legacy alternative to WebSockets).

#### Request Payload Structure
```json
{
  "avatarId": "npc_001",
  "worldState": {
    "personality": {
      "openness": 0.8,
      "conscientiousness": 0.7,
      "extraversion": 0.3,
      "agreeableness": 0.6,
      "neuroticism": 0.2
    },
    "attributes": {
      "age": 25,
      "occupation": "software engineer",
      "hobbies": ["coding", "gaming"]
    },
    "time": "14:30",
    "completedActivities": [
      "woke up", 
      "entered classroom"
    ],
    "scene": {
      "sceneDescription": "Classroom with desks and laptops",
      "agentLocation": "near desk",
      "objects": [
        { "name": "chair", "distance": 1.2 },
        { "name": "computer", "distance": 0.8 }
      ]
    }
  }
}
```

#### Response Structure
```json
{
  "need": "Physiological",
  "task": "Repose",
  "activity": "sit down and rest",
  "object": "chair",
  "animation": "sit",
  "dialogue": "I need to take a break right now."
}
```

---

## 📊 Observability (Prometheus)
The backend is highly resilient with Circuit Breakers built over the Groq REST client.
Visit `http://localhost:8080/actuator/prometheus` to scrape metrics on LLM latency, HTTP failures, and circuit breaker states.
