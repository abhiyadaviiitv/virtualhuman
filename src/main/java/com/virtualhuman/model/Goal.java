package com.virtualhuman.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Goal {
    private String id;
    private String title;
    private String description;
    private double progress; // 0.0 to 1.0
    private Priority priority;

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
