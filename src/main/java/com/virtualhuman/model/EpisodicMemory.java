package com.virtualhuman.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "episodic_memories")
public class EpisodicMemory {

    @Id
    private String id;

    private String avatarId;

    // The exact memory string, e.g. "Ate apple at 15:30"
    private String memoryText;

    // When the memory occurred
    private Instant timestamp;

    // The emotional state of the NPC when this memory was formed, useful for
    // retrieval weighing
    private EmotionalState emotionalContext;
}
