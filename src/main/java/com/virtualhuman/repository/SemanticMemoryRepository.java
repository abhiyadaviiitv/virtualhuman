package com.virtualhuman.repository;

import com.virtualhuman.model.SemanticMemory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SemanticMemoryRepository extends MongoRepository<SemanticMemory, String> {

    // Find all semantic facts for an avatar, sorted by importance
    List<SemanticMemory> findByAvatarIdOrderByImportanceDesc(String avatarId);
}
