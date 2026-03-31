using System;

/// <summary>
/// A single interactable object visible to the NPC, with its name and distance.
/// </summary>
[Serializable]
public class SceneObjectData
{
    public string name;
    public float distance;

    public SceneObjectData(string name, float distance)
    {
        this.name = name;
        this.distance = Mathf.Round(distance * 10f) / 10f; // Round to 1 decimal
    }
}
