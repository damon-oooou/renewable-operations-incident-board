package com.risen.incidentboard;

import com.risen.incidentboard.domain.*;
import com.risen.incidentboard.service.classifier.Classification;
import com.risen.incidentboard.service.classifier.KeywordAlertClassifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordAlertClassifierTest {

    private final KeywordAlertClassifier classifier = new KeywordAlertClassifier();

    private Alert alertWith(String description) {
        Site site = new Site("SITE-01", "Test Site", "NSW", Technology.SOLAR, "85 MW");
        return Alert.unanalysed("ALT-0001", site, Instant.parse("2026-08-03T00:00:00Z"),
                "inverter_fault", Severity.CRITICAL, AlertStatus.NEW, description);
    }

    @Test
    void matchesTheHighestPrioritySignalPresent() {
        // Mentions both a hazard and a crew. First match wins in priority order,
        // so this is a safety hazard, not a field visit -- consistent with how
        // the ordering scale ranks the two.
        assertThat(classifier.classify(alertWith(
                "Smoke detected in container B04. Dispatch a crew to inspect.")).signal())
                .isEqualTo(AiSignal.SAFETY_HAZARD);
    }

    @Test
    void detectsADegradingCondition() {
        assertThat(classifier.classify(alertWith(
                "Cell voltage spread is widening under rising container temperature."))
                .signal()).isEqualTo(AiSignal.ESCALATION_RISK);
    }

    @Test
    void detectsATransientCondition() {
        assertThat(classifier.classify(alertWith(
                "Plant rode through the event. Logged for record, no action required."))
                .signal()).isEqualTo(AiSignal.LIKELY_TRANSIENT);
    }

    @Test
    void unremarkableTextFallsToNone() {
        assertThat(classifier.classify(alertWith("Routine status update.")).signal())
                .isEqualTo(AiSignal.NONE);
    }

    @Test
    void alwaysSuppliesAnAction() {
        Classification result = classifier.classify(alertWith("Routine status update."));
        assertThat(result.action()).isNotBlank();
    }

    @Test
    void neverRaisesOnDegenerateInput() {
        // The contract the whole fallback rests on: an analysis run cannot fail.
        assertThat(classifier.classify(alertWith("")).signal()).isEqualTo(AiSignal.NONE);
        assertThat(classifier.classify(alertWith(null)).signal()).isEqualTo(AiSignal.NONE);
    }
}
