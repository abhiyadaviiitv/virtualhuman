package com.virtualhuman.repository;

import com.virtualhuman.model.AvatarProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AvatarProfileRepository extends MongoRepository<AvatarProfile, String> {
}
