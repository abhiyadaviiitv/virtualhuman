package com.virtualhuman.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class WorldState {

    // Intrinsic properties mapped from AvatarProfile are needed for LLM
    private Personality personality;
    private Map<String, Object> attributes;

    // Extrinsic properties from the environment
    private String time;
    private List<String> completedActivities;
    private Scene scene;
}
