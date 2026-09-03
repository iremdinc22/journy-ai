package com.journy.backend.explore.repository;

import com.journy.backend.explore.model.Place;
import com.journy.backend.place.enums.PlaceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, String> {
    List<Place> findTop12ByOrderByRatingDesc();

    List<Place> findByCategoryOrderByRatingDesc(PlaceCategory category);

    List<Place> findByCityIgnoreCaseOrderByRatingDesc(String city);

    List<Place> findByCityIgnoreCaseAndCategoryOrderByRatingDesc(String city, PlaceCategory category);

    @Query("select distinct p.city from Place p order by p.city asc")
    List<String> findDistinctCities();

    long countByCityIgnoreCase(String city);

    long countByCityIgnoreCaseAndCategoryIn(String city, Collection<PlaceCategory> categories);

    Optional<Place> findByNameIgnoreCaseAndCityIgnoreCase(String name, String city);

    Optional<Place> findByProviderIgnoreCaseAndProviderPlaceId(String provider, String providerPlaceId);

    @Query("""
            select p from Place p
            where lower(p.city) = lower(:city)
              and p.provider is not null
              and lower(p.provider) not in ('seed', 'starter')
              and p.providerFetchedAt is not null
            order by p.rating desc
            """)
    List<Place> findProviderCachedByCity(@Param("city") String city);

    @Query("""
            select p from Place p
            where lower(p.city) = lower(:city)
              and p.category = :category
              and p.provider is not null
              and lower(p.provider) not in ('seed', 'starter')
              and p.providerFetchedAt is not null
            order by p.rating desc
            """)
    List<Place> findProviderCachedByCityAndCategory(@Param("city") String city, @Param("category") PlaceCategory category);

    @Query("""
            select p from Place p
            where lower(p.city) = lower(:city)
              and p.provider is not null
              and lower(p.provider) not in ('seed', 'starter')
              and p.providerFetchedAt is not null
              and p.providerFetchedAt >= :cutoff
            order by p.rating desc
            """)
    List<Place> findFreshProviderCachedByCity(@Param("city") String city, @Param("cutoff") Instant cutoff);

    @Query("""
            select p from Place p
            where lower(p.city) = lower(:city)
              and p.category = :category
              and p.provider is not null
              and lower(p.provider) not in ('seed', 'starter')
              and p.providerFetchedAt is not null
              and p.providerFetchedAt >= :cutoff
            order by p.rating desc
            """)
    List<Place> findFreshProviderCachedByCityAndCategory(
            @Param("city") String city,
            @Param("category") PlaceCategory category,
            @Param("cutoff") Instant cutoff
    );
}
