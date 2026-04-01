# Personality-Driven Virtual Human Behavior Engine

This is a production-ready Java Spring Boot backend that implements the behavioral reasoning from the paper *"Personality-Driven Virtual Human Behavior Generation (X’s Day)"*.

## Setup

1. **MongoDB**: Ensure MongoDB is running locally on port `27017`.
2. **HuggingFace API Key**: In `src/main/resources/application.properties`, configure your valid HF API key:
   ```properties
   huggingface.api.key=YOUR_API_KEY
   ```
3. **Run**:
   If using Maven wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
   Or standard Maven:
   ```bash
   mvn spring-boot:run
   ```

## API Documentation

### WebSocket `ws://localhost:8080/ws/behavior` (Recommended)
Connect to the raw TCP WebSocket to maintain a persistent connection. 
1. Send the exact JSON payload shown below as a Text Frame.
2. The server will respond with the `BehaviorResponse` JSON asynchronously over the same socket.

### HTTP POST `/behavior/generate`
Generates the next behavior for the virtual human avatar via standard HTTP polling.

#### Request Example
```json
{
  "avatarId": "user1",
  "worldState": {
    "personality": {
      "openness": 0.8,
      "conscientiousness": 0.7,
      "extraversion": 0.3,
      "agreeableness": 0.6,
      "neuroticism": 0.2
    },
    "attributes": {
      "age": 22,
      "occupation": "student",
      "hobbies": ["coding", "music"]
    },
    "time": "10:30",
    "completedActivities": [
      "woke up", 
      "entered classroom"
    ],
    "scene": {
      "sceneDescription": "Classroom with desks and laptops",
      "agentLocation": "near desk",
      "objects": [
        { "name": "chair", "distance": 1.2 },
        { "name": "laptop", "distance": 0.8 }
      ]
    }
  }
}
```

#### Response Example
```json
{
  "need": "social",
  "task": "study",
  "activity": "ask_question",
  "object": "teacher",
  "animation": "raise_hand",
  "dialogue": "Excuse me, can you explain this?"
}
```

### Architecture Features
- **Autoregressive Memory**: Once a behavior is generated and executed, it's stored in MongoDB and contextually loaded automatically for future API interactions regarding the same `avatarId`.
- **Hierarchical Decision Making**: The `BehaviorPlannerService` runs a two-stage prompt passing Need -> Task -> Activity constraints to the LLM.
