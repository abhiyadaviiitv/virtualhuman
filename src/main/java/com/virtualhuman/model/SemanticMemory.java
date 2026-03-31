package com.virtualhuman.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "semantic_memories")
public class SemanticMemory {

    @Id
    private String id;

    private String avatarId;

    // A generalized fact extracted from multiple episodic memories
    // e.g. "I enjoy talking to Bob" or "I am highly stressed by the computer"
    private String fact;

    // How important this fact is to the NPC's core identity (0.0 to 1.0)
    private double importance;
}
