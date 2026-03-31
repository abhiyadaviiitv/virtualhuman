package com.virtualhuman.service;

import com.virtualhuman.model.SemanticMemory;
import com.virtualhuman.repository.SemanticMemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SemanticMemoryService {

    private final SemanticMemoryRepository semanticMemoryRepository;

    public SemanticMemoryService(SemanticMemoryRepository semanticMemoryRepository) {
        this.semanticMemoryRepository = semanticMemoryRepository;
        seedTestData();
    }

    /**
     * Seeds initial test data for checking prompt injection without needing actual
     * LLM summarization runs yet.
     */
    private void seedTestData() {
        if (semanticMemoryRepository.count() == 0) {
            semanticMemoryRepository
                    .save(new SemanticMemory(null, "test_user1", "I really enjoy coding and find it relaxing.", 0.8));
            semanticMemoryRepository
                    .save(new SemanticMemory(null, "test_user1", "I get stressed when I am hungry.", 0.6));
            semanticMemoryRepository.save(
                    new SemanticMemory(null, "test_user1", "I consider the sofa as my primary place to rest.", 0.9));
        }
    }

    /**
     * Retrieves the most important semantic facts about this NPC and formats them
     * for the LLM prompt.
     */
    public String buildPromptContext(String avatarId) {
        List<SemanticMemory> memories = semanticMemoryRepository.findByAvatarIdOrderByImportanceDesc(avatarId);

        if (memories.isEmpty()) {
            return "No long-term memories or core facts established yet.";
        }

        return memories.stream()
                .limit(5) // Only take the top 5 most important facts to save token context
                .map(m -> "- " + m.getFact())
                .collect(Collectors.joining("\n"));
    }
}
