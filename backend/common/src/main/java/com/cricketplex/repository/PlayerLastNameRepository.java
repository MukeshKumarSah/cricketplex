package com.cricketplex.repository;

import com.cricketplex.entity.PlayerLastName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlayerLastNameRepository extends JpaRepository<PlayerLastName, UUID> {

    List<PlayerLastName> findByCountryIgnoreCase(String country);

    @Query("SELECT DISTINCT LOWER(p.country) FROM PlayerLastName p ORDER BY LOWER(p.country)")
    List<String> findDistinctCountries();

    long countByCountryIgnoreCase(String country);

    @Query(value = "SELECT * FROM player_last_names WHERE LOWER(country) = LOWER(:country) ORDER BY RANDOM() LIMIT :count", nativeQuery = true)
    List<PlayerLastName> findRandomByCountry(@Param("country") String country, @Param("count") int count);

    boolean existsByNameIgnoreCaseAndCountryIgnoreCase(String name, String country);
}
