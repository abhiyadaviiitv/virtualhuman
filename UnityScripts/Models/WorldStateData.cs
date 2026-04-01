using System;
using System.Collections.Generic;

/// <summary>
/// Mirrors Java's WorldState.java — the complete snapshot of the NPC's world.
/// Built dynamically every tick from real Unity scene data.
/// </summary>
[Serializable]
public class WorldStateData
{
    public Personality personality;
    public AttributesData attributes;
    public string time;
    public List<string> completedActivities = new List<string>();
    public SceneData scene;
}

/// <summary>
/// NPC attributes like age, occupation, hobbies.
/// Using a dedicated class instead of Dictionary for clean JSON serialization.
/// </summary>
[Serializable]
public class AttributesData
{
    public int age;
    public string occupation;
    public List<string> hobbies = new List<string>();
}
