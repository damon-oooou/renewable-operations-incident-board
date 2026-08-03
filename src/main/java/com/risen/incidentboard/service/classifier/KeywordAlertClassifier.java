package com.risen.incidentboard.service.classifier;

import com.risen.incidentboard.domain.Alert;
import com.risen.incidentboard.domain.AiSignal;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic fallback. Rules are evaluated in signal priority order and the
 * first match wins, so a description mentioning both smoke and a vendor callout
 * classifies as a safety hazard -- consistent with how the ordering scale ranks
 * them.
 *
 * This class must never throw. It has no I/O and no external dependency, and
 * classify() is additionally wrapped so that even an unforeseen runtime error
 * degrades to NONE rather than propagating. An analysis run cannot fail; at
 * worst it produces a less useful ordering.
 */
@Component
public class KeywordAlertClassifier implements AlertClassifier {

    private static final Map<AiSignal, List<String>> RULES = new LinkedHashMap<>();

    static {
        RULES.put(AiSignal.SAFETY_HAZARD, List.of(
                "fire", "smoke", "gas", "thermal runaway", "arc", "overtemperature",
                "over-temperature", "intrusion", "exclusion zone", "injury", "hazard"));
        RULES.put(AiSignal.ESCALATION_RISK, List.of(
                "widening", "rising", "degrading", "worsening", "drifting",
                "deteriorating", "recurring", "failed", "climbing", "diverged",
                "derate", "trending"));
        RULES.put(AiSignal.SITE_WIDE_IMPACT, List.of(
                "site-wide", "complete loss", "entire", "total", "not dispatchable",
                "point of connection", "all ", "unavailable", "isolated"));
        RULES.put(AiSignal.FIELD_VISIT_REQUIRED, List.of(
                "replace", "technician", "crew", "callout", "vendor", "inspect",
                "blocked", "physical", "repair", "found "));
        RULES.put(AiSignal.LIKELY_TRANSIENT, List.of(
                "rode through", "no action required", "restored", "cleared",
                "momentary", "logged for record", "returned to service",
                "no damage", "no equipment fault"));
    }

    @Override
    public Classification classify(Alert alert) {
        AiSignal signal = AiSignal.NONE;
        try {
            String text = alert.description() == null
                    ? ""
                    : alert.description().toLowerCase(Locale.ROOT);
            outer:
            for (Map.Entry<AiSignal, List<String>> rule : RULES.entrySet()) {
                for (String term : rule.getValue()) {
                    if (text.contains(term)) {
                        signal = rule.getKey();
                        break outer;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Contract: never raise. NONE is the safe default -- it neither
            // promotes an alert the operator should not trust nor demotes one
            // below a genuinely transient issue.
            signal = AiSignal.NONE;
        }
        return new Classification(signal, signal.fallbackAction());
    }
}
