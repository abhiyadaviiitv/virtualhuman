package com.virtualhuman.model;

import lombok.Data;
import java.util.List;

@Data
public class Scene {
    private String sceneDescription;
    private String agentLocation;
    private List<SceneObject> objects;
}
