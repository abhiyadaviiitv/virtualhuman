using System;

/// <summary>
/// Mirrors Java's BehaviorResponse.java — the LLM's decision about what the NPC should do.
/// </summary>
[Serializable]
public class BehaviorResponseData
{
    public string need;
    public string task;
    public string activity;
    // Named 'targetObject' in C# to avoid conflict with UnityEngine.Object
    public string @object;
    public string animation;
    public string dialogue;
}
