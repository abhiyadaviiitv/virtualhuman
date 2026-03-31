package com.virtualhuman.model;

import lombok.Data;

@Data
public class BehaviorRequest {
    private String avatarId;
    private WorldState worldState;
}
