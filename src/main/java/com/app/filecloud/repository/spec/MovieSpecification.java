package com.app.filecloud.repository.spec;

import com.app.filecloud.entity.Movie;
import com.app.filecloud.entity.MovieAlternativeTitle;
import com.app.filecloud.entity.Studio;
import com.app.filecloud.entity.Tag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.CollectionUtils;

public class MovieSpecification {

    public static Specification<Movie> filterMovies(String keyword, Integer year, String studioId, List<String> tagIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Search (Title OR Alt Title)
            if (StringUtils.hasText(keyword)) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                Join<Movie, MovieAlternativeTitle> altTitleJoin = root.join("alternativeTitles", JoinType.LEFT);
                Predicate titleLike = cb.like(cb.lower(root.get("title")), likePattern);
                Predicate altTitleLike = cb.like(cb.lower(altTitleJoin.get("altTitle")), likePattern);
                predicates.add(cb.or(titleLike, altTitleLike));
            }

            // 2. Year
            if (year != null) {
                predicates.add(cb.equal(root.get("releaseYear"), year));
            }

            // 3. Studio
            if (StringUtils.hasText(studioId)) {
                Join<Movie, Studio> studioJoin = root.join("studios", JoinType.INNER);
                predicates.add(cb.equal(studioJoin.get("id"), studioId));
            }

            // 4. Tags (Multi-select)
            if (!CollectionUtils.isEmpty(tagIds)) {
                Join<Movie, Tag> tagJoin = root.join("tags", JoinType.INNER);
                // Logic: Tag ID nằm trong danh sách các ID đã chọn (IN clause)
                predicates.add(tagJoin.get("id").in(tagIds));
            }

            query.distinct(true);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
