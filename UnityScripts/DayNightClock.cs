using UnityEngine;

/// <summary>
/// Simulates a 24-hour day/night cycle. Singleton — one per scene.
/// Provides the current in-game time as a formatted "HH:mm" string.
/// Optionally rotates a Directional Light to simulate sun movement.
/// </summary>
public class DayNightClock : MonoBehaviour
{
    public static DayNightClock Instance { get; private set; }

    [Header("Time Settings")]
    [Tooltip("How many real-time seconds equal one in-game minute")]
    [SerializeField] private float realSecondsPerGameMinute = 1f; // 1 real sec = 1 game min → 24 min = full day

    [Tooltip("Starting hour (0-23)")]
    [SerializeField] private int startHour = 7; // Start at 07:00

    [Tooltip("Starting minute (0-59)")]
    [SerializeField] private int startMinute = 0;

    [Header("Optional Day/Night Visuals")]
    [Tooltip("Assign the scene's Directional Light to rotate with the sun")]
    [SerializeField] private Light sunLight;

    // Internal time tracking (in total minutes since midnight)
    private float currentTimeInMinutes;

    /// <summary>
    /// Current in-game hour (0-23).
    /// </summary>
    public int CurrentHour => Mathf.FloorToInt(currentTimeInMinutes / 60f) % 24;

    /// <summary>
    /// Current in-game minute (0-59).
    /// </summary>
    public int CurrentMinute => Mathf.FloorToInt(currentTimeInMinutes) % 60;

    /// <summary>
    /// The formatted time string sent to the Java backend (e.g., "14:30").
    /// </summary>
    public string CurrentTime => $"{CurrentHour:D2}:{CurrentMinute:D2}";

    /// <summary>
    /// Normalized time of day (0.0 = midnight, 0.5 = noon, 1.0 = midnight).
    /// Useful for shader/lighting interpolation.
    /// </summary>
    public float NormalizedTime => (currentTimeInMinutes % 1440f) / 1440f;

    private void Awake()
    {
        if (Instance != null && Instance != this)
        {
            Destroy(gameObject);
            return;
        }
        Instance = this;

        currentTimeInMinutes = (startHour * 60f) + startMinute;
    }

    private void Update()
    {
        // Advance the clock
        float minutesThisFrame = Time.deltaTime / realSecondsPerGameMinute;
        currentTimeInMinutes += minutesThisFrame;

        // Wrap around at 24 hours (1440 minutes)
        if (currentTimeInMinutes >= 1440f)
            currentTimeInMinutes -= 1440f;

        // Rotate the sun light if assigned
        if (sunLight != null)
        {
            // Map 0-1440 minutes to 0-360 degrees rotation
            float sunAngle = (NormalizedTime * 360f) - 90f; // -90 so sunrise starts at horizon
            sunLight.transform.rotation = Quaternion.Euler(sunAngle, 170f, 0f);
        }
    }

    /// <summary>
    /// Manually set the time (useful for testing or fast-forward).
    /// </summary>
    public void SetTime(int hour, int minute)
    {
        currentTimeInMinutes = (hour * 60f) + minute;
    }

    /// <summary>
    /// Skip forward by a number of in-game hours.
    /// </summary>
    public void FastForward(float hours)
    {
        currentTimeInMinutes += hours * 60f;
        if (currentTimeInMinutes >= 1440f)
            currentTimeInMinutes -= 1440f;
    }
}
