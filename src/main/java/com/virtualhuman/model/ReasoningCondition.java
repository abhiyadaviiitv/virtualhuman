package com.virtualhuman.model;

import lombok.Data;

@Data
public class ReasoningCondition {
    private String dominantNeed;
    private String urgencyLevel;
    private String behaviorStyle;
    private String contextSummary;
}
