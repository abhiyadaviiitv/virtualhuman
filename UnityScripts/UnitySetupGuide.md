# Unity NPC Brain — Complete Setup & Integration Guide

> This guide takes you from an empty Unity project to a fully autonomous, LLM-powered NPC that sees, thinks, moves, speaks, and remembers — connected to our Java backend brain via WebSocket.

---

## Table of Contents
1. [Prerequisites](#1-prerequisites)
2. [Project Setup](#2-project-setup)
3. [Scene Hierarchy](#3-scene-hierarchy)
4. [Setting Up the NPC](#4-setting-up-the-npc)
5. [Setting Up the Environment](#5-setting-up-the-environment)
6. [Complete Data Flow](#6-complete-data-flow)
7. [Real Scenario Walkthrough](#7-real-scenario-walkthrough)
8. [Testing & Verification](#8-testing--verification)
9. [Troubleshooting](#9-troubleshooting)
10. [Future VR Integration Roadmap](#10-future-vr-integration-roadmap)

---

## 1. Prerequisites

Before you start, make sure you have:

| Requirement | Why |
|---|---|
| Unity 2022.3+ (LTS) | Stable NavMesh, WebSocket support |
| Java backend running | `mvn spring-boot:run` on port 8080 |
| Redis running | `docker run -p 6379:6379 -d redis` |
| TextMeshPro package | For NPC dialogue bubbles (Unity auto-prompts) |
| NavMesh components | Built into Unity (Window → AI → Navigation) |

---

## 2. Project Setup

### Step 1: Copy Scripts into Unity
```
YourUnityProject/
└── Assets/
    └── Scripts/
        └── NPC/                          ← Copy the entire UnityScripts/ folder here
            ├── Models/
            │   ├── Personality.cs
            │   ├── SceneObjectData.cs
            │   ├── SceneData.cs
            │   ├── WorldStateData.cs
            │   ├── BehaviorRequestData.cs
            │   └── BehaviorResponseData.cs
            ├── NPCProfile.cs
            ├── InteractableObject.cs
            ├── SensorySphere.cs
            ├── DayNightClock.cs
            ├── ActivityTracker.cs
            ├── WorldStateBuilder.cs
            ├── BrainWebSocketClient.cs
            ├── UnityMainThreadDispatcher.cs
            └── NPCBehaviorExecutor.cs
```

### Step 2: Import TextMeshPro
When Unity opens, it will prompt you to "Import TMP Essentials" — **click Import**. This is needed for the dialogue bubble.

### Step 3: Wait for Compilation
Unity will compile all scripts. If you see **zero errors** in the Console, you're good to go.

---

## 3. Scene Hierarchy

Here is the exact GameObject hierarchy you need to build:

```
Scene Root
├── 🌞 Directional Light          (existing — assign to DayNightClock)
├── 📷 Main Camera                (existing)
├── 🏠 Environment                (your room/apartment model)
│   ├── Floor                     (with NavMesh baked on it)
│   ├── Walls
│   └── Ceiling
├── 🪑 Interactables              (parent for organization)
│   ├── Sofa                      + InteractableObject (objectName: "sofa")
│   ├── Computer                  + InteractableObject (objectName: "computer")
│   ├── Television                + InteractableObject (objectName: "television")
│   ├── Bed                       + InteractableObject (objectName: "bed")
│   ├── Refrigerator              + InteractableObject (objectName: "refrigerator")
│   ├── Bookshelf                 + InteractableObject (objectName: "bookshelf")
│   └── DiningTable               + InteractableObject (objectName: "dining table")
├── 🧑 NPC_Avatar                 (your humanoid character model)
│   ├── DialogueBubble            (floating TextMeshPro above head)
│   └── (child objects/bones)
├── ⏰ [GameManager]              (empty GameObject)
│   ├── DayNightClock             (component)
│   └── UnityMainThreadDispatcher (component)
└── 🎮 [XR Rig]                  (if VR — your camera rig)
```

---

## 4. Setting Up the NPC

This is the most important part. Follow every sub-step carefully.

### Step 4.1: Create an NPC Profile Asset

1. **Right-click** in your Project window → `Create → NPC → Profile`
2. Name it `DefaultNPC`
3. **Click on it** and set values in Inspector:

```
Avatar ID:          "npc_001"

── Big Five Personality ──
Openness:           0.8   (curious, creative)
Conscientiousness:  0.6   (moderately disciplined)
Extraversion:       0.4   (slightly introverted)
Agreeableness:      0.7   (friendly)
Neuroticism:        0.3   (emotionally stable)

── Attributes ──
Age:                25
Occupation:         "software engineer"
Hobbies:            ["coding", "gaming", "reading"]
```

> **Tip:** Experiment with personality! Set Neuroticism to 0.9 and watch the NPC panic at minor problems. Set Extraversion to 0.1 and it will avoid social activities entirely. The LLM reads these values and adjusts behavior accordingly.

### Step 4.2: Configure the NPC GameObject

Select your NPC character model (`NPC_Avatar`) and add these components **in this order**:

| # | Component | Settings |
|---|---|---|
| 1 | **NavMeshAgent** | Speed: 3.5, Angular Speed: 120, Stopping Distance: 0.5 |
| 2 | **Animator** | Controller: Your humanoid animation controller |
| 3 | **SensorySphere** | Detection Radius: 8 |
| 4 | **ActivityTracker** | Max Activities: 10 |
| 5 | **WorldStateBuilder** | NPC Profile: drag `DefaultNPC` asset here |
| 6 | **BrainWebSocketClient** | Server URL: `ws://localhost:8080/ws/behavior`, Request Interval: 10 |
| 7 | **NPCBehaviorExecutor** | Interaction Distance: 1.5, Dialogue Duration: 5 |

### Step 4.3: Wire the References

In `WorldStateBuilder`:
- **NPC Profile**: Drag your `DefaultNPC.asset`
- **Scene Description**: Type `"A cozy apartment with a living room, kitchen, and bedroom"`
- **Sensory Sphere**: Auto-detected (same GameObject)
- **Activity Tracker**: Auto-detected (same GameObject)

In `BrainWebSocketClient`:
- **World State Builder**: Auto-detected
- **Behavior Executor**: Auto-detected

In `NPCBehaviorExecutor`:
- **Sensory Sphere**: Auto-detected
- **Activity Tracker**: Auto-detected
- **Animator**: Auto-detected
- **Dialogue Bubble**: Drag the TextMeshPro child object

### Step 4.4: Create the Dialogue Bubble

1. Create an empty child under `NPC_Avatar`, name it `DialogueBubble`
2. Position it at `(0, 2.2, 0)` — floating above the NPC's head
3. Add a **TextMeshPro - Text** component
4. Settings: Font Size 3, Alignment Center, Color White
5. Drag this into the `NPCBehaviorExecutor` → Dialogue Bubble slot

### Step 4.5: Set Up the Animator

Your Animator Controller needs these trigger parameters and states:

```
Parameters (Triggers):
├── idle
├── walk
├── sit
├── eat
├── sleep
├── work
├── watch_tv
└── exercise

States:
├── Idle          (default state)
├── Walking       (triggered by "walk")
├── Sitting       (triggered by "sit")
├── Eating        (triggered by "eat")
├── Sleeping      (triggered by "sleep")
├── Working       (triggered by "work")
├── WatchingTV    (triggered by "watch_tv")
└── Exercising    (triggered by "exercise")
```

> **Note:** You don't need ALL animations on day one. Start with just `idle` and `walk`. The executor will gracefully fall back to idle if a trigger doesn't exist. Add more animations over time as you find what the LLM generates.

---

## 5. Setting Up the Environment

### Step 5.1: Interactable Objects

For **every** object the NPC should be able to interact with:

1. Select the 3D object (e.g., Sofa)
2. Add component: `InteractableObject`
3. Set **Object Name**: `"sofa"` ← This exact string is what the LLM sees and references
4. Set **Category**: `"furniture"` (optional, for your reference)
5. **Make sure it has a Collider** (Box/Mesh Collider) — the SensorySphere needs this for trigger detection!

| Object | objectName | Category | Collider Type |
|---|---|---|---|
| Sofa | `sofa` | furniture | Box Collider |
| Computer/Laptop | `computer` | work | Box Collider |
| Television | `television` | entertainment | Box Collider |
| Bed | `bed` | furniture | Box Collider |
| Refrigerator | `refrigerator` | food | Box Collider |
| Dining Table | `dining table` | food | Box Collider |
| Bookshelf | `bookshelf` | entertainment | Box Collider |
| Exercise Mat | `exercise mat` | fitness | Box Collider |

### Step 5.2: Bake the NavMesh

1. Go to `Window → AI → Navigation`
2. Select your floor object → Mark as **Navigation Static**
3. In the Navigation window → **Bake** tab → Click **Bake**
4. You should see a blue overlay on your floor — the NPC can now walk on it

### Step 5.3: Set Up the Game Manager

1. Create an empty GameObject called `[GameManager]`
2. Add `DayNightClock` component:
   - Real Seconds Per Game Minute: `1` (1 real second = 1 game minute)
   - Start Hour: `7`, Start Minute: `0` (NPC wakes up at 7 AM)
   - Sun Light: Drag your Directional Light here
3. Add `UnityMainThreadDispatcher` component (no settings needed)

---

## 6. Complete Data Flow

Here is exactly what happens every 10 seconds when the NPC asks the brain for a decision:

```
[10s Timer Fires in BrainWebSocketClient]
        │
        ▼
[WorldStateBuilder.BuildRequestJson()]
        │
        ├── DayNightClock.CurrentTime ──────────── "14:30"
        ├── SensorySphere.GetNearbyObjects() ───── [{sofa, 2.1}, {computer, 0.8}]
        ├── ActivityTracker.GetActivities() ────── ["worked on computer", "ate lunch"]
        ├── NPCProfile.ToPersonality() ─────────── {openness: 0.8, ...}
        │
        ▼
[JSON Payload assembled]
        │
        ▼
[WebSocket sends to ws://localhost:8080/ws/behavior]
        │
        ▼
[Java Backend BehaviorPlannerService]
        ├── EmotionService ────── stress: 0.6, energy: 0.4
        ├── GoalService ───────── "Career Growth (HIGH)"
        ├── SemanticMemory ────── "I enjoy coding"
        │
        ├── Stage 1 (Temp 0.3) → Groq LLM → {dominantNeed: "Physiological", urgency: "High"}
        ├── Stage 2 (Temp 0.8) → Groq LLM → {activity: "rest", object: "sofa", animation: "sit"}
        │
        ▼
[WebSocket pushes BehaviorResponse JSON back to Unity]
        │
        ▼
[NPCBehaviorExecutor.ExecuteBehavior()]
        ├── SensorySphere.FindObjectByName("sofa") → Gets actual Transform
        ├── NavMeshAgent.SetDestination(sofa.position)
        ├── Animator.SetTrigger("walk")
        │   ... NPC walks to sofa ...
        ├── Animator.SetTrigger("sit")
        ├── DialogueBubble.text = "I need a break"
        └── ActivityTracker.AddActivity("rest with sofa")
```

---

## 7. Real Scenario Walkthrough

### Scenario 1: Morning Routine (07:00)

**WorldState sent to backend:**
```json
{
  "avatarId": "npc_001",
  "worldState": {
    "personality": { "openness": 0.8, "conscientiousness": 0.6 },
    "attributes": { "age": 25, "occupation": "software engineer" },
    "time": "07:00",
    "completedActivities": [],
    "scene": {
      "sceneDescription": "A cozy apartment with a living room, kitchen, and bedroom",
      "agentLocation": "next to bed",
      "objects": [
        { "name": "bed", "distance": 0.5 },
        { "name": "refrigerator", "distance": 4.2 },
        { "name": "computer", "distance": 6.0 }
      ]
    }
  }
}
```

**What the LLM thinks:**
> *"It's 7 AM, completedActivities is empty (just woke up). Energy is probably low. The closest object is the bed but they need food. The refrigerator is nearby. Let's eat first."*

**Response:**
```json
{
  "need": "Physiological",
  "task": "Nourish",
  "activity": "eat breakfast",
  "object": "refrigerator",
  "animation": "walk",
  "dialogue": "Good morning! Time for breakfast."
}
```

**What happens in Unity:**
1. Dialogue bubble shows: *"Good morning! Time for breakfast."*
2. NPC walks to the refrigerator (NavMeshAgent)
3. Plays eat animation
4. ActivityTracker records: `"eat breakfast with refrigerator"`

---

### Scenario 2: Afternoon Burnout (15:00)

After 6 hours of the NPC repeatedly choosing "work on computer", the backend's emotional engine has accumulated:
- `stress: 0.7` (HIGH), `energy: 0.3` (LOW), `boredom: 0.6`

**Response:**
```json
{
  "need": "Physiological",
  "task": "Repose",
  "activity": "watch television",
  "object": "television",
  "animation": "sit",
  "dialogue": "I can't focus anymore. I need to relax."
}
```

The NPC **autonomously decides** to stop working and watch TV. Nobody told it to. The emotional math made it happen.

---

### Scenario 3: Late Night (23:00)

**WorldState shows:** time: "23:00", nearby objects include bed. Energy depleted from full day.

**Response:**
```json
{
  "need": "Physiological",
  "task": "Repose",
  "activity": "sleep",
  "object": "bed",
  "animation": "sleep",
  "dialogue": "What a long day... time to sleep."
}
```

---

### Scenario 4: New Object Appears!

You dynamically spawn an `Apple` object near the NPC at runtime. It has an `InteractableObject` component with `objectName: "apple"`.

The SensorySphere's `OnTriggerEnter` fires → apple added to `visibleObjects` → next WorldState includes `{"name": "apple", "distance": 1.2}`.

The LLM sees a new food item and may react: *"Oh, an apple! I could use a snack."*

This is **emergent behavior** driven entirely by the physics of your Unity scene!

---

## 8. Testing & Verification

### Quick Test Checklist

| # | Test | Expected Result |
|---|---|---|
| 1 | Start Java backend + Redis | Console shows "Started on port 8080" |
| 2 | Hit Play in Unity | Console shows `[Brain] ✅ Connected to backend brain!` |
| 3 | Wait 10 seconds | Console shows `[Brain] 📤 Sending request at 07:00` |
| 4 | Wait for response | Console shows `[Brain] 📥 Received: {...}` |
| 5 | Watch NPC | NPC walks to an object and performs an animation |
| 6 | Check dialogue | TextMeshPro bubble shows LLM-generated dialogue |
| 7 | Wait another 10s | Different behavior (emotion has decayed/changed) |
| 8 | Fast-forward clock | Call `DayNightClock.Instance.SetTime(23, 0)` → NPC sleeps |

### Debug Commands (Paste in a test MonoBehaviour)

```csharp
// Fast-forward to midnight
DayNightClock.Instance.SetTime(0, 0);

// Fast-forward 6 hours
DayNightClock.Instance.FastForward(6);

// Check what the NPC currently sees
var objects = FindObjectOfType<SensorySphere>().GetNearbyObjects();
foreach (var obj in objects)
    Debug.Log($"Visible: {obj.name} at {obj.distance}m");
```

---

## 9. Troubleshooting

| Problem | Cause | Fix |
|---|---|---|
| `[Brain] ❌ Connection failed` | Backend not running | Run `mvn spring-boot:run` first |
| NPC doesn't move | NavMesh not baked | Window → AI → Navigation → Bake |
| NPC walks but never reaches object | Object not on NavMesh | Move object onto the baked floor |
| SensorySphere detects nothing | Objects missing Collider | Add BoxCollider to each Interactable |
| `JsonUtility` errors | Field name mismatch | Ensure C# field names match Java exactly |
| NPC stuck in "Walking" state | Destination unreachable | Check NavMesh connectivity |
| No dialogue visible | DialogueBubble not assigned | Drag TMP child into Inspector slot |
| `"error": "Unable to connect to Redis"` | Redis not running | `docker start virtualhuman-redis` |

---

## 10. Future VR Integration Roadmap

### 🥽 Tier 1: Basic VR Awareness

**Make the NPC aware of the VR player as another "object" in the scene.**

Add `InteractableObject` to the XR Camera Rig with `objectName: "player"`. The SensorySphere detects the player, the LLM sees `{"name": "player", "distance": 2.3}` and may greet you:

```json
{
  "need": "Social",
  "task": "Socialize",
  "activity": "greet",
  "object": "player",
  "animation": "wave",
  "dialogue": "Hey! I didn't see you there. How are you?"
}
```

---

### 🎤 Tier 2: Voice Interaction

Let the VR player **talk** to the NPC:
1. Capture voice with Unity's `Microphone` class
2. Transcribe with Whisper API
3. Add `"playerSpeech"` field to WorldState
4. LLM reads it and generates a contextual reply
5. Play response via ElevenLabs TTS as spatial audio from the NPC's position

---

### ✋ Tier 3: Hand Tracking & Object Manipulation

Track VR controller positions, detect when player picks up objects. Add `"playerAction": "player picked up apple"` to WorldState. NPC reacts to your physical actions in real-time.

---

### 👥 Tier 4: Multi-NPC Communication

Each NPC has its own `BrainWebSocketClient` and personality. When NPC-A speaks, inject their dialogue into NPC-B's `sceneDescription`. They form relationships, argue, and make plans — all emergent from the LLM!

---

### 🎭 Tier 5: Procedural Animation & Emotion Visualization

Query the backend's emotional state, map values to Unity blend shapes:
- High stress → furrowed brow, tense shoulders
- Low energy → slouched posture, slow NavMeshAgent speed
- High happiness → smile, bouncy walk

---

### 🌍 Tier 6: Persistent World State

Save `ActivityTracker` and `DayNightClock` state to disk on quit. Load on start so the NPC remembers yesterday. Combined with MongoDB's permanent memories, the NPC truly lives across sessions.

---

> **Important:** Start simple. Get Tier 0 (this guide) working first. Once you see the NPC walking around autonomously, THEN layer on VR features one tier at a time. Each tier builds on the last.
