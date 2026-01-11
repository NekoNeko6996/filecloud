package com.app.filecloud.entity;

import com.app.filecloud.entity.key.MovieCreditId;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movie_credits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MovieCredit {

    @EmbeddedId
    private MovieCreditId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("movieId") // Map phần movieId trong EmbeddedId với object Movie
    @JoinColumn(name = "movie_id")
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("personId") // Map phần personId trong EmbeddedId với object Person
    @JoinColumn(name = "person_id")
    private Person person;

    // role đã nằm trong khoá chính (EmbeddedId) nên không map lại field ở đây để tránh lỗi duplicate column mapping

    @Column(name = "character_name")
    private String characterName;
    
    // Helper để set role dễ hơn
    public void setRole(String role) {
        if (this.id == null) this.id = new MovieCreditId();
        this.id.setRole(role);
    }
    
    public String getRole() {
        return this.id != null ? this.id.getRole() : null;
    }
}