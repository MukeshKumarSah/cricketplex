package com.cricketplex.service;

import com.cricketplex.entity.PlayerFirstName;
import com.cricketplex.entity.PlayerLastName;
import com.cricketplex.repository.PlayerFirstNameRepository;
import com.cricketplex.repository.PlayerLastNameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPlayerService {

    private final PlayerFirstNameRepository firstNameRepo;
    private final PlayerLastNameRepository lastNameRepo;

    /** Capitalize first letter of each word: "ul haq" → "Ul Haq" */
    private String capitalizeName(String name) {
        if (name == null || name.isBlank()) return name;
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                sb.append(c);
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── Stats ──

    public Map<String, Object> getStats() {
        long totalFirst = firstNameRepo.count();
        long totalLast = lastNameRepo.count();

        // Collect all unique countries from both pools
        Set<String> countries = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        countries.addAll(firstNameRepo.findDistinctCountries());
        countries.addAll(lastNameRepo.findDistinctCountries());

        // Per-country stats
        List<Map<String, Object>> countryStats = new ArrayList<>();
        long totalCombinations = 0;
        for (String country : countries) {
            long fn = firstNameRepo.countByCountryIgnoreCase(country);
            long ln = lastNameRepo.countByCountryIgnoreCase(country);
            long combos = fn * ln;
            totalCombinations += combos;
            countryStats.add(Map.of(
                    "country", country,
                    "firstNames", fn,
                    "lastNames", ln,
                    "combinations", combos
            ));
        }

        return Map.of(
                "totalFirstNames", totalFirst,
                "totalLastNames", totalLast,
                "totalCombinations", totalCombinations,
                "countries", countryStats
        );
    }

    // ── First Names ──

    public List<Map<String, Object>> getFirstNamesByCountry(String country) {
        return firstNameRepo.findByCountryIgnoreCase(country).stream()
                .map(fn -> Map.<String, Object>of("id", fn.getId(), "name", fn.getName(), "country", fn.getCountry()))
                .toList();
    }

    @Transactional
    public Map<String, Object> addFirstNames(String country, List<String> names) {
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
        List<PlayerFirstName> toSave = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skipped = 0;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String trimmed = capitalizeName(name.trim());
            String key = trimmed.toLowerCase();
            if (!seen.add(key) || firstNameRepo.existsByNameIgnoreCaseAndCountryIgnoreCase(trimmed, country.trim())) {
                skipped++;
                continue;
            }
            toSave.add(PlayerFirstName.builder().name(trimmed).country(country.trim()).build());
        }
        List<PlayerFirstName> saved = firstNameRepo.saveAll(toSave);
        return Map.of("added", saved.size(), "skipped", skipped);
    }

    @Transactional
    public void deleteFirstName(UUID id) {
        firstNameRepo.deleteById(id);
    }

    // ── Last Names ──

    public List<Map<String, Object>> getLastNamesByCountry(String country) {
        return lastNameRepo.findByCountryIgnoreCase(country).stream()
                .map(ln -> Map.<String, Object>of("id", ln.getId(), "name", ln.getName(), "country", ln.getCountry()))
                .toList();
    }

    @Transactional
    public Map<String, Object> addLastNames(String country, List<String> names) {
        if (country == null || country.isBlank()) {
            throw new IllegalArgumentException("Country is required");
        }
        List<PlayerLastName> toSave = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int skipped = 0;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String trimmed = capitalizeName(name.trim());
            String key = trimmed.toLowerCase();
            if (!seen.add(key) || lastNameRepo.existsByNameIgnoreCaseAndCountryIgnoreCase(trimmed, country.trim())) {
                skipped++;
                continue;
            }
            toSave.add(PlayerLastName.builder().name(trimmed).country(country.trim()).build());
        }
        List<PlayerLastName> saved = lastNameRepo.saveAll(toSave);
        return Map.of("added", saved.size(), "skipped", skipped);
    }

    @Transactional
    public void deleteLastName(UUID id) {
        lastNameRepo.deleteById(id);
    }

    // ── Pool data for a country ──

    public Map<String, Object> getPoolByCountry(String country) {
        List<Map<String, Object>> firstNames = getFirstNamesByCountry(country);
        List<Map<String, Object>> lastNames = getLastNamesByCountry(country);
        return Map.of(
                "country", country,
                "firstNames", firstNames,
                "lastNames", lastNames,
                "combinations", (long) firstNames.size() * lastNames.size()
        );
    }
}
