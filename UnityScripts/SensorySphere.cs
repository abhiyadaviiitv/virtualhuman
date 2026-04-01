using System.Collections.Generic;
using System.Linq;
using UnityEngine;

/// <summary>
/// The NPC's "Eyes" — a spherical trigger collider that detects nearby InteractableObjects.
/// Attach to the NPC GameObject. Requires a SphereCollider set to isTrigger.
/// </summary>
[RequireComponent(typeof(SphereCollider))]
public class SensorySphere : MonoBehaviour
{
    [Tooltip("Detection radius in meters")]
    [SerializeField] private float detectionRadius = 8f;

    // Live list of objects currently inside the detection sphere
    private List<InteractableObject> visibleObjects = new List<InteractableObject>();

    private SphereCollider sphereCollider;

    void Awake()
    {
        sphereCollider = GetComponent<SphereCollider>();
        sphereCollider.isTrigger = true;
        sphereCollider.radius = detectionRadius;
    }

    // --- Trigger Detection ---

    private void OnTriggerEnter(Collider other)
    {
        InteractableObject interactable = other.GetComponent<InteractableObject>();
        if (interactable != null && !visibleObjects.Contains(interactable))
        {
            visibleObjects.Add(interactable);
            Debug.Log($"[SensorySphere] Detected: {interactable.objectName}");
        }
    }

    private void OnTriggerExit(Collider other)
    {
        InteractableObject interactable = other.GetComponent<InteractableObject>();
        if (interactable != null)
        {
            visibleObjects.Remove(interactable);
            Debug.Log($"[SensorySphere] Lost sight of: {interactable.objectName}");
        }
    }

    // --- Public API ---

    /// <summary>
    /// Returns all currently visible objects as SceneObjectData, sorted by distance (nearest first).
    /// Called by WorldStateBuilder every tick before sending to the backend.
    /// </summary>
    public List<SceneObjectData> GetNearbyObjects()
    {
        // Clean up any destroyed objects
        visibleObjects.RemoveAll(obj => obj == null);

        return visibleObjects
            .Select(obj => new SceneObjectData(obj.objectName, obj.GetDistanceTo(transform)))
            .OrderBy(o => o.distance)
            .ToList();
    }

    /// <summary>
    /// Finds the actual Unity GameObject for a given object name (used by BehaviorExecutor).
    /// Returns null if the object is no longer visible.
    /// </summary>
    public InteractableObject FindObjectByName(string objectName)
    {
        visibleObjects.RemoveAll(obj => obj == null);
        return visibleObjects.FirstOrDefault(
            obj => obj.objectName.Equals(objectName, System.StringComparison.OrdinalIgnoreCase)
        );
    }

    /// <summary>
    /// Updates the detection radius at runtime if needed.
    /// </summary>
    public void SetDetectionRadius(float newRadius)
    {
        detectionRadius = newRadius;
        if (sphereCollider != null)
            sphereCollider.radius = newRadius;
    }
}
