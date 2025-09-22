package org.scriptdojo.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
//import jakarta.persistence.OneToMany;
import lombok.Data;
//import java.util.List;

@Entity
@Data
public class UserEntity {
    @Id
    private Long id;

    private String username;
    private String password;
    private String email;

//    @OneToMany(mappedBy = "user")
//    private List<FileEntity> files; // Reference to owned files, to be defined later
}
