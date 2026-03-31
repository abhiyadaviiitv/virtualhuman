using System;
using System.Collections.Generic;
using UnityEngine;

/// <summary>
/// Dispatches actions from background threads to Unity's main thread.
/// Required because WebSocket responses arrive on background threads,
/// but Unity APIs (NavMesh, Animator, etc.) can only be called from the main thread.
/// Place ONE instance in the scene on an empty GameObject.
/// </summary>
public class UnityMainThreadDispatcher : MonoBehaviour
{
    private static UnityMainThreadDispatcher instance;
    private static readonly Queue<Action> actionQueue = new Queue<Action>();

    private void Awake()
    {
        if (instance != null && instance != this)
        {
            Destroy(gameObject);
            return;
        }
        instance = this;
        DontDestroyOnLoad(gameObject);
    }

    private void Update()
    {
        lock (actionQueue)
        {
            while (actionQueue.Count > 0)
            {
                actionQueue.Dequeue()?.Invoke();
            }
        }
    }

    /// <summary>
    /// Enqueue an action to be executed on the next Unity Update frame.
    /// </summary>
    public static void Enqueue(Action action)
    {
        if (action == null) return;

        lock (actionQueue)
        {
            actionQueue.Enqueue(action);
        }
    }
}
