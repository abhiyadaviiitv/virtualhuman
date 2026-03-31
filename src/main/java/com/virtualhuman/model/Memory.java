package com.virtualhuman.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "memories")
public class Memory {
    @Id
    private String id;
    private String avatarId;
    private String activity;
    private LocalDateTime timestamp;
}
