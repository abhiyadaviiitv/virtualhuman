using System.Collections.Generic;
using UnityEngine;

/// <summary>
/// ScriptableObject representing the NPC's permanent identity.
/// Create in Unity: Right Click → Create → NPC → Profile
/// Assign to the NPC prefab's WorldStateBuilder component.
/// </summary>
[CreateAssetMenu(fileName = "NewNPCProfile", menuName = "NPC/Profile")]
public class NPCProfile : ScriptableObject
{
    [Header("Identity")]
    public string avatarId = "npc_001";

    [Header("Big Five Personality Traits (0.0 - 1.0)")]
    [Range(0f, 1f)] public float openness = 0.5f;
    [Range(0f, 1f)] public float conscientiousness = 0.5f;
    [Range(0f, 1f)] public float extraversion = 0.5f;
    [Range(0f, 1f)] public float agreeableness = 0.5f;
    [Range(0f, 1f)] public float neuroticism = 0.5f;

    [Header("Attributes")]
    public int age = 25;
    public string occupation = "student";
    public List<string> hobbies = new List<string> { "reading", "gaming" };

    /// <summary>
    /// Converts Inspector values into the C# Personality data model.
    /// </summary>
    public Personality ToPersonality()
    {
        return new Personality
        {
            openness = this.openness,
            conscientiousness = this.conscientiousness,
            extraversion = this.extraversion,
            agreeableness = this.agreeableness,
            neuroticism = this.neuroticism
        };
    }

    /// <summary>
    /// Converts Inspector values into the C# Attributes data model.
    /// </summary>
    public AttributesData ToAttributes()
    {
        return new AttributesData
        {
            age = this.age,
            occupation = this.occupation,
            hobbies = new List<string>(this.hobbies)
        };
    }
}
