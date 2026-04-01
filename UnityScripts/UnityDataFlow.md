# The Complete Unity → Backend Flow

### Phase 1: Data Collection (Every 10 Seconds)

```
┌─────────────────────────────────────────────────────────────┐
│                    UNITY GAME SCENE                         │
│                                                             │
│   ┌──────────────┐    ┌──────────────┐   ┌──────────────┐   │
│   │ DayNightClock│    │SensorySphere │   │ActivityTracker│  │
│   │              │    │  (Trigger)   │   │              │   │
│   │ Ticks every  │    │              │   │ Stores last  │   │
│   │ frame:       │    │ OnTriggerEnter│  │ 10 activities│   │
│   │ 07:00→07:01  │    │   ↓          │   │              │   │
│   │ →07:02...    │    │ Sofa entered!│   │ "ate lunch"  │   │
│   │              │    │ TV entered!  │   │ "worked"     │   │
│   │ CurrentTime: │    │ Bed entered! │   │ "sat down"   │   │
│   │ "14:30"      │    │              │   │              │   │
│   └──────┬───────┘    │ OnTriggerExit│   └──────┬───────┘   │
│          │            │   ↓          │          │           │
│          │            │ Apple left!  │          │           │
│          │            └──────┬───────┘          │           │
│          │                   │                  │           │
│   ┌──────┴───────┐          │                  │            │
│   │  NPCProfile  │          │                  │            │
│   │ (Static DNA) │          │                  │            │
│   │              │          │                  │            │
│   │ openness:0.8 │          │                  │            │
│   │ conscien:0.6 │          │                  │            │
│   │ age: 25      │          │                  │            │
│   │ job: "dev"   │          │                  │            │
│   └──────┬───────┘          │                  │            │
│          │                  │                  │            │
│          ▼                  ▼                  ▼            │
│   ┌──────────────────────────────────────────────────┐      │
│   │            WorldStateBuilder.BuildRequestJson()   │      │
│   │                                                    │      │
│   │  1. Read NPCProfile → personality + attributes     │      │
│   │  2. Read DayNightClock.CurrentTime → "14:30"       │      │
│   │  3. Read SensorySphere.GetNearbyObjects()          │      │
│   │     → [{sofa, 2.1}, {computer, 0.8}, {TV, 3.5}]    │      │
│   │  4. Read ActivityTracker.GetActivities()           │      │
│   │     → ["ate lunch", "worked on computer"]          │      │
│   │  5. Compute agentLocation from Transform           │      │
│   │     → "near computer"                              │      │
│   │  6. Pack into BehaviorRequestData object           │      │
│   │  7. JsonUtility.ToJson() → JSON string             │      │
│   └──────────────────────┬───────────────────────────┘      │
│                          │                                  │
└──────────────────────────┼──────────────────────────────────┘
                           │
                           ▼
```

