using System;
using System.Collections.Generic;

/// <summary>
/// Mirrors Java's Scene.java — describes what the NPC can see right now.
/// </summary>
[Serializable]
public class SceneData
{
    public string sceneDescription;
    public string agentLocation;
    public List<SceneObjectData> objects = new List<SceneObjectData>();
}
