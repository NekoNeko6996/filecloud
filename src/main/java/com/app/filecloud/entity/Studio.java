package com.app.filecloud.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Entity
@Getter 
@Setter
@NoArgsConstructor 
@AllArgsConstructor
@Builder
@Table(name = "studios")
public class Studio {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String name;

    @ManyToMany(mappedBy = "studios")
    private Set<Movie> movies;
    
    private String slug;
}