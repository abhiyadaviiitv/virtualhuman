using System.Collections.Generic;
using UnityEngine;

/// <summary>
/// Tracks what the NPC has done recently. Keeps a rolling window of completed activities
/// that gets sent to the backend as part of the WorldState.
/// </summary>
public class ActivityTracker : MonoBehaviour
{
    [Tooltip("Maximum number of recent activities to remember")]
    [SerializeField] private int maxActivities = 10;

    private List<string> completedActivities = new List<string>();

    /// <summary>
    /// Called by NPCBehaviorExecutor when the NPC finishes an action.
    /// </summary>
    public void AddActivity(string activity)
    {
        completedActivities.Add(activity);

        // Keep only the most recent N activities
        if (completedActivities.Count > maxActivities)
        {
            completedActivities.RemoveAt(0);
        }

        Debug.Log($"[ActivityTracker] Added: {activity} (Total: {completedActivities.Count})");
    }

    /// <summary>
    /// Returns a copy of the current activity list for the WorldState payload.
    /// </summary>
    public List<string> GetActivities()
    {
        return new List<string>(completedActivities);
    }

    /// <summary>
    /// Clears all tracked activities (e.g., on a new day).
    /// </summary>
    public void ClearActivities()
    {
        completedActivities.Clear();
    }
}
