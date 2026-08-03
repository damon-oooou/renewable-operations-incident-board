package com.risen.incidentboard.seed;

import com.risen.incidentboard.domain.*;
import com.risen.incidentboard.repo.AlertRepository;
import com.risen.incidentboard.repo.SiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads sites and alerts on startup. Notes are never seeded -- every row in that
 * table is produced by an operator action or a status change in the running app.
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final SiteRepository sites;
    private final AlertRepository alerts;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public DataSeeder(SiteRepository sites, AlertRepository alerts) {
        this.sites = sites;
        this.alerts = alerts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        // Guard on alerts rather than sites. Sites are inserted first, so
        // guarding on them would leave a window where a crash between the two
        // inserts produces a database that looks seeded but has no alerts.
        // The whole seed runs in one transaction so that window cannot open.
        long existing = alerts.count();
        if (existing > 0) {
            log.info("Seeding skipped: {} alerts already present. "
                    + "Run with --reset for a clean database.", existing);
            return;
        }

        SeedData data;
        try (InputStream in = new ClassPathResource("seed/alerts.json").getInputStream()) {
            data = mapper.readValue(in, SeedData.class);
        }

        Map<String, Site> byId = new HashMap<>();
        for (SeedData.SeedSite s : data.sites()) {
            // Identifiers and free text keep their original case. Only the
            // closed-vocabulary column is normalised: lowercasing SITE-01 would
            // produce a different string, and lowercasing NSW or 85 MW would
            // corrupt text that is displayed verbatim.
            Site site = new Site(s.id(), s.name(), s.region(),
                    Technology.valueOf(upper(s.technology())), s.capacity());
            byId.put(site.id(), site);
            sites.insert(site);
        }

        for (SeedData.SeedAlert a : data.alerts()) {
            Site site = byId.get(a.siteId());
            if (site == null) {
                throw new IllegalStateException(
                        "Alert " + a.id() + " references unknown site " + a.siteId());
            }
            alerts.insert(Alert.unanalysed(
                    a.id(),
                    site,
                    toUtc(a.occurredAt()),
                    a.type().trim().toLowerCase(Locale.ROOT),
                    Severity.valueOf(upper(a.severity())),
                    AlertStatus.valueOf(upper(a.status())),
                    a.description()));
        }

        log.info("Seeded {} sites and {} alerts", data.sites().size(), data.alerts().size());
    }

    private static String upper(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * The fixture carries source offsets: +10:00 for NSW, QLD and VIC, +09:30
     * for SA. Storing those as-is would risk breaking ordering, because SQLite
     * compares TEXT lexicographically and 12:10+09:30 sorts before 12:20+10:00
     * despite occurring twenty minutes later. Converting here removes the
     * problem at the only point where it can be introduced.
     */
    private static Instant toUtc(String timestamp) {
        return OffsetDateTime.parse(timestamp).toInstant();
    }
}
