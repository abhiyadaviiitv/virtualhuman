using System;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using UnityEngine;

/// <summary>
/// Persistent WebSocket connection to the Java backend brain.
/// Replaces the old VirtualHumanClient.cs HTTP polling approach.
/// Attach to the NPC GameObject alongside WorldStateBuilder.
/// </summary>
public class BrainWebSocketClient : MonoBehaviour
{
    [Header("Backend Connection")]
    [SerializeField] private string serverUrl = "ws://localhost:8080/ws/behavior";

    [Header("Request Timing")]
    [Tooltip("Seconds between behavior requests (higher = less LLM calls)")]
    [SerializeField] private float requestInterval = 10f;

    [Header("References")]
    [SerializeField] private WorldStateBuilder worldStateBuilder;
    [SerializeField] private NPCBehaviorExecutor behaviorExecutor;

    private ClientWebSocket webSocket;
    private CancellationTokenSource cancellationTokenSource;
    private float requestTimer;
    private bool isConnected = false;
    private bool isWaitingForResponse = false;

    private async void Start()
    {
        if (worldStateBuilder == null) worldStateBuilder = GetComponent<WorldStateBuilder>();
        if (behaviorExecutor == null) behaviorExecutor = GetComponent<NPCBehaviorExecutor>();

        cancellationTokenSource = new CancellationTokenSource();
        await Connect();
    }

    private void Update()
    {
        if (!isConnected || isWaitingForResponse) return;

        requestTimer += Time.deltaTime;

        // Only ask the brain for a new behavior when the interval elapses
        // AND the NPC has finished executing the previous behavior
        if (requestTimer >= requestInterval && !behaviorExecutor.IsBusy)
        {
            requestTimer = 0f;
            _ = SendBehaviorRequest();
        }
    }

    private async void OnDestroy()
    {
        cancellationTokenSource?.Cancel();
        if (webSocket != null && webSocket.State == WebSocketState.Open)
        {
            await webSocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Goodbye", CancellationToken.None);
        }
        webSocket?.Dispose();
    }

    // ==================== CONNECTION ====================

    private async Task Connect()
    {
        webSocket = new ClientWebSocket();

        try
        {
            Debug.Log($"[Brain] Connecting to {serverUrl}...");
            await webSocket.ConnectAsync(new Uri(serverUrl), cancellationTokenSource.Token);
            isConnected = true;
            Debug.Log("[Brain] ✅ Connected to backend brain!");

            // Start listening for responses in background
            _ = ReceiveLoop();
        }
        catch (Exception e)
        {
            Debug.LogError($"[Brain] ❌ Connection failed: {e.Message}");
            isConnected = false;

            // Retry after 5 seconds
            await Task.Delay(5000);
            await Connect();
        }
    }

    // ==================== SENDING ====================

    private async Task SendBehaviorRequest()
    {
        if (webSocket.State != WebSocketState.Open) return;

        isWaitingForResponse = true;

        try
        {
            string json = worldStateBuilder.BuildRequestJson();
            byte[] bytes = Encoding.UTF8.GetBytes(json);

            Debug.Log($"[Brain] 📤 Sending request at {DayNightClock.Instance?.CurrentTime ?? "??:??"}");
            await webSocket.SendAsync(
                new ArraySegment<byte>(bytes), 
                WebSocketMessageType.Text, 
                true, 
                cancellationTokenSource.Token
            );
        }
        catch (Exception e)
        {
            Debug.LogError($"[Brain] Send error: {e.Message}");
            isWaitingForResponse = false;
            isConnected = false;
            _ = Connect(); // Reconnect
        }
    }

    // ==================== RECEIVING ====================

    private async Task ReceiveLoop()
    {
        byte[] buffer = new byte[8192]; // 8KB buffer for JSON responses

        while (webSocket.State == WebSocketState.Open && !cancellationTokenSource.IsCancellationRequested)
        {
            try
            {
                WebSocketReceiveResult result = await webSocket.ReceiveAsync(
                    new ArraySegment<byte>(buffer), 
                    cancellationTokenSource.Token
                );

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    Debug.Log("[Brain] Server closed the connection.");
                    isConnected = false;
                    break;
                }

                string responseJson = Encoding.UTF8.GetString(buffer, 0, result.Count);
                Debug.Log($"[Brain] 📥 Received: {responseJson}");

                // Parse and dispatch to the behavior executor on the main thread
                BehaviorResponseData response = JsonUtility.FromJson<BehaviorResponseData>(responseJson);

                if (response != null && !string.IsNullOrEmpty(response.activity))
                {
                    // Dispatch to main Unity thread
                    UnityMainThreadDispatcher.Enqueue(() =>
                    {
                        behaviorExecutor.ExecuteBehavior(response);
                    });
                }

                isWaitingForResponse = false;
            }
            catch (OperationCanceledException)
            {
                break; // Normal shutdown
            }
            catch (Exception e)
            {
                Debug.LogError($"[Brain] Receive error: {e.Message}");
                isWaitingForResponse = false;
                break;
            }
        }

        // If we broke out, try to reconnect
        if (!cancellationTokenSource.IsCancellationRequested)
        {
            isConnected = false;
            await Task.Delay(3000);
            await Connect();
        }
    }
}
