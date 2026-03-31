package com.virtualhuman.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;

@Data
@Document(collection = "avatar_profiles")
public class AvatarProfile {
    @Id
    private String avatarId;
    private Personality personality;
    private Map<String, Object> attributes;
    private String goal;
}
