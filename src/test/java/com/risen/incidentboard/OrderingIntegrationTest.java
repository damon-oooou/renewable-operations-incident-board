package com.risen.incidentboard;

import com.risen.incidentboard.domain.Alert;
import com.risen.incidentboard.domain.AlertStatus;
import com.risen.incidentboard.domain.Severity;
import com.risen.incidentboard.repo.AlertRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real ORDER BY against real SQLite with the seeded fixture.
 * Uses a throwaway database file so a developer's working data is untouched.
 */
@SpringBootTest
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:sqlite:target/test-incident-board.db?foreign_keys=on")
class OrderingIntegrationTest {

    @Autowired
    private AlertRepository alerts;

    @Test
    void severityBandsNeverInvert() {
        List<Alert> ordered = alerts.findOrdered(null, Arrays.asList(AlertStatus.values()));
        int previous = -1;
        for (Alert a : ordered) {
            assertThat(a.severity().rank())
                    .as("severity must be non-decreasing down the list")
                    .isGreaterThanOrEqualTo(previous);
            previous = a.severity().rank();
        }
    }

    @Test
    void mediumAndLowEachRunNewestFirst() {
        List<Alert> ordered = alerts.findOrdered(null, Arrays.asList(AlertStatus.values()));

        // Each severity is its own band, so this must be checked per band. The
        // oldest medium legitimately precedes the newest low; asserting over the
        // two combined would fail against correct output.
        for (Severity severity : List.of(Severity.MEDIUM, Severity.LOW)) {
            List<Alert> band = ordered.stream().filter(a -> a.severity() == severity).toList();
            for (int i = 1; i < band.size(); i++) {
                assertThat(band.get(i).occurredAt())
                        .as("%s band must run newest first", severity)
                        .isBeforeOrEqualTo(band.get(i - 1).occurredAt());
            }
        }
    }

    @Test
    void openFilterExcludesClosedAlerts() {
        List<Alert> open = alerts.findOrdered(null, AlertStatus.OPEN);
        assertThat(open).isNotEmpty();
        assertThat(open).noneMatch(a -> a.status().isClosed());
    }

    @Test
    void seedingIsIdempotent() {
        // The context has already started once. The count is the fixture size,
        // not a multiple of it.
        assertThat(alerts.count()).isEqualTo(20);
    }
}
