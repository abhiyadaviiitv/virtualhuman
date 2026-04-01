# Virtual Human Behavior System - Architecture & Logic Flow

This document provides a highly detailed explanation of the Virtual Human backend system, including the purpose of each component, the role of the database, the automated daily resets, and the overall logical flow with code examples.

## 1. Why do we need a database?

In a virtual human architecture, the agent needs to act consistently over time. If an agent just ate breakfast, it shouldn't randomly decide to eat breakfast again a few minutes later. 

The database (MongoDB) is used as the agent's **Memory System**. It stores a historical record of all the activities the agent has completed. By retrieving these past activities before making a new decision, the LLM is provided with **context**. 

Furthermore, memory grows infinitely. To ensure our virtual human acts on *recent* context and saves database space, we instituted a **Daily Reset mechanism**, completely clearing their short-term memory at the end of each day.

## 2. Models (Data Structures)

Models represent the state and information passing through the system:

- **`AvatarProfile`**: The core identity of the virtual human, mapped directly to a MongoDB collection (`avatar_profiles`). It contains the `avatarId`, `Personality` traits, general `attributes`, and their overarching `goal`.
- **`WorldState`**: Represents everything the agent knows at a given moment:
  - **Intrinsic:** `personality` and `attributes`
  - **Extrinsic:** `time`, `completedActivities`, and the `scene`.
- **`BehaviorRequest` / `BehaviorResponse`**: The JSON payload sent from Unity to the backend (`BehaviorRequest`), and what the backend responds with (`BehaviorResponse`).
- **`Memory`**: The database entity representing a single past event.
- **`ReasoningCondition`**: An intermediate data model holding the output of Stage 1 reasoning (Dominant Need, Urgency, Style, Context).

---

## 3. Services (Business Logic)

### A. GroqService (The LLM Bridge)
Instead of local CPU execution, this project uses the extremely fast **Groq API** to process prompts. The `GroqService` constructs an HTTP request, passes the prompt string to Groq's open-source LLM models, and returns the generated text.

### B. MemoryService (Database & Scheduling)
Handles all interactions with MongoDB (`MemoryRepository`) and automated cron jobs.

**Core Responsibilities:**
1. **Fetching Context:** Grabs the memory of recent actions so the LLM doesn't hallucinate repeated behaviors.
2. **Saving Activity:** Automatically adds new actions to the DB.
3. **Daily Reset Scheduler:** Clears all memories daily at midnight.

**Code Snippet - Daily Reset:**
```java
@Service
public class MemoryService {
    private final MemoryRepository memoryRepository;

    // Fetches the most recent 'limit' actions
    public List<String> getRecentActivities(String avatarId, int limit) {
        return memoryRepository.findByAvatarIdOrderByTimestampDesc(avatarId).stream()
                .limit(limit)
                .map(Memory::getActivity)
                .collect(Collectors.toList());
    }

    // Runs automatically at Midnight securely clearing the entire short-term memory
    @Scheduled(cron = "0 0 0 * * ?") 
    public void clearDailyActivities() {
        memoryRepository.deleteAll();
        System.out.println("Cleared daily activities for all avatars at midnight.");
    }
}
```
*(Note: To allow `@Scheduled` methods to run, `@EnableScheduling` was added to `VirtualHumanApplication.java`).*

### C. BehaviorPlannerService (The "Brain")
This service orchestrates the famous Two-Stage Reasoning architecture for autonomous agents.

**Code Snippet - The Reasoning Pipeline:**
```java
public BehaviorResponse planBehavior(BehaviorRequest request) {
    WorldState state = request.getWorldState();

    // STEP 1: Dynamic Data Injection
    // If Unity didn't send completed activities, we pull the last 10 from MongoDB.
    if (state.getCompletedActivities() == null || state.getCompletedActivities().isEmpty()) {
        state.setCompletedActivities(memoryService.getRecentActivities(request.getAvatarId(), 10));
    }

    // STEP 2: Stage 1 Reasoning - Infer Internal Condition
    ReasoningCondition condition = inferConditions(state);

    // STEP 3: Stage 2 Reasoning - Sample the Exact Behavior
    BehaviorResponse response = sampleBehavior(state, condition);

    // STEP 4: Memory Update
    // Save the decision so the agent "remembers" it next time
    if (response != null && response.getActivity() != null) {
        String memoryEntry = response.getActivity() + " with " + response.getObject();
        memoryService.addMemory(request.getAvatarId(), memoryEntry);
    }

    return response;
}
```

---

## 4. The Complete End-to-End Logical Flow

Here is exactly what happens from the moment your Unity game boots up to the moment your character moves.

### Step 1: Unity Client Requests Behavior
In Unity, a C# script (`VirtualHumanClient.cs`) uses `UnityWebRequest` to compile the character's current environment (e.g., "I am at a desk, a computer is 0.5m away") into a JSON `BehaviorRequest`.
Unity fires a `POST` request to `http://localhost:8080/behavior/generate`.

### Step 2: Request Reception (Controller)
`BehaviorController.java` receives the JSON payload, deserializes it into Java Objects, and passes it down to the `BehaviorPlannerService`.

### Step 3: Memory Injection
Before making a decision, the plan needs historical context. The planner checks if Unity provided `completedActivities`. If the list is empty, it dynamically asks MongoDB for the last 10 activities via `MemoryService`.

### Step 4: Stage 1 - Condition Reasoning (Internal Deduction)
The backend formats a massive string prompt containing the agent's personality traits, the time, their environment, and their injected memory. 
It sends this to the Groq API. Groq acts as a psychologist, inferring the agent's state: *"Since they've been working for 4 hours and it's 18:00, their 'Dominant Need' is Physiological, with 'High' urgency."*
This is returned as a `ReasoningCondition`.

### Step 5: Stage 2 - Behavior Sampling (Action Selection)
The planner sends a second prompt to Groq, passing in the newly inferred `ReasoningCondition`. 
Groq translates the abstract need into a concrete action: *"Need: Physiological -> Task: Nourish -> Activity: Eat -> Object: Apple"*.
This is returned as a `BehaviorResponse`.

### Step 6: Memory Preservation
Before returning the response to Unity, the backend MUST save the action so that the next request has context. It creates a new `Memory` generic entry ("ate with Apple") and saves it to MongoDB.

### Step 7: Execution in Unity
The `BehaviorController` packs this chosen action up and sends the JSON response back to Unity over HTTP. The Unity C# script receives it, parses `"animation": "eat"`, and physically drives the 3D character in your game engine.

### Step 8: The Midnight Wipes
As the agent plays out endless days in Unity, the MongoDB collection grows. At precisely 00:00 system time, the `@Scheduled` task executes, firing an instantaneous DB wipe (`memoryRepository.deleteAll()`). The agent's slate is wiped clean, preventing infinite context build-up and saving database costs.
