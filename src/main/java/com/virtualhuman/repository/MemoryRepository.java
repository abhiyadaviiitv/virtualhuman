package com.virtualhuman.repository;

import com.virtualhuman.model.Memory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MemoryRepository extends MongoRepository<Memory, String> {
    List<Memory> findByAvatarIdOrderByTimestampDesc(String avatarId);
}
