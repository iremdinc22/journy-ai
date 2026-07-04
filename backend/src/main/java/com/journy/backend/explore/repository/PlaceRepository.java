package com.journy.backend.explore.repository;

import com.journy.backend.explore.model.Place;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PlaceRepository extends JpaRepository<Place, String> {
    List<Place> findTop12ByOrderByRatingDesc();

    List<Place> findByCategoryOrderByRatingDesc(PlaceCategory category);

    @Query("select distinct p.city from Place p order by p.city asc")
    List<String> findDistinctCities();

    long countByCityIgnoreCase(String city);
}
