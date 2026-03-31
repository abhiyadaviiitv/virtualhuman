using System.Collections;
using UnityEngine;
using UnityEngine.Networking;
using System.Text;

public class VirtualHumanClient : MonoBehaviour
{
    private const string apiUrl = "http://localhost:8080/behavior/generate";

    void Start()
    {
        // Example Usage on Start
        StartCoroutine(RequestBehavior());
    }

    public IEnumerator RequestBehavior()
    {
        // Construct the expected payload structure
        string jsonPayload = @"{
            ""avatarId"": ""test_user1"",
            ""worldState"": {
                ""personality"": {
                    ""openness"": 0.8,
                    ""conscientiousness"": 0.7,
                    ""extraversion"": 0.8,
                    ""agreeableness"": 0.6,
                    ""neuroticism"": 0.5
                },
                ""attributes"": {
                    ""age"": 25,
                    ""occupation"": ""software engineer"",
                    ""hobbies"": [""gaming"", ""reading""],
                    ""energy"": 0.3,
                    ""hunger"": 0.2
                },
                ""time"": ""18:00"",
                ""completedActivities"": [],
                ""scene"": {
                    ""sceneDescription"": ""Office room with a computer, television, and sofa"",
                    ""agentLocation"": ""at desk"",
                    ""objects"": [
                        { ""name"": ""computer"", ""distance"": 0.5 },
                        { ""name"": ""sofa"", ""distance"": 2.0 },
                        { ""name"": ""television"", ""distance"": 2.5 }
                    ]
                }
            }
        }";

        using (UnityWebRequest request = new UnityWebRequest(apiUrl, "POST"))
        {
            byte[] bodyRaw = Encoding.UTF8.GetBytes(jsonPayload);
            request.uploadHandler = new UploadHandlerRaw(bodyRaw);
            request.downloadHandler = new DownloadHandlerBuffer();
            request.SetRequestHeader("Content-Type", "application/json");

            Debug.Log("Sending Request to: " + apiUrl);

            yield return request.SendWebRequest();

            if (request.result == UnityWebRequest.Result.ConnectionError || request.result == UnityWebRequest.Result.ProtocolError)
            {
                Debug.LogError("Error: " + request.error);
                Debug.LogError("Response: " + request.downloadHandler.text);
            }
            else
            {
                Debug.Log("Success!");
                Debug.Log("Behavior Response: " + request.downloadHandler.text);
            }
        }
    }
}
