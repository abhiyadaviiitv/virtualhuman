using UnityEngine;

/// <summary>
/// Attach this to EVERY object in the VR scene that the NPC can interact with.
/// Examples: sofa, computer, apple, bed, television, bookshelf, etc.
/// The NPC's SensorySphere will detect these via trigger collisions.
/// </summary>
public class InteractableObject : MonoBehaviour
{
    [Tooltip("The name sent to the LLM (e.g. 'sofa', 'computer', 'apple')")]
    public string objectName = "unknown";

    [Tooltip("Optional: What category this object belongs to")]
    public string category = "general"; // e.g., "food", "entertainment", "furniture", "work"

    /// <summary>
    /// Returns the distance from this object to a given NPC position.
    /// </summary>
    public float GetDistanceTo(Transform npcTransform)
    {
        return Vector3.Distance(transform.position, npcTransform.position);
    }
}
