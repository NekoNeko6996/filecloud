package com.app.filecloud.entity.key;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class MovieCreditId implements Serializable {
    @Column(name = "movie_id")
    private String movieId;

    @Column(name = "person_id")
    private String personId;

    @Column(name = "role")
    private String role; // Director, Actor...
}