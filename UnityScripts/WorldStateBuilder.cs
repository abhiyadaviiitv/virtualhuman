using System.Collections.Generic;
using UnityEngine;
using UnityEngine.AI;
using UnityEngine.SceneManagement;

/// <summary>
/// Orchestrates all sensory components into a single BehaviorRequest JSON payload.
/// This is the bridge between the Unity world and the Java backend.
/// Attach to the NPC GameObject alongside SensorySphere, ActivityTracker, etc.
/// </summary>
public class WorldStateBuilder : MonoBehaviour
{
    [Header("NPC Identity (Drag your NPCProfile asset here)")]
    [SerializeField] private NPCProfile npcProfile;

    [Header("Scene Description")]
    [Tooltip("A human-readable description of the current environment")]
    [SerializeField] private string sceneDescription = "A cozy apartment with a kitchen, living room, and bedroom";

    [Header("References (Auto-detected if on same GameObject)")]
    [SerializeField] private SensorySphere sensorySphere;
    [SerializeField] private ActivityTracker activityTracker;

    private NavMeshAgent navAgent;

    private void Awake()
    {
        // Auto-wire references if not manually assigned
        if (sensorySphere == null) sensorySphere = GetComponent<SensorySphere>();
        if (activityTracker == null) activityTracker = GetComponent<ActivityTracker>();
        navAgent = GetComponent<NavMeshAgent>();
    }

    /// <summary>
    /// Builds the complete BehaviorRequest by reading live data from all sensory components.
    /// Called by BrainWebSocketClient before every request.
    /// </summary>
    public BehaviorRequestData BuildRequest()
    {
        BehaviorRequestData request = new BehaviorRequestData();
        request.avatarId = npcProfile.avatarId;

        WorldStateData worldState = new WorldStateData();

        // --- STATIC: Personality DNA (never changes) ---
        worldState.personality = npcProfile.ToPersonality();
        worldState.attributes = npcProfile.ToAttributes();

        // --- DYNAMIC: Time from the Day/Night Clock ---
        if (DayNightClock.Instance != null)
        {
            worldState.time = DayNightClock.Instance.CurrentTime;
        }
        else
        {
            // Fallback to real system time if no clock exists
            worldState.time = System.DateTime.Now.ToString("HH:mm");
        }

        // --- DYNAMIC: Completed Activities ---
        worldState.completedActivities = activityTracker != null 
            ? activityTracker.GetActivities() 
            : new List<string>();

        // --- DYNAMIC: Scene (what the NPC sees right now) ---
        SceneData scene = new SceneData();
        scene.sceneDescription = sceneDescription;
        scene.agentLocation = GetAgentLocation();
        scene.objects = sensorySphere != null 
            ? sensorySphere.GetNearbyObjects() 
            : new List<SceneObjectData>();

        worldState.scene = scene;

        request.worldState = worldState;

        return request;
    }

    /// <summary>
    /// Converts the request to a JSON string ready to send over WebSocket.
    /// </summary>
    public string BuildRequestJson()
    {
        BehaviorRequestData request = BuildRequest();
        return JsonUtility.ToJson(request);
    }

    /// <summary>
    /// Determines the NPC's current location description based on nearby objects.
    /// </summary>
    private string GetAgentLocation()
    {
        // Try to describe position relative to nearest object
        if (sensorySphere != null)
        {
            List<SceneObjectData> nearby = sensorySphere.GetNearbyObjects();
            if (nearby.Count > 0)
            {
                SceneObjectData nearest = nearby[0];
                if (nearest.distance < 1.5f)
                    return $"next to {nearest.name}";
                else
                    return $"near {nearest.name}";
            }
        }

        return "center of room";
    }

    /// <summary>
    /// Allows changing the scene description at runtime (e.g., when NPC moves between rooms).
    /// </summary>
    public void SetSceneDescription(string description)
    {
        sceneDescription = description;
    }
}
