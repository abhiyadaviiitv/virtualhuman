using UnityEngine;
using UnityEngine.AI;
using TMPro;

/// <summary>
/// The NPC's "Muscles" — translates abstract LLM responses into physical Unity actions.
/// Handles NavMesh navigation, animation triggering, and dialogue display.
/// Attach to the NPC GameObject alongside NavMeshAgent and Animator.
/// </summary>
[RequireComponent(typeof(NavMeshAgent))]
public class NPCBehaviorExecutor : MonoBehaviour
{
    [Header("References")]
    [SerializeField] private SensorySphere sensorySphere;
    [SerializeField] private ActivityTracker activityTracker;
    [SerializeField] private Animator animator;

    [Header("Dialogue UI")]
    [Tooltip("A TextMeshPro floating above the NPC's head for dialogue")]
    [SerializeField] private TextMeshPro dialogueBubble;
    [Tooltip("How long dialogue stays visible (seconds)")]
    [SerializeField] private float dialogueDuration = 5f;

    [Header("Navigation")]
    [Tooltip("How close the NPC needs to be to the target object before interacting")]
    [SerializeField] private float interactionDistance = 1.5f;

    private NavMeshAgent navAgent;
    private BehaviorResponseData currentBehavior;
    private InteractableObject targetInteractable;

    // State machine
    private enum NPCState { Idle, Walking, Interacting }
    private NPCState currentState = NPCState.Idle;
    private float dialogueTimer = 0f;

    /// <summary>
    /// Whether the NPC is currently executing a behavior.
    /// Used by BrainWebSocketClient to avoid sending new requests while busy.
    /// </summary>
    public bool IsBusy => currentState != NPCState.Idle;

    private void Awake()
    {
        navAgent = GetComponent<NavMeshAgent>();
        if (sensorySphere == null) sensorySphere = GetComponent<SensorySphere>();
        if (activityTracker == null) activityTracker = GetComponent<ActivityTracker>();
        if (animator == null) animator = GetComponent<Animator>();
    }

    private void Update()
    {
        switch (currentState)
        {
            case NPCState.Walking:
                UpdateWalking();
                break;
            case NPCState.Interacting:
                UpdateInteracting();
                break;
        }

        // Dialogue fade timer
        if (dialogueTimer > 0)
        {
            dialogueTimer -= Time.deltaTime;
            if (dialogueTimer <= 0 && dialogueBubble != null)
            {
                dialogueBubble.text = "";
            }
        }
    }

    // ==================== PUBLIC API ====================

    /// <summary>
    /// Called by BrainWebSocketClient when the LLM returns a behavior decision.
    /// </summary>
    public void ExecuteBehavior(BehaviorResponseData behavior)
    {
        currentBehavior = behavior;

        Debug.Log($"[Executor] Received: {behavior.task} → {behavior.activity} with {behavior.@object}");

        // Show dialogue immediately
        ShowDialogue(behavior.dialogue);

        // Try to find the target object in the scene
        targetInteractable = null;
        if (!string.IsNullOrEmpty(behavior.@object) && behavior.@object != "none")
        {
            targetInteractable = sensorySphere.FindObjectByName(behavior.@object);
        }

        if (targetInteractable != null)
        {
            // Navigate to the object
            navAgent.SetDestination(targetInteractable.transform.position);
            currentState = NPCState.Walking;

            // Trigger walk animation
            SetAnimation("walk");

            Debug.Log($"[Executor] Walking to {targetInteractable.objectName}...");
        }
        else
        {
            // No specific object to go to — just perform the action in place
            currentState = NPCState.Interacting;
            PerformInteraction();
        }
    }

    // ==================== STATE UPDATES ====================

    private void UpdateWalking()
    {
        if (navAgent.pathPending) return;

        // Check if we've arrived at the destination
        if (navAgent.remainingDistance <= interactionDistance)
        {
            navAgent.ResetPath();
            currentState = NPCState.Interacting;
            PerformInteraction();
        }
    }

    private void UpdateInteracting()
    {
        // Check if the interaction animation has finished
        if (animator != null)
        {
            AnimatorStateInfo stateInfo = animator.GetCurrentAnimatorStateInfo(0);
            // If the animation has looped or finished, mark as idle
            if (stateInfo.normalizedTime >= 1f && !animator.IsInTransition(0))
            {
                FinishBehavior();
            }
        }
        else
        {
            // No animator — just finish immediately
            FinishBehavior();
        }
    }

    // ==================== HELPERS ====================

    private void PerformInteraction()
    {
        if (currentBehavior == null) return;

        // Trigger the appropriate animation
        string animName = currentBehavior.animation;
        if (!string.IsNullOrEmpty(animName))
        {
            SetAnimation(animName);
        }

        Debug.Log($"[Executor] Performing: {currentBehavior.activity}");
    }

    private void FinishBehavior()
    {
        if (currentBehavior != null)
        {
            // Log the completed activity
            string activityLog = currentBehavior.activity;
            if (targetInteractable != null)
            {
                activityLog += " with " + targetInteractable.objectName;
            }
            activityTracker?.AddActivity(activityLog);
        }

        // Reset to idle
        currentState = NPCState.Idle;
        targetInteractable = null;
        currentBehavior = null;
        SetAnimation("idle");

        Debug.Log("[Executor] Behavior complete. Now idle.");
    }

    private void SetAnimation(string triggerName)
    {
        if (animator != null)
        {
            // Reset all triggers first to avoid conflicting states
            animator.ResetTrigger("idle");
            animator.ResetTrigger("walk");

            animator.SetTrigger(triggerName);
        }
    }

    private void ShowDialogue(string text)
    {
        if (string.IsNullOrEmpty(text)) return;

        if (dialogueBubble != null)
        {
            dialogueBubble.text = text;
            dialogueTimer = dialogueDuration;
        }

        // Always log it
        Debug.Log($"[NPC Says] \"{text}\"");
    }
}
