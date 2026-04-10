# Autonomous Cognitive NPC Architecture — Complete System Explanation

> This document explains the **complete end-to-end flow** of the Virtual Human system — from the moment Unity collects sensory data, through the Java backend "brain", the database layers, the LLM reasoning pipeline, and back to Unity where the 3D character physically executes the decision.

---

## Table of Contents
1. [System Overview](#1-system-overview)
2. [Tech Stack](#2-tech-stack)
3. [Architecture Diagram](#3-architecture-diagram)
4. [Unity Side — The Body & Senses](#4-unity-side--the-body--senses)
5. [Communication Layer — WebSocket Bridge](#5-communication-layer--websocket-bridge)
6. [Java Backend — The Brain](#6-java-backend--the-brain)
7. [Database Layer — Memory Systems](#7-database-layer--memory-systems)
8. [LLM Integration — Groq API](#8-llm-integration--groq-api)
9. [Emotional State Engine](#9-emotional-state-engine)
10. [Prompt Engineering — The Two-Stage Pipeline](#10-prompt-engineering--the-two-stage-pipeline)
11. [Complete End-to-End Flow (Step by Step)](#11-complete-end-to-end-flow-step-by-step)
12. [A Real Scenario Walkthrough](#12-a-real-scenario-walkthrough)
13. [Resilience & Observability](#13-resilience--observability)
14. [File Reference Map](#14-file-reference-map)
15. [How Input is Gathered — From Unity to JSON](#15-how-input-is-gathered--from-unity-to-json)
16. [How Semantic Memory Works (Detailed)](#16-how-semantic-memory-works-detailed)
17. [Invigilator Demo Guide — Testing Without Unity](#17-invigilator-demo-guide--testing-without-unity)

---

## 1. System Overview

This system is the **brain** for an autonomous NPC (Non-Player Character) living inside a Unity 3D simulation. The NPC is not scripted — it has no predefined behavior tree or state machine. Instead, every few seconds, it collects what it can see, how it feels, what it remembers, and its personality traits, sends all of that to a Java backend, which uses an LLM (Large Language Model) to reason about what a real human would do in that situation, and sends back a concrete physical action (walk to the sofa, eat an apple, say "I'm exhausted").

**The NPC autonomously:**
- Perceives its environment (objects, distances)
- Tracks time (simulated 24-hour day/night cycle)
- Remembers what it did recently (short-term memory in Redis)
- Remembers permanent facts about itself (semantic memory in MongoDB)
- Feels emotions that change over time (mathematical emotion engine)
- Has personality traits that shape its baseline mood and decisions
- Has life goals that guide long-term behavior
- Makes decisions via a two-stage LLM reasoning pipeline
- Physically executes decisions using NavMesh pathfinding and animations

---

## 2. Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Game Client** | Unity (C#) | 3D simulation, NPC body, sensory input, behavior execution |
| **Communication** | Raw TCP WebSocket | Persistent, low-latency bidirectional connection |
| **Backend Framework** | Spring Boot 3 (Java 21) | REST + WebSocket server, service orchestration |
| **LLM Provider** | Groq API (LLaMA 3.3 70B) | Natural language reasoning for behavior generation |
| **Hot Cache** | Redis (Sorted Sets + Hashes) | Short-term memory (~0.5ms), emotional state persistence |
| **Cold Storage** | MongoDB Atlas | Long-term episodic memory, semantic memory, avatar profiles |
| **Resilience** | Resilience4j | Circuit breaker + retry for LLM API calls |
| **Observability** | Spring Actuator + Prometheus | Health checks, metrics export |

---

## 3. Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                        UNITY (C# Game Client)                     │
│                                                                    │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────┐ │
│  │ NPCProfile  │  │SensorySphere │  │DayNightClock │  │Activity│ │
│  │ (Personality│  │ (Detects     │  │ (Simulated   │  │Tracker │ │
│  │  & DNA)     │  │  objects)    │  │  24hr clock) │  │        │ │
│  └──────┬──────┘  └──────┬───────┘  └──────┬───────┘  └───┬────┘ │
│         │                │                  │              │       │
│         ▼                ▼                  ▼              ▼       │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │              WorldStateBuilder.BuildRequestJson()          │    │
│  │  Combines all sensory data into a single JSON payload      │    │
│  └───────────────────────────┬────────────────────────────────┘    │
│                              │                                     │
│  ┌───────────────────────────▼────────────────────────────────┐    │
│  │              BrainWebSocketClient                          │    │
│  │  Sends JSON over WebSocket every ~10 seconds               │    │
│  └───────────────────────────┬────────────────────────────────┘    │
│                              │                                     │
│  ┌───────────────────────────▼────────────────────────────────┐    │
│  │              NPCBehaviorExecutor                           │    │
│  │  Receives response → NavMesh walk → Animate → Dialogue    │    │
│  └────────────────────────────────────────────────────────────┘    │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ WebSocket (ws://localhost:8080/ws/behavior)
                               ▼
┌──────────────────────────────────────────────────────────────────┐
│                   JAVA SPRING BOOT BACKEND                        │
│                                                                    │
│  ┌───────────────────────────────────────────────────────────┐     │
│  │  BehaviorWebSocketHandler                                 │     │
│  │  Parses JSON → Runs inference pipeline → Returns response │     │
│  └──────────────────────────┬────────────────────────────────┘     │
│                             ▼                                      │
│  ┌───────────────────────────────────────────────────────────┐     │
│  │  BehaviorPlannerService (THE BRAIN)                       │     │
│  │                                                           │     │
│  │  1. Load memories from Redis/MongoDB                      │     │
│  │  2. Calculate emotional state (time-drift + personality)  │     │
│  │  3. Load life goals                                       │     │
│  │  4. Load semantic memories (permanent facts)              │     │
│  │  5. Stage 1: LLM infers internal condition (T=0.3)       │     │
│  │  6. Stage 2: LLM selects concrete action (T=0.8)         │     │
│  │  7. Update emotions based on chosen action                │     │
│  │  8. Save activity to memory                               │     │
│  └──┬──────┬──────┬──────┬──────┬────────────────────────────┘     │
│     │      │      │      │      │                                  │
│     ▼      ▼      ▼      ▼      ▼                                  │
│  ┌─────┐┌─────┐┌─────┐┌─────┐┌──────┐                             │
│  │Groq ││Emot.││Mem. ││Goal ││Seman.│                             │
│  │Svc  ││Svc  ││Svc  ││Svc  ││Mem.  │                             │
│  │(LLM)││     ││     ││     ││Svc   │                             │
│  └──┬──┘└──┬──┘└──┬──┘└─────┘└──┬───┘                             │
│     │      │      │              │                                  │
│     ▼      ▼      ▼              ▼                                  │
│  ┌─────┐┌──────────────┐ ┌──────────────┐                          │
│  │Groq ││    Redis     │ │   MongoDB    │                          │
│  │ API ││  (Hot Cache) │ │ (Cold Store) │                          │
│  └─────┘└──────────────┘ └──────────────┘                          │
└──────────────────────────────────────────────────────────────────┘
```

---

## 4. Unity Side — The Body & Senses

Unity acts as the NPC's physical body. It collects sensory data and executes the backend's decisions. There are 6 core C# scripts:

### 4.1 NPCProfile (The DNA)

**File:** `UnityScripts/NPCProfile.cs`

A Unity `ScriptableObject` that stores the NPC's **permanent, unchanging identity**. You configure this once in the Unity Inspector:
- **Avatar ID** — Unique identifier (e.g., `"npc_001"`)
- **Big Five Personality (OCEAN)** — Five floats (0.0–1.0): Openness, Conscientiousness, Extraversion, Agreeableness, Neuroticism
- **Attributes** — Age, occupation, hobbies

These values never change at runtime. They define *who the NPC fundamentally is*.

### 4.2 SensorySphere (The Eyes)

**File:** `UnityScripts/SensorySphere.cs`

A large invisible `SphereCollider` (set to `isTrigger`, radius ~8 meters) centered on the NPC. It functions as the NPC's field of vision:

1. **OnTriggerEnter** — When a scene object enters the sphere, it's added to the `visibleObjects` list
2. **OnTriggerExit** — When it leaves, it's removed
3. **GetNearbyObjects()** — Returns all visible objects as `{name, distance}` pairs sorted by distance (nearest first)

Objects must have the `InteractableObject` component to be detected. Each `InteractableObject` has a human-readable `objectName` string (e.g., `"sofa"`, `"computer"`, `"refrigerator"`).

### 4.3 DayNightClock (The Clock)

**File:** `UnityScripts/DayNightClock.cs`

A singleton that simulates a 24-hour day/night cycle. By default, 1 real second = 1 in-game minute, so a full day passes in 24 real minutes.

- Exposes `CurrentTime` as a formatted `"HH:mm"` string
- Optionally rotates a Directional Light to simulate sun movement
- The time string is sent to the backend with every behavior request, so the LLM knows "it's 14:30" and can make time-appropriate decisions (sleep at night, work during the day)

### 4.4 ActivityTracker (The Short-Term Memory)

**File:** `UnityScripts/ActivityTracker.cs`

Keeps a rolling list of the last 10 completed activities on the Unity side. When the `NPCBehaviorExecutor` finishes an action, it logs it here (e.g., `"eat with refrigerator"`, `"sit with sofa"`). This list is sent to the backend as `completedActivities` so the LLM knows what the NPC just did and avoids repetition.

### 4.5 WorldStateBuilder (The Data Assembler)

**File:** `UnityScripts/WorldStateBuilder.cs`

The central orchestrator that reads data from all the above components and packs it into a single `BehaviorRequestData` JSON object:

```
WorldStateBuilder.BuildRequestJson()
  ├── NPCProfile        → personality + attributes
  ├── DayNightClock      → time ("14:30")
  ├── SensorySphere      → scene.objects [{name, distance}, ...]
  ├── ActivityTracker    → completedActivities ["ate lunch", "worked"]
  └── NavMeshAgent       → scene.agentLocation ("near computer")
```

### 4.6 NPCBehaviorExecutor (The Muscles)

**File:** `UnityScripts/NPCBehaviorExecutor.cs`

The output handler. When the backend returns a `BehaviorResponse`, this script translates it into physical Unity actions:

1. **Parse response** — Read `object`, `animation`, `dialogue`
2. **Find target** — Look up the named object via `SensorySphere.FindObjectByName()`
3. **Navigate** — Set the `NavMeshAgent.SetDestination()` to walk toward the object
4. **Wait for arrival** — Monitor `remainingDistance < 1.5m`
5. **Animate** — Trigger the appropriate `Animator` state (e.g., `"sit"`, `"eat"`, `"wave"`)
6. **Show dialogue** — Display `dialogue` string in a floating TextMeshPro bubble above the NPC
7. **Log activity** — Tell `ActivityTracker` what the NPC just did
8. **Return to idle** — Signal `IsBusy = false` so the next brain request can fire

---

## 5. Communication Layer — WebSocket Bridge

### Why WebSocket Instead of HTTP?

The original system used HTTP POST polling (`POST /behavior/generate`). This was replaced with a **persistent raw TCP WebSocket** for several reasons:
- **Lower latency** — No connection setup overhead per request
- **Server-initiated push** — The backend can asynchronously "wake up" an NPC via `pushEventToAvatar()` without Unity asking
- **Connection reuse** — One socket handles the entire simulation session

### How It Works

**Unity Side** — `BrainWebSocketClient.cs`:
1. On `Start()`, connects to `ws://localhost:8080/ws/behavior`
2. Every `requestInterval` seconds (default 10), calls `WorldStateBuilder.BuildRequestJson()` and sends the JSON as a WebSocket text frame
3. A background `ReceiveLoop()` listens for the server's JSON response
4. When a response arrives, dispatches to `NPCBehaviorExecutor.ExecuteBehavior()` on the main Unity thread via `UnityMainThreadDispatcher`
5. Auto-reconnects on disconnect with a 5-second retry

**Java Side** — `BehaviorWebSocketHandler.java`:
1. `WebSocketConfig.java` exposes the endpoint at `/ws/behavior` with CORS `*`
2. On receiving a text message, deserializes it to `BehaviorRequest`
3. Passes it through the full `BehaviorPlannerService.planBehavior()` inference pipeline
4. Serializes the `BehaviorResponse` and sends it back down the same socket

### The JSON Payload

**Request (Unity → Backend):**
```json
{
  "avatarId": "npc_001",
  "worldState": {
    "personality": {
      "openness": 0.8,
      "conscientiousness": 0.6,
      "extraversion": 0.4,
      "agreeableness": 0.7,
      "neuroticism": 0.3
    },
    "attributes": { "age": 25, "occupation": "software engineer", "hobbies": ["coding", "gaming"] },
    "time": "14:30",
    "completedActivities": ["ate lunch", "worked on computer for 3 hours"],
    "scene": {
      "sceneDescription": "A cozy apartment with living room and kitchen",
      "agentLocation": "near computer",
      "objects": [
        { "name": "computer", "distance": 0.8 },
        { "name": "sofa", "distance": 3.2 },
        { "name": "refrigerator", "distance": 5.1 }
      ]
    }
  }
}
```

**Response (Backend → Unity):**
```json
{
  "need": "Physiological",
  "task": "Repose",
  "activity": "sit",
  "object": "sofa",
  "animation": "sit",
  "dialogue": "I've been coding for hours, I need a break."
}
```

---

## 6. Java Backend — The Brain

The Spring Boot backend is the NPC's cognitive processing center. It's organized into these packages:

### Package Structure

```
com.virtualhuman/
├── VirtualHumanApplication.java        ← Entry point (@EnableAsync, @EnableScheduling)
├── config/
│   ├── CorsConfig.java                 ← Allows cross-origin requests from Unity WebGL
│   ├── RedisConfig.java                ← StringRedisTemplate bean
│   ├── WebClientConfig.java            ← HTTP client for Groq API (with timeouts)
│   └── WebSocketConfig.java            ← Registers /ws/behavior endpoint
├── controller/
│   ├── BehaviorController.java         ← Legacy HTTP POST endpoint
│   └── BehaviorWebSocketHandler.java   ← WebSocket handler (recommended)
├── exception/
│   └── GlobalExceptionHandler.java     ← @ControllerAdvice for friendly error JSON
├── model/
│   ├── AvatarProfile.java              ← MongoDB document for avatar identity
│   ├── BehaviorRequest.java            ← Input DTO (avatarId + worldState)
│   ├── BehaviorResponse.java           ← Output DTO (need, task, activity, object, animation, dialogue)
│   ├── EmotionalState.java             ← 5-dimension emotion model
│   ├── EpisodicMemory.java             ← MongoDB document for life events
│   ├── Goal.java                       ← Life goals with priority levels
│   ├── Memory.java                     ← MongoDB document for daily activities
│   ├── Personality.java                ← OCEAN Big Five trait model
│   ├── ReasoningCondition.java         ← Stage 1 output (dominantNeed, urgency, style)
│   ├── Scene.java / SceneObject.java   ← Environment snapshot
│   ├── SemanticMemory.java             ← Permanent facts about the NPC
│   └── WorldState.java                 ← Complete state container
├── prompt/
│   └── PromptTemplateEngine.java       ← Loads .txt templates, replaces {{variables}}
├── repository/
│   ├── AvatarProfileRepository.java    ← MongoDB CRUD
│   ├── EpisodicMemoryRepository.java   ← MongoDB CRUD
│   ├── MemoryRepository.java           ← MongoDB CRUD
│   └── SemanticMemoryRepository.java   ← MongoDB CRUD
└── service/
    ├── BehaviorPlannerService.java      ← THE BRAIN — orchestrates everything
    ├── EmotionService.java             ← Mathematical emotion engine
    ├── GoalService.java                ← Life goals management
    ├── GroqService.java                ← LLM API client
    ├── MemoryConsolidationService.java ← Background: Redis → MongoDB transfer
    ├── MemoryService.java              ← Redis-first memory with MongoDB fallback
    ├── RedisMemoryService.java         ← Redis sorted set operations
    └── SemanticMemoryService.java      ← Permanent facts retrieval
```

### BehaviorPlannerService — The Orchestrator

This is the single most important class. Its `planBehavior()` method runs the entire cognitive pipeline:

```java
public BehaviorResponse planBehavior(BehaviorRequest request) {
    WorldState state = request.getWorldState();

    // 1. MEMORY — Load recent activities from Redis (fallback: MongoDB)
    state.setCompletedActivities(memoryService.getRecentActivities(avatarId, 10));

    // 2. EMOTIONS — Load current emotional state, apply time-drift
    EmotionalState emotionalState = emotionService.getCurrentState(avatarId, personality);

    // 3. GOALS — Load life goals
    String goals = goalService.buildPromptContext(avatarId);

    // 4. SEMANTIC MEMORY — Load permanent identity facts from MongoDB
    String semanticMemories = semanticMemoryService.buildPromptContext(avatarId);

    // 5. STAGE 1 — LLM infers the NPC's internal psychological condition
    ReasoningCondition condition = inferConditions(state, emotionalState, goals, semanticMemories);

    // 6. STAGE 2 — LLM selects a concrete physical action
    BehaviorResponse response = sampleBehavior(state, condition, emotionalState, goals, semanticMemories);

    // 7. POST-EXECUTION — Save memory + update emotional state
    memoryService.addMemory(avatarId, response.getActivity() + " with " + response.getObject());
    emotionService.applyEventUpdate(avatarId, emotionalState, response);

    return response;
}
```

---

## 7. Database Layer — Memory Systems

The NPC has **three memory layers**, mimicking human cognitive memory:

### 7.1 Short-Term Memory (Redis)

**Service:** `RedisMemoryService.java`

Stores the NPC's *today's activities* in a Redis **Sorted Set**.

```
Key:    avatar:{avatarId}:memory
Type:   Sorted Set
Score:  System.currentTimeMillis()  (for chronological ordering)
Member: "ate with Apple|2026-03-28T15:30:00"
TTL:    24 hours (auto-expiry)
```

- **Write:** `ZADD` with timestamp score + pipe-delimited member
- **Read:** `ZREVRANGE` (newest first, limit 10) → returns `["ate with Apple", "worked on computer"]`
- **Speed:** ~0.5ms reads, critical for every-10-second polling

The `MemoryService.java` wraps this with an **async write-through to MongoDB**: every Redis write also fires an `@Async` MongoDB save so nothing is lost if Redis restarts.

### 7.2 Episodic Memory (MongoDB)

**Service:** `MemoryConsolidationService.java`

A background `@Scheduled` job runs every hour and transfers short-term Redis memories into permanent `EpisodicMemory` documents in MongoDB. This is like "sleeping on it" — the NPC's daily experiences become consolidated long-term memories.

```java
@Scheduled(fixedRate = 3600000)  // Every 1 hour
public void consolidateMemories() {
    // For each avatar: read Redis memories → save as EpisodicMemory in MongoDB
}
```

### 7.3 Semantic Memory (MongoDB)

**Service:** `SemanticMemoryService.java`

Permanent, high-importance **facts about the NPC's identity and world**. These never expire. Examples:
- *"I really enjoy coding and find it relaxing."*
- *"I get stressed when I am hungry."*
- *"I consider the sofa as my primary place to rest."*

Each fact has an `importance` score (0.0–1.0). The top 5 most important facts are injected into every LLM prompt, so the NPC always remembers core aspects of its identity.

### Memory Flow Diagram

```
[NPC does an action]
        │
        ▼
  ┌───────────┐     async      ┌───────────┐
  │   Redis   │ ──────────────▶│  MongoDB   │
  │ (Sorted   │     write-     │ (Memory    │
  │  Set)     │     through    │  collection│
  │           │                │            │
  │ 24h TTL   │                │ Permanent  │
  │ ~0.5ms    │                │ Archival   │
  └───────────┘                └─────┬──────┘
                                     │
                           @Scheduled hourly
                                     │
                                     ▼
                              ┌──────────────┐
                              │  Episodic    │
                              │  Memory      │
                              │  (MongoDB)   │
                              └──────────────┘
                              ┌──────────────┐
                              │  Semantic    │
                              │  Memory      │
                              │  (MongoDB)   │
                              │  (Permanent) │
                              └──────────────┘
```

---

## 8. LLM Integration — Groq API

**Service:** `GroqService.java`

The system uses the **Groq API** (which hosts LLaMA 3.3 70B) as its LLM brain. Groq provides extremely fast inference (~200ms per request) compared to traditional cloud LLM providers.

### How It Works

1. Constructs a chat completion request with `system` + `user` messages
2. Sends it to `https://api.groq.com/openai/v1/chat/completions`
3. Uses `response_format: { type: "json_object" }` to enforce structured JSON output
4. Parses the response and extracts the `choices[0].message.content`

### Key Parameters

| Parameter | Stage 1 (Condition) | Stage 2 (Behavior) |
|---|---|---|
| Model | `llama-3.3-70b-versatile` | `llama-3.3-70b-versatile` |
| Temperature | `0.3` (Analytical, strict) | `0.8` (Creative, expressive) |
| Max Tokens | 512 | 512 |
| Response Format | JSON Object | JSON Object |

The **temperature difference is intentional**: Stage 1 should be a cold, logical analysis of the NPC's state, while Stage 2 can be creative and varied in choosing what the NPC actually does.

---

## 9. Emotional State Engine

**Service:** `EmotionService.java` | **Model:** `EmotionalState.java`

The NPC has 5 emotional dimensions, each a float between 0.0 and 1.0:

| Dimension | What It Represents |
|---|---|
| `happiness` | General well-being (High → cheerful, Low → withdrawn) |
| `stress` | Mental pressure (High → irritable, avoidant) |
| `boredom` | Need for stimulation (High → seeks novelty) |
| `energy` | Physical/mental stamina (High → active, Low → rest) |
| `socialSatisfaction` | Fulfillment from interaction (Low → seeks people) |

### Mechanism 1: Personality-Based Initialization

On the NPC's first-ever request, emotions are derived from their personality:

```java
// High neuroticism → more initial stress, less happiness
stress = 0.2 + neuroticism × 0.4
happiness = 0.6 - neuroticism × 0.2

// High extraversion → lower social satisfaction (needs people sooner)
socialSatisfaction = 0.8 - extraversion × 0.5

// High openness → gets bored faster
boredom = 0.1 + openness × 0.3

// High conscientiousness → starts with more energy
energy = 0.6 + conscientiousness × 0.3
```

### Mechanism 2: Time-Based Drift (Passive)

Every time a request comes in, **before** the LLM runs, the system calculates how many minutes have passed and applies exponential decay:

```java
decayFactor = e^(-0.005 × minutesElapsed)

happiness → drifts toward 0.5 (neutral)
boredom   → rises toward 1.0 (idle = bored)
energy    → drops toward 0.2 (depletes over time)
stress    → drifts toward 0.3 (relaxes slowly)
socialSatisfaction → decays toward 0.0 (loneliness grows)
```

**Practical effect:** After 2 hours idle, boredom hits ~0.7, energy drops to ~0.4, social satisfaction near 0. The NPC "feels" the passage of time.

### Mechanism 3: Event-Driven Updates (Active)

After the LLM selects a behavior, the chosen activity triggers emotional changes via a rule table:

| Activity | happiness | stress | boredom | energy | social |
|---|:---:|:---:|:---:|:---:|:---:|
| eat / nourish | +0.10 | −0.05 | −0.05 | +0.15 | — |
| sit / rest / sleep | +0.05 | −0.15 | +0.10 | +0.25 | — |
| exercise / walk | +0.15 | −0.10 | −0.20 | −0.15 | — |
| talk / socialize | +0.20 | −0.05 | −0.25 | −0.05 | +0.30 |
| work / study / code | −0.05 | +0.10 | −0.10 | −0.10 | — |
| play / watch / game | +0.20 | −0.15 | −0.30 | −0.10 | — |

All emotional state is persisted in **Redis Hashes** at key `avatar:{id}:emotion`.

### Emergent "Burnout" Behavior

If the NPC works for 5 consecutive hours:
- Energy starts at 1.0, drops by −0.10 each hour → reaches 0.5
- Stress starts at 0.0, rises by +0.10 each hour → reaches 0.5

By hour 6-7, stress crosses 0.7 (HIGH) and energy drops below 0.3 (LOW). The LLM reads these emotional labels in the prompt and autonomously decides to stop working and go rest — even if the NPC's goal says "Get Promoted". **This is emergent behavior**, not hardcoded.

---

## 10. Prompt Engineering — The Two-Stage Pipeline

The backend uses **three prompt template files** located at `src/main/resources/prompts/`:

### System Message (`system_message.txt`)
Sets the LLM's persona for both stages:
> *"You are the internal brain of an autonomous Virtual Human NPC in a 3D simulation. Your goal is to be hyper-realistic, consistent with your personality. You output JSON exclusively."*

### Stage 1: Condition Reasoning (`stage1_condition.txt`)

**Temperature: 0.3** (Analytical, deterministic)

**Purpose:** Act as a behavioral psychologist to infer the NPC's internal state.

**Inputs injected via `{{variable}}` placeholders:**
- `{{personality}}` — OCEAN model JSON
- `{{attributes}}` — Age, occupation, hobbies
- `{{time}}` — Current in-game time
- `{{completedActivities}}` — Recent Redis memories
- `{{scene}}` — Visible objects with distances
- `{{emotionalState}}` — Formatted emotion labels (e.g., "Boredom: 0.70 (HIGH)")
- `{{goals}}` — Life goals with priorities
- `{{semanticMemories}}` — Permanent identity facts

**Output JSON:**
```json
{
  "dominantNeed": "Physiological",    // Maslow's hierarchy level
  "urgencyLevel": "High",             // Low, Medium, High, Critical
  "behaviorStyle": "Lethargic",       // How the NPC should act
  "contextSummary": "After 4 hours of work, high stress and low energy suggest the NPC needs rest."
}
```

### Stage 2: Behavior Sampling (`stage2_behavior.txt`)

**Temperature: 0.8** (Creative, varied)

**Purpose:** Translate the abstract need from Stage 1 into a concrete physical action using available scene objects.

**Additional inputs:** Stage 1's output (`dominantNeed`, `urgencyLevel`, `behaviorStyle`, `contextSummary`)

**Output JSON:**
```json
{
  "need": "Physiological",
  "task": "Repose",
  "activity": "sit",
  "object": "sofa",
  "animation": "sit",
  "dialogue": "I've been coding all day, I need a break."
}
```

### The PromptTemplateEngine

**File:** `prompt/PromptTemplateEngine.java`

A simple but effective template engine that:
1. Loads `.txt` template files from the classpath (`src/main/resources/prompts/`)
2. Caches them in memory
3. Replaces `{{variableName}}` placeholders with actual values from a `Map<String, String>`

---

## 11. Complete End-to-End Flow (Step by Step)

Here is exactly what happens from the moment Unity boots up to the NPC performing an action:

### Step 1: Unity Scene Starts
- `DayNightClock` begins ticking from 07:00
- `SensorySphere` starts detecting nearby `InteractableObject`s
- `BrainWebSocketClient` connects to `ws://localhost:8080/ws/behavior`

### Step 2: Timer Elapses (Every ~10 Seconds)
- `BrainWebSocketClient.Update()` checks: *Is the timer up? Is the NPC not busy?*
- If yes → calls `worldStateBuilder.BuildRequestJson()`

### Step 3: WorldState Assembly
- Reads `NPCProfile` → personality + attributes
- Reads `DayNightClock.CurrentTime` → `"14:30"`
- Reads `SensorySphere.GetNearbyObjects()` → `[{sofa, 3.2}, {computer, 0.8}]`
- Reads `ActivityTracker.GetActivities()` → `["ate lunch", "worked on computer"]`
- Packs everything into a JSON string

### Step 4: WebSocket Send
- JSON sent as a text frame to the backend

### Step 5: Backend Receives the Request
- `BehaviorWebSocketHandler.handleTextMessage()` deserializes `BehaviorRequest`
- Calls `behaviorPlannerService.planBehavior(request)`

### Step 6: Memory Loading
- `MemoryService.getRecentActivities("npc_001", 10)` tries **Redis first**
  - Redis hit → returns activities in ~0.5ms
  - Redis miss (cold start) → falls back to MongoDB
- These memories are injected into the prompt so the LLM sees: *"Recently: ate lunch, worked on computer"*

### Step 7: Emotional State Calculation
- `EmotionService.getCurrentState("npc_001", personality)`:
  - First request ever? → Initialize from personality traits (`fromPersonality()`)
  - Subsequent? → Load from Redis hash, apply time-drift
- **Time-drift** runs the exponential decay math on all 5 dimensions
- Returns emotional state like: *"Boredom: 0.70 (HIGH), Energy: 0.45 (MODERATE)"*

### Step 8: Goal & Semantic Memory Loading
- `GoalService.buildPromptContext("npc_001")` → *"Career Growth (HIGH Priority): Work hard to excel"*
- `SemanticMemoryService.buildPromptContext("npc_001")` → *"I really enjoy coding. I get stressed when hungry."*

### Step 9: Stage 1 — LLM Condition Reasoning
- `PromptTemplateEngine` loads `stage1_condition.txt`, replaces all `{{variables}}`
- `GroqService.sendPrompt(systemMsg, userPrompt, temperature=0.3)`:
  - Sends the full prompt to Groq API
  - Groq returns JSON: `{"dominantNeed": "Physiological", "urgencyLevel": "High", "behaviorStyle": "Lethargic"}`
  - Parsed into `ReasoningCondition` object

### Step 10: Stage 2 — LLM Behavior Sampling
- `PromptTemplateEngine` loads `stage2_behavior.txt`, replaces all `{{variables}}` (including Stage 1 output)
- `GroqService.sendPrompt(systemMsg, userPrompt, temperature=0.8)`:
  - Groq returns JSON: `{"need": "Physiological", "task": "Repose", "activity": "sit", "object": "sofa", "animation": "sit", "dialogue": "I need a break."}`
  - Parsed into `BehaviorResponse` object

### Step 11: Post-Execution Updates
- **Memory** — `memoryService.addMemory("npc_001", "sit with sofa")`
  - Writes to Redis sorted set (immediate)
  - Async write-through to MongoDB (background thread)
- **Emotions** — `emotionService.applyEventUpdate("npc_001", state, response)`
  - "sit/rest" → happiness +0.05, stress −0.15, boredom +0.10, energy +0.25
  - Updated emotions saved back to Redis hash

### Step 12: Response Sent Back Over WebSocket
- `BehaviorWebSocketHandler` serializes `BehaviorResponse` to JSON
- Sends as a WebSocket text frame back to Unity

### Step 13: Unity Receives & Executes
- `BrainWebSocketClient.ReceiveLoop()` gets the JSON
- Dispatches to main thread via `UnityMainThreadDispatcher`
- `NPCBehaviorExecutor.ExecuteBehavior()`:
  1. Shows dialogue: *"I need a break."* (floating TextMeshPro bubble)
  2. Looks up `"sofa"` in `SensorySphere.FindObjectByName()`
  3. `NavMeshAgent.SetDestination(sofa.transform.position)` → NPC walks to sofa
  4. On arrival → triggers `animator.SetTrigger("sit")`
  5. On animation complete → `ActivityTracker.AddActivity("sit with sofa")`
  6. Sets `IsBusy = false` → next brain request can fire

### Step 14: Cycle Repeats
- 10 seconds later, the entire process starts again from Step 2
- But now the NPC's emotions have changed (it rested, stress is lower, energy is higher), memory is updated, and the LLM will make a different decision

---

## 12. A Real Scenario Walkthrough

> **NPC:** 25-year-old software engineer, O=0.8, C=0.6, E=0.4, A=0.7, N=0.3
> **Scene:** An apartment with a computer, sofa, refrigerator, bookshelf, TV

### 07:00 — Morning
- **Emotions:** Fresh start (energy: 0.78, stress: 0.32, boredom: 0.34)
- **Goal:** Career Growth (HIGH priority)
- **LLM decides:** The NPC's conscientiousness (0.6) and career goal push it to work: `"activity": "code", "object": "computer"`

### 11:00 — After 4 Hours of Work
- **Emotions:** Energy dropped to 0.38, Stress rose to 0.72, Boredom at 0.15
- **Semantic memory says:** *"I get stressed when I am hungry"*
- **LLM decides:** High stress + low energy + semantic memory → `"activity": "eat", "object": "refrigerator", "dialogue": "I'm starving, I need to eat something."`

### 14:30 — After Lunch
- **Emotions:** Energy recovered to 0.53, Stress dropped to 0.58, Boredom rising at 0.45
- **LLM decides:** Moderate boredom + the TV is nearby → `"activity": "watch", "object": "television", "dialogue": "Let me take a short break."`

### 17:00 — Evening
- **Emotions:** Social satisfaction has been decaying all day (now 0.12 — VERY LOW)
- **Extraversion:** 0.4 means the NPC eventually craves connection
- **LLM decides:** `"activity": "talk", "object": "none", "dialogue": "I should call someone..."`

### 22:00 — Night
- **Emotions:** Energy at 0.21, Boredom at 0.68
- **LLM decides:** Low energy + late time → `"activity": "sleep", "object": "bed"`

Each of these decisions is **not scripted**. They emerge from the interaction of personality, time, emotions, memories, goals, and the LLM's reasoning.

---

## 13. Resilience & Observability

### Circuit Breaker + Retry (Resilience4j)

The Groq API is external and can fail. `GroqService` has:
- **`@Retry(name = "groq")`** — Retries 3 times with 2-second waits
- **`@CircuitBreaker(name = "groq")`** — Opens after 50% failure rate across 10 calls; waits 30 seconds before half-opening

If everything fails, the `fallbackResponse()` returns `"{}"`, and `BehaviorPlannerService` uses hardcoded fallback actions (sit idle) so Unity never gets a null or error.

### Actuator & Metrics

- `GET /actuator/health` — Health check (Redis, MongoDB, app)
- `GET /actuator/prometheus` — Prometheus-formatted metrics for Grafana dashboards

### WebClient Timeouts

`WebClientConfig.java` configures:
- Connection timeout: 5 seconds
- Response timeout: 15 seconds
- Read/Write timeouts: 15s / 5s

### Global Exception Handler

`GlobalExceptionHandler.java` catches any unhandled exception and returns a clean JSON error response instead of a stack trace, so Unity always gets parseable JSON.

---

## 14. File Reference Map

### Java Backend (`src/main/java/com/virtualhuman/`)

| File | Role |
|---|---|
| `VirtualHumanApplication.java` | Entry point — `@EnableAsync`, `@EnableScheduling` |
| `config/CorsConfig.java` | CORS `*` for Unity WebGL |
| `config/RedisConfig.java` | Redis template bean |
| `config/WebClientConfig.java` | HTTP client with timeouts |
| `config/WebSocketConfig.java` | Registers `/ws/behavior` |
| `controller/BehaviorController.java` | Legacy HTTP endpoint |
| `controller/BehaviorWebSocketHandler.java` | WebSocket handler + push capability |
| `exception/GlobalExceptionHandler.java` | Clean error responses |
| `model/BehaviorRequest.java` | Input: `avatarId` + `worldState` |
| `model/BehaviorResponse.java` | Output: `need`, `task`, `activity`, `object`, `animation`, `dialogue` |
| `model/EmotionalState.java` | 5-dimension emotion model |
| `model/ReasoningCondition.java` | Stage 1 output |
| `model/Personality.java` | OCEAN Big Five traits |
| `model/WorldState.java` | Full state container |
| `model/Goal.java` | Life goals with priorities |
| `model/Memory.java` | MongoDB daily memory document |
| `model/EpisodicMemory.java` | MongoDB long-term event |
| `model/SemanticMemory.java` | MongoDB permanent fact |
| `prompt/PromptTemplateEngine.java` | `{{variable}}` template loading |
| `service/BehaviorPlannerService.java` | **THE BRAIN** — orchestrates everything |
| `service/EmotionService.java` | Mathematical emotion engine |
| `service/GoalService.java` | Life goals management |
| `service/GroqService.java` | Groq LLM API client |
| `service/MemoryService.java` | Redis-first + MongoDB fallback |
| `service/RedisMemoryService.java` | Redis sorted set operations |
| `service/SemanticMemoryService.java` | Permanent facts retrieval |
| `service/MemoryConsolidationService.java` | Background Redis→MongoDB transfer |

### Prompt Templates (`src/main/resources/prompts/`)

| File | Stage | Temperature | Purpose |
|---|---|---|---|
| `system_message.txt` | Both | — | LLM persona definition |
| `stage1_condition.txt` | 1 | 0.3 | Psychological analysis |
| `stage2_behavior.txt` | 2 | 0.8 | Action selection |

### Unity Scripts (`UnityScripts/`)

| File | Role |
|---|---|
| `NPCProfile.cs` | ScriptableObject — personality DNA |
| `SensorySphere.cs` | Trigger collider — NPC's eyes |
| `DayNightClock.cs` | Simulated 24-hour clock |
| `ActivityTracker.cs` | Rolling list of recent actions |
| `WorldStateBuilder.cs` | Assembles all data into JSON |
| `BrainWebSocketClient.cs` | WebSocket connection + send/receive loop |
| `NPCBehaviorExecutor.cs` | NavMesh walk + animate + dialogue |
| `InteractableObject.cs` | Tags scene objects as interactable |
| `UnityMainThreadDispatcher.cs` | Thread-safe callback dispatch |
| `Models/*.cs` | Data models matching backend DTOs |

---

## 15. How Input is Gathered — From Unity to JSON

A common question is: *"How does the system actually get the data in that JSON format?"*

The answer depends on whether you're running with Unity or testing standalone.

### 15.1 With Unity (Production Mode)

No human writes the JSON. Unity **automatically constructs it every 10 seconds** by reading live game data:

| JSON Field | Where It Comes From in Unity | Script |
|---|---|---|
| `avatarId` | `NPCProfile.avatarId` — set once in Inspector | `NPCProfile.cs` |
| `personality.*` | `NPCProfile` ScriptableObject — 5 OCEAN sliders set in Inspector | `NPCProfile.cs` |
| `attributes.*` | `NPCProfile` — age, occupation, hobbies set in Inspector | `NPCProfile.cs` |
| `time` | `DayNightClock.Instance.CurrentTime` — ticks every frame | `DayNightClock.cs` |
| `completedActivities` | `ActivityTracker.GetActivities()` — rolling list of last 10 actions | `ActivityTracker.cs` |
| `scene.sceneDescription` | `WorldStateBuilder.sceneDescription` — set in Inspector | `WorldStateBuilder.cs` |
| `scene.agentLocation` | Computed from `SensorySphere` nearest object + transform | `WorldStateBuilder.cs` |
| `scene.objects[*]` | `SensorySphere.GetNearbyObjects()` — trigger collider detection | `SensorySphere.cs` |

**The flow:**
```
Every 10 seconds:
  BrainWebSocketClient.Update()
    → if timer elapsed AND NPC is idle:
        → WorldStateBuilder.BuildRequestJson()
            → reads NPCProfile (static DNA)
            → reads DayNightClock (dynamic time)
            → reads SensorySphere (dynamic objects nearby)
            → reads ActivityTracker (dynamic past actions)
            → packs into BehaviorRequestData
            → JsonUtility.ToJson()
        → sends JSON over WebSocket
```

**Key insight:** The `scene.objects` array is **dynamic**. If the NPC walks from the bedroom to the kitchen, the SensorySphere's trigger collider detects different objects (refrigerator enters, bed exits), so the next JSON payload automatically reflects the new environment. The LLM will then reason about kitchen objects instead of bedroom objects.

### 15.2 Without Unity (Testing / Demo Mode)

When you don't have Unity running, you simulate the input by writing JSON files manually and sending them via `curl` or the WebSocket test script. This is what the `demo_scenario_*.json` files do — they represent what Unity *would have sent* at different moments in the NPC's day.

You are essentially "pretending to be Unity" by crafting the JSON payload yourself.

---

## 16. How Semantic Memory Works (Detailed)

Semantic memory is the NPC's **permanent identity and knowledge** — facts that never expire. It's the longest-lasting memory layer.

### 16.1 What Is a Semantic Memory?

A `SemanticMemory` is a MongoDB document in the `semantic_memories` collection:

```java
@Document(collection = "semantic_memories")
public class SemanticMemory {
    @Id
    private String id;              // MongoDB auto-generated
    private String avatarId;        // e.g., "test_user1"
    private String fact;            // "I really enjoy coding and find it relaxing."
    private double importance;      // 0.0 to 1.0 (higher = more important)
}
```

### 16.2 How Semantic Memories Are Created

Currently, semantic memories are **seeded at application startup** by `SemanticMemoryService.java`:

```java
private void seedTestData() {
    if (semanticMemoryRepository.count() == 0) {
        semanticMemoryRepository.save(
            new SemanticMemory(null, "test_user1",
                "I really enjoy coding and find it relaxing.", 0.8));
        semanticMemoryRepository.save(
            new SemanticMemory(null, "test_user1",
                "I get stressed when I am hungry.", 0.6));
        semanticMemoryRepository.save(
            new SemanticMemory(null, "test_user1",
                "I consider the sofa as my primary place to rest.", 0.9));
    }
}
```

This seed data is stored once in MongoDB and persists across restarts. In a future production system, these facts could be:
- **Manually entered** by a game designer defining the NPC's backstory
- **Auto-generated** by an LLM summarization job that reads episodic memories and distills patterns (e.g., after the NPC codes for 10 days straight, the system generates: *"I enjoy coding"*)

### 16.3 How Semantic Memories Are Used in the Pipeline

Every time `BehaviorPlannerService.planBehavior()` runs:

1. **Query:** `SemanticMemoryService.buildPromptContext(avatarId)` calls `semanticMemoryRepository.findByAvatarIdOrderByImportanceDesc(avatarId)`
2. **Select Top 5:** Only the 5 highest-importance facts are used (to save LLM token space)
3. **Format:** Facts are formatted as a bullet list:
   ```
   - I consider the sofa as my primary place to rest.
   - I really enjoy coding and find it relaxing.
   - I get stressed when I am hungry.
   ```
4. **Inject:** This text is placed into the `{{semanticMemories}}` placeholder in both Stage 1 and Stage 2 prompts
5. **LLM reads it:** The LLM now knows the NPC's core preferences and will bias decisions accordingly (e.g., when tired, it picks the sofa over the bed because the semantic memory says *"sofa is my primary rest place"*)

### 16.4 The Three Memory Layers Compared

| Layer | Storage | Lifespan | Speed | Content | Example |
|---|---|---|---|---|---|
| **Short-Term** | Redis Sorted Set | 24 hours (TTL) | ~0.5ms | Today's activities | `"ate with refrigerator"` |
| **Episodic** | MongoDB `episodic_memories` | Permanent | ~5-15ms | Consolidated life events | `"Ate apple at 15:30"` |
| **Semantic** | MongoDB `semantic_memories` | Permanent | ~5-15ms | Generalized identity facts | `"I enjoy coding and find it relaxing"` |

---

## 17. Invigilator Demo Guide — Testing Without Unity

You can fully demonstrate the system's intelligence **without Unity** using just `curl` commands or the provided demo script.

### 17.1 Prerequisites

Start these in separate terminals:

```bash
# Terminal 1: Start Redis
docker run -p 6379:6379 -d redis

# Terminal 2: Start the Java backend
cd /home/abhijeet/Desktop/virtualhuman
mvn spring-boot:run
```

Wait for the backend to show `Started VirtualHumanApplication` in the logs.

### 17.2 Option A: Run the Interactive Demo Script

The easiest way — runs 4 scenarios back-to-back, pausing between each:

```bash
cd /home/abhijeet/Desktop/virtualhuman
./demo.sh
```

This sends 4 different JSON payloads and displays the LLM's response for each, proving:
- **Scenario 1 → 2:** Same NPC, different time + activities = different behavior
- **Scenario 2 → 3:** Same exhausted NPC, different scene objects = different object choice
- **Scenario 2 → 4:** Same scene, different personality = completely different behavior style

### 17.3 Option B: Manual curl Commands

Send specific scenarios one at a time:

```bash
# SCENARIO 1: Morning — NPC just woke up at 08:00
curl -s -X POST http://localhost:8080/behavior/generate \
  -H "Content-Type: application/json" \
  -d @demo_scenario_1_morning.json | python3 -m json.tool

# SCENARIO 2: After Work — Same NPC at 18:00, 7+ hours of coding done
curl -s -X POST http://localhost:8080/behavior/generate \
  -H "Content-Type: application/json" \
  -d @demo_scenario_2_after_work.json | python3 -m json.tool

# SCENARIO 3: Different Scene — Same exhausted NPC, but in a PARK
curl -s -X POST http://localhost:8080/behavior/generate \
  -H "Content-Type: application/json" \
  -d @demo_scenario_3_different_scene.json | python3 -m json.tool

# SCENARIO 4: Different Personality — High-neurotic introvert, same scene
curl -s -X POST http://localhost:8080/behavior/generate \
  -H "Content-Type: application/json" \
  -d @demo_scenario_4_high_neurotic.json | python3 -m json.tool
```

### 17.4 Option C: WebSocket Test (via Node.js)

```bash
# Test via WebSocket (uses test2.json as payload)
node testws.js
```

### 17.5 What to Explain to the Invigilator

When presenting results, highlight these points:

1. **"The LLM is NOT hardcoded."** — Show Scenario 1 vs 2. Same NPC, but 7 hours of work changed the `completedActivities` and `time`, so the LLM chose rest instead of work.

2. **"It adapts to the physical environment."** — Show Scenario 2 vs 3. The NPC had the same emotional/mental state but was placed in a park. Instead of choosing `"sofa"`, it chose `"bench"` because that's what was available in the scene.

3. **"Personality changes everything."** — Show Scenario 2 vs 4. Same scene, same time. But the high-neurotic accountant (N=0.9) will show anxiety-driven behavior while the relaxed engineer (N=0.3) shows calm behavior.

4. **"Emotional state is mathematical."** — After Scenario 1 runs, the NPC's emotions are saved to Redis. Run Scenario 2 and point out that the emotions have evolved (stress rose from work, energy dropped). The backend log will show: `Emotion updated for avatar=test_user1 after activity='code': stress=0.42, energy=0.68`

5. **"It has real memory."** — Run Scenario 1, then Scenario 2 immediately after. The backend will pull the Scenario 1 action from Redis memory and feed it to the LLM as recent context, so the LLM knows what happened earlier.

### 17.6 The Reasoning Pipeline is 2-Stage

The system uses a **2-stage reasoning pipeline**, not 3-stage. Here is exactly what happens:

```
┌──────────────────────────────────────────────────────────┐
│                    STAGE 1: CONDITION                     │
│              (Temperature = 0.3 — Analytical)            │
│                                                          │
│  Input:  Personality + Time + Scene + Emotions +         │
│          Memories + Goals + Semantic Facts                │
│                                                          │
│  Role:   "Act as a behavioral psychologist"              │
│                                                          │
│  Output: {                                               │
│    "dominantNeed": "Physiological",                      │
│    "urgencyLevel": "High",                               │
│    "behaviorStyle": "Lethargic",                         │
│    "contextSummary": "After 7 hours of work..."          │
│  }                                                       │
└────────────────────────┬─────────────────────────────────┘
                         │  Stage 1 output feeds into Stage 2
                         ▼
┌──────────────────────────────────────────────────────────┐
│                    STAGE 2: BEHAVIOR                      │
│              (Temperature = 0.8 — Creative)              │
│                                                          │
│  Input:  Stage 1 output + Scene + Emotions + Goals       │
│                                                          │
│  Role:   "Translate the abstract need into a concrete    │
│           action using available scene objects"           │
│                                                          │
│  Output: {                                               │
│    "need": "Physiological",                              │
│    "task": "Repose",                                     │
│    "activity": "sit",                                    │
│    "object": "sofa",                                     │
│    "animation": "sit",                                   │
│    "dialogue": "I need a break."                         │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
```

**Why 2 stages instead of 1?**
- **Stage 1** uses low temperature (0.3) for strict, analytical reasoning about *what the NPC needs*
- **Stage 2** uses high temperature (0.8) for creative, varied *action selection*
- Splitting them ensures the "what" (need analysis) is consistent while the "how" (action choice) has natural variety
- A single prompt trying to do both tends to be less reliable and harder to debug

**Why not 3 stages?**
The emotional state calculation, memory retrieval, and goal loading happen *before* the LLM is called — they are **pre-processing steps**, not separate LLM stages. Only 2 actual LLM calls are made per behavior cycle. This keeps latency low (~200ms per Groq call × 2 = ~400ms total for the LLM portion).
