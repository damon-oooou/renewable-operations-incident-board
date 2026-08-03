package com.risen.incidentboard.seed;

import java.util.List;

/** Shape of the seed fixture. Values arrive uppercase and are normalised on load. */
public record SeedData(List<SeedSite> sites, List<SeedAlert> alerts) {

    public record SeedSite(String id, String name, String region,
                           String technology, String capacity) { }

    public record SeedAlert(String id, String siteId, String occurredAt, String type,
                            String severity, String status, String description) { }
}
