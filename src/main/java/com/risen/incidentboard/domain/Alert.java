package com.risen.incidentboard.domain;

import java.time.Instant;

/**
 * Immutable row type carrying its joined site.
 *
 * With no ORM there is no managed entity and no dirty checking, so a change is
 * always an explicit UPDATE through the repository. That is a fair trade for
 * three tables with no object graph -- and it removes the class of bug where a
 * setter silently persists at flush time.
 *
 * There is no way to construct an Alert with a different severity: severity is
 * ground truth from the source system and nothing in the application changes it.
 */
public record Alert(String id,
                    Site site,
                    Instant occurredAt,
                    String type,
                    Severity severity,
                    AlertStatus status,
                    String description,
                    AiSignal aiSignal,
                    String aiAction,
                    AiPath aiPath,
                    Instant aiRunAt,
                    String aiRuleVersion) {

    /** A freshly seeded alert: no analysis run has covered it yet. */
    public static Alert unanalysed(String id, Site site, Instant occurredAt, String type,
                                   Severity severity, AlertStatus status, String description) {
        return new Alert(id, site, occurredAt, type, severity, status, description,
                null, null, null, null, null);
    }
}