### Phase 2: The JSON Payload (What Gets Sent)

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
    "attributes": {
      "age": 25,
      "occupation": "software engineer",
      "hobbies": ["coding", "gaming"]
    },
    "time": "14:30",
    "completedActivities": [
      "ate lunch",
      "worked on computer for 3 hours"
    ],
    "scene": {
      "sceneDescription": "A cozy apartment with living room and kitchen",
      "agentLocation": "near computer",
      "objects": [
        { "name": "computer", "distance": 0.8 },
        { "name": "sofa", "distance": 2.1 },
        { "name": "television", "distance": 3.5 },
        { "name": "refrigerator", "distance": 5.0 }
      ]
    }
  }
}
```

### Phase 3: WebSocket Transmission

```
┌──────────────────┐                          ┌──────────────────┐
│  UNITY           │                          │  JAVA BACKEND    │
│                  │    WebSocket (TCP)        │  :8080           │
│ BrainWebSocket   │◄════════════════════════►│ BehaviorWebSocket│
│ Client.cs        │  ws://localhost:8080      │ Handler.java     │
│                  │  /ws/behavior             │                  │
│ 1. Timer fires   │                          │                  │
│    (every 10s)   │──── JSON Text Frame ────►│ 1. Parse JSON    │
│                  │                          │    into          │
│ 2. Calls         │                          │    BehaviorReq   │
│    BuildRequest  │                          │                  │
│    Json()        │                          │ 2. Forward to    │
│                  │                          │    BehaviorPlan  │
│ 3. Sends over    │                          │    nerService    │
│    WebSocket     │                          │                  │
│                  │                          │      │           │
│                  │                          │      ▼           │
│ WAITS...         │                          │  [SEE PHASE 4]   │
│                  │                          │      │           │
│                  │◄── JSON Text Frame ──────│      │           │
│ 4. ReceiveLoop   │                          │ 3. Send response │
│    catches it    │                          │    back          │
│                  │                          │                  │
│ 5. Dispatches to │                          │                  │
│    main thread   │                          │                  │
│                  │                          │                  │
│ 6. Calls         │                          │                  │
│    Executor      │                          │                  │
│    .Execute      │                          │                  │
│    Behavior()    │                          │                  │
└──────────────────┘                          └──────────────────┘
```

### Phase 4: Inside the Java Brain (What Happens on the Server)

```
BehaviorPlannerService.planBehavior(request)
        │
        ├── 1. EmotionService.getCurrentState("npc_001")
        │       └── Reads Redis → Applies time-decay math
        │           → stress: 0.5, energy: 0.4, boredom: 0.6, happiness: 0.5
        │
        ├── 2. GoalService.buildPromptContext("npc_001")
        │       └── Returns: "Career Growth (HIGH), Healthy Lifestyle (MEDIUM)"
        │
        ├── 3. SemanticMemoryService.buildPromptContext("npc_001")
        │       └── Queries MongoDB → Returns top 5 facts:
        │           - "I enjoy coding and find it relaxing"
        │           - "I get stressed when I am hungry"
        │           - "The sofa is my primary place to rest"
        │
        ├── 4. RedisMemoryService.getRecentActivities("npc_001")
        │       └── Returns: ["ate lunch", "worked on computer"]
        │
        │
        ▼ STAGE 1 — "What does the NPC NEED?" (Temperature: 0.3)
        │
        │  Sends to Groq LLM:
        │  ┌─────────────────────────────────────────────────┐
        │  │ SYSTEM: You are an NPC behavior analyst...      │
        │  │ USER:                                           │
        │  │   Personality: {openness:0.8, conscient:0.6...} │
        │  │   Time: 14:30                                   │
        │  │   Emotions: stress:0.5, energy:0.4, bored:0.6   │
        │  │   Goals: Career Growth (HIGH)                   │
        │  │   Memories: "I enjoy coding"                    │
        │  │   Scene: near computer, sofa 2.1m, TV 3.5m      │
        │  │   Recent: "worked 3 hours", "ate lunch"         │
        │  │                                                 │
        │  │   → Analyze: What does this NPC need most?      │
        │  └─────────────────────────────────────────────────┘
        │  
        │  LLM Returns:
        │  {
        │    "dominantNeed": "Physiological",
        │    "urgencyLevel": "Medium",
        │    "behaviorStyle": "Relaxed",
        │    "contextSummary": "NPC has been working for hours,
        │                       boredom is rising, needs a break"
        │  }
        │
        │
        ▼ STAGE 2 — "What EXACTLY should the NPC DO?" (Temperature: 0.8)
        │
        │  Sends to Groq LLM:
        │  ┌─────────────────────────────────────────────────┐
        │  │ Dominant Need: Physiological                    │
        │  │ Urgency: Medium                                 │
        │  │ Style: Relaxed                                  │
        │  │ Context: "worked for hours, needs a break"      │
        │  │ Scene Objects: computer 0.8m, sofa 2.1m, TV 3.5m│
        │  │ Memories: "The sofa is my primary rest place"   │
        │  │ Goals: Career Growth (HIGH)                     │
        │  │                                                 │
        │  │ → Pick a SPECIFIC action from scene objects     │
        │  └─────────────────────────────────────────────────┘
        │  
        │  LLM Returns:
        │  {
        │    "need": "Physiological",
        │    "task": "Repose",
        │    "activity": "sit and relax",
        │    "object": "sofa",          ← must match a scene object!
        │    "animation": "sit",
        │    "dialogue": "I've been staring at this screen too long."
        │  }
        │
        │
        ▼ POST-PROCESSING
        │
        ├── Save "sit and relax with sofa" to Redis memory
        ├── Update EmotionalState: stress -0.1, boredom -0.15
        └── Send JSON response back over WebSocket
```

### Phase 5: Back in Unity (Executing the Behavior)

```
NPCBehaviorExecutor.ExecuteBehavior(response)
        │
        ├── 1. Show dialogue bubble: "I've been staring at this screen too long."
        │
        ├── 2. Find target object:
        │       SensorySphere.FindObjectByName("sofa")
        │       → Returns the actual sofa's Transform in 3D space
        │
        ├── 3. Navigate:
        │       NavMeshAgent.SetDestination(sofa.transform.position)
        │       Animator.SetTrigger("walk")
        │
        │       ┌─────────────────────────┐
        │       │ NPC walks across room   │
        │       │ toward the sofa...      │
        │       │ (NavMesh pathfinding)   │
        │       └─────────────┬───────────┘
        │                     │
        │       remainingDistance < 1.5m? YES
        │                     │
        ├── 4. Interact:
        │       NavMeshAgent.ResetPath()   (stop walking)
        │       Animator.SetTrigger("sit") (play sitting animation)
        │
        ├── 5. Log activity:
        │       ActivityTracker.AddActivity("sit and relax with sofa")
        │       (This will appear in the NEXT request's completedActivities!)
        │
        └── 6. State → Idle
                (BrainWebSocketClient can now send the next request)
                
        ┌─────────────────────────────────────────────┐
        │  10 seconds later... the cycle repeats!     │
        │  But now:                                   │
        │  - completedActivities has "sit and relax"  │
        │  - Emotions have decayed (less bored now)   │
        │  - Time is "14:40"                          │
        │  - NPC's position is "next to sofa"         │
        │                                             │
        │  → The LLM will generate a DIFFERENT action │
        │    because the world has changed!           │
        └─────────────────────────────────────────────┘
```

### Summary: The Infinite Loop

```
    ┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
    │ COLLECT  │────►│  SEND    │────►│  THINK   │────►│  ACT     │
    │ (Unity)  │     │ (WebSock)│     │ (Java+LLM)│    │ (Unity)  │
    │          │     │          │     │          │     │          │
    │ Time     │     │ JSON     │     │ Emotions │     │ NavMesh  │
    │ Objects  │     │ over TCP │     │ Goals    │     │ Animator │
    │ History  │     │          │     │ Memory   │     │ Dialogue │
    │ Position │     │          │     │ 2x LLM   │     │ Log act  │
    └──────────┘     └──────────┘     └──────────┘     └─────┬────┘
         ▲                                                     │
         │              EVERY 10 SECONDS                       │
         └─────────────────────────────────────────────────────┘
```

Each cycle the world is slightly different — time advanced, emotions decayed, a new activity was logged, the NPC's position changed — so the LLM **always** generates something fresh and contextually appropriate. That's what makes it feel alive!
