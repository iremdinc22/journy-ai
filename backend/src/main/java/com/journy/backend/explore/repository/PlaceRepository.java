package com.journy.backend.explore.repository;

import com.journy.backend.explore.model.Place;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, String> {
    List<Place> findTop12ByOrderByRatingDesc();

    List<Place> findByCategoryOrderByRatingDesc(PlaceCategory category);
}
