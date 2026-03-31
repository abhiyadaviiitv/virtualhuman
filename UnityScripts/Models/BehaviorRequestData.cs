using System;

/// <summary>
/// Mirrors Java's BehaviorRequest.java — the top-level payload sent to the backend.
/// </summary>
[Serializable]
public class BehaviorRequestData
{
    public string avatarId;
    public WorldStateData worldState;
}
