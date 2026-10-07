package com.cricketplex.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class WeatherService {

    // Weather conditions
    public static final String SUNNY = "Sunny";
    public static final String PARTLY_CLOUDY = "Partly Cloudy";
    public static final String OVERCAST = "Overcast";
    public static final String LIGHT_RAIN = "Light Rain";
    public static final String HEAVY_RAIN = "Heavy Rain";
    public static final String HOT_HUMID = "Hot & Humid";
    public static final String WINDY = "Windy";
    public static final String FOGGY = "Foggy";

    // Country climate profiles: {conditions[], weights[], tempMin, tempMax, humidityMin, humidityMax}
    private static final Map<String, ClimateProfile> CLIMATE_PROFILES = new LinkedHashMap<>();

    static {
        // Hot dry
        CLIMATE_PROFILES.put("India", new ClimateProfile(
                new String[]{SUNNY, HOT_HUMID, PARTLY_CLOUDY, OVERCAST, LIGHT_RAIN, HEAVY_RAIN, WINDY, FOGGY},
                new int[]{25, 25, 15, 10, 10, 5, 5, 5}, 28, 42, 50, 85));
        CLIMATE_PROFILES.put("Pakistan", new ClimateProfile(
                new String[]{SUNNY, HOT_HUMID, PARTLY_CLOUDY, OVERCAST, WINDY, LIGHT_RAIN, FOGGY, HEAVY_RAIN},
                new int[]{30, 20, 15, 10, 10, 8, 5, 2}, 30, 44, 35, 70));
        CLIMATE_PROFILES.put("Sri Lanka", new ClimateProfile(
                new String[]{HOT_HUMID, SUNNY, PARTLY_CLOUDY, LIGHT_RAIN, HEAVY_RAIN, OVERCAST, WINDY, FOGGY},
                new int[]{25, 20, 15, 15, 10, 8, 5, 2}, 27, 35, 65, 90));
        CLIMATE_PROFILES.put("Bangladesh", new ClimateProfile(
                new String[]{HOT_HUMID, LIGHT_RAIN, SUNNY, PARTLY_CLOUDY, HEAVY_RAIN, OVERCAST, FOGGY, WINDY},
                new int[]{25, 20, 15, 15, 10, 8, 5, 2}, 26, 36, 70, 95));

        // Temperate / overcast
        CLIMATE_PROFILES.put("England", new ClimateProfile(
                new String[]{OVERCAST, PARTLY_CLOUDY, LIGHT_RAIN, SUNNY, WINDY, FOGGY, HEAVY_RAIN, HOT_HUMID},
                new int[]{25, 20, 18, 12, 10, 8, 5, 2}, 12, 24, 60, 85));
        CLIMATE_PROFILES.put("Ireland", new ClimateProfile(
                new String[]{OVERCAST, LIGHT_RAIN, PARTLY_CLOUDY, WINDY, FOGGY, SUNNY, HEAVY_RAIN, HOT_HUMID},
                new int[]{25, 22, 15, 15, 10, 8, 4, 1}, 10, 20, 65, 90));
        CLIMATE_PROFILES.put("Scotland", new ClimateProfile(
                new String[]{OVERCAST, LIGHT_RAIN, WINDY, PARTLY_CLOUDY, FOGGY, SUNNY, HEAVY_RAIN, HOT_HUMID},
                new int[]{25, 20, 18, 15, 10, 7, 4, 1}, 8, 18, 65, 90));
        CLIMATE_PROFILES.put("Netherlands", new ClimateProfile(
                new String[]{OVERCAST, PARTLY_CLOUDY, LIGHT_RAIN, WINDY, SUNNY, FOGGY, HEAVY_RAIN, HOT_HUMID},
                new int[]{22, 20, 18, 15, 12, 7, 4, 2}, 10, 22, 60, 85));

        // Hot & dry
        CLIMATE_PROFILES.put("Australia", new ClimateProfile(
                new String[]{SUNNY, HOT_HUMID, PARTLY_CLOUDY, WINDY, OVERCAST, LIGHT_RAIN, FOGGY, HEAVY_RAIN},
                new int[]{35, 20, 15, 10, 8, 6, 4, 2}, 25, 40, 30, 65));
        CLIMATE_PROFILES.put("United Arab Emirates", new ClimateProfile(
                new String[]{SUNNY, HOT_HUMID, PARTLY_CLOUDY, WINDY, OVERCAST, FOGGY, LIGHT_RAIN, HEAVY_RAIN},
                new int[]{40, 25, 15, 8, 5, 4, 2, 1}, 32, 48, 25, 60));
        CLIMATE_PROFILES.put("Oman", new ClimateProfile(
                new String[]{SUNNY, HOT_HUMID, PARTLY_CLOUDY, WINDY, OVERCAST, FOGGY, LIGHT_RAIN, HEAVY_RAIN},
                new int[]{38, 25, 15, 10, 5, 4, 2, 1}, 30, 46, 25, 55));

        // Moderate warm
        CLIMATE_PROFILES.put("South Africa", new ClimateProfile(
                new String[]{SUNNY, PARTLY_CLOUDY, WINDY, OVERCAST, LIGHT_RAIN, HOT_HUMID, FOGGY, HEAVY_RAIN},
                new int[]{30, 20, 15, 12, 8, 7, 5, 3}, 20, 34, 35, 70));
        CLIMATE_PROFILES.put("Zimbabwe", new ClimateProfile(
                new String[]{SUNNY, PARTLY_CLOUDY, HOT_HUMID, OVERCAST, LIGHT_RAIN, WINDY, HEAVY_RAIN, FOGGY},
                new int[]{28, 20, 15, 12, 10, 7, 5, 3}, 22, 35, 40, 75));
        CLIMATE_PROFILES.put("West Indies", new ClimateProfile(
                new String[]{SUNNY, HOT_HUMID, PARTLY_CLOUDY, LIGHT_RAIN, WINDY, OVERCAST, HEAVY_RAIN, FOGGY},
                new int[]{25, 25, 15, 12, 10, 7, 5, 1}, 27, 34, 60, 85));

        // Mild / variable
        CLIMATE_PROFILES.put("New Zealand", new ClimateProfile(
                new String[]{PARTLY_CLOUDY, SUNNY, OVERCAST, LIGHT_RAIN, WINDY, FOGGY, HEAVY_RAIN, HOT_HUMID},
                new int[]{22, 20, 18, 15, 12, 7, 4, 2}, 14, 26, 55, 80));
        CLIMATE_PROFILES.put("United States", new ClimateProfile(
                new String[]{SUNNY, PARTLY_CLOUDY, HOT_HUMID, OVERCAST, WINDY, LIGHT_RAIN, FOGGY, HEAVY_RAIN},
                new int[]{25, 20, 15, 12, 10, 8, 6, 4}, 20, 35, 40, 75));

        // Mountain / dry
        CLIMATE_PROFILES.put("Afghanistan", new ClimateProfile(
                new String[]{SUNNY, HOT_HUMID, PARTLY_CLOUDY, WINDY, OVERCAST, FOGGY, LIGHT_RAIN, HEAVY_RAIN},
                new int[]{35, 20, 15, 12, 8, 5, 4, 1}, 28, 42, 20, 50));
        CLIMATE_PROFILES.put("Nepal", new ClimateProfile(
                new String[]{SUNNY, PARTLY_CLOUDY, OVERCAST, LIGHT_RAIN, FOGGY, WINDY, HOT_HUMID, HEAVY_RAIN},
                new int[]{22, 18, 16, 15, 12, 8, 5, 4}, 18, 32, 50, 80));
    }

    /**
     * Generate deterministic weather for a country + date.
     * Uses hash of (country + date) as RNG seed for consistency.
     */
    public Map<String, Object> getWeather(String country, LocalDate date) {
        long seed = (country + "|" + date.toString()).hashCode();
        Random rng = new Random(seed);

        ClimateProfile profile = CLIMATE_PROFILES.getOrDefault(country,
                new ClimateProfile(
                        new String[]{SUNNY, PARTLY_CLOUDY, OVERCAST, LIGHT_RAIN, WINDY, FOGGY, HOT_HUMID, HEAVY_RAIN},
                        new int[]{20, 18, 15, 12, 12, 10, 8, 5}, 20, 32, 40, 70));

        // Pick condition based on weighted random
        String condition = pickWeighted(rng, profile.conditions, profile.weights);

        // Temperature with some variance
        int temp = profile.tempMin + rng.nextInt(profile.tempMax - profile.tempMin + 1);

        // Humidity
        int humidity = profile.humidityMin + rng.nextInt(profile.humidityMax - profile.humidityMin + 1);

        // Adjust for condition
        if (condition.equals(HEAVY_RAIN) || condition.equals(LIGHT_RAIN)) {
            humidity = Math.min(99, humidity + 10);
            temp = Math.max(profile.tempMin, temp - 3);
        } else if (condition.equals(SUNNY) || condition.equals(HOT_HUMID)) {
            temp = Math.min(profile.tempMax, temp + 2);
        } else if (condition.equals(FOGGY)) {
            temp = Math.max(profile.tempMin, temp - 4);
            humidity = Math.min(99, humidity + 15);
        }

        Map<String, Object> weather = new LinkedHashMap<>();
        weather.put("condition", condition);
        weather.put("temperature", temp);
        weather.put("humidity", humidity);
        weather.put("icon", getIcon(condition));
        return weather;
    }

    /**
     * Get 7-day weather forecast for a country starting from a given date.
     */
    public List<Map<String, Object>> getForecast(String country, LocalDate startDate) {
        List<Map<String, Object>> forecast = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = startDate.plusDays(i);
            Map<String, Object> day = getWeather(country, date);
            day.put("date", date.toString());
            forecast.add(day);
        }
        return forecast;
    }

    /**
     * Get weather for a match; returns null-like "not available" if more than 7 days away.
     */
    public Map<String, Object> getMatchWeather(String homeCountry, LocalDate matchDate, LocalDate today) {
        long daysUntil = ChronoUnit.DAYS.between(today, matchDate);
        if (daysUntil > 7) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", false);
            result.put("message", "Forecast not yet available");
            return result;
        }
        Map<String, Object> weather = getWeather(homeCountry, matchDate);
        weather.put("available", true);
        return weather;
    }

    private String pickWeighted(Random rng, String[] items, int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int roll = rng.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < items.length; i++) {
            cumulative += weights[i];
            if (roll < cumulative) return items[i];
        }
        return items[0];
    }

    private String getIcon(String condition) {
        return switch (condition) {
            case SUNNY -> "☀️";
            case PARTLY_CLOUDY -> "⛅";
            case OVERCAST -> "☁️";
            case LIGHT_RAIN -> "🌦️";
            case HEAVY_RAIN -> "🌧️";
            case HOT_HUMID -> "🔥";
            case WINDY -> "💨";
            case FOGGY -> "🌫️";
            default -> "🌤️";
        };
    }

    private static class ClimateProfile {
        final String[] conditions;
        final int[] weights;
        final int tempMin, tempMax, humidityMin, humidityMax;

        ClimateProfile(String[] conditions, int[] weights, int tempMin, int tempMax, int humidityMin, int humidityMax) {
            this.conditions = conditions;
            this.weights = weights;
            this.tempMin = tempMin;
            this.tempMax = tempMax;
            this.humidityMin = humidityMin;
            this.humidityMax = humidityMax;
        }
    }
}
