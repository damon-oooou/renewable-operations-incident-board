package com.risen.incidentboard.domain;

import java.time.Instant;

/**
 * Append-only. There is no update path and no delete endpoint -- a note, once
 * written, is part of the operational record.
 *
 * Status changes are also notes, authored by SYSTEM_AUTHOR. That is what lets
 * the schema carry no status history table while still leaving a full audit
 * trail: transitions sit in the same timeline as the operator's own notes.
 */
public record Note(Long id, String alertId, String body, String author, Instant createdAt) {

    public static final String SYSTEM_AUTHOR = "system";
    public static final String OPERATOR_AUTHOR = "operator";
    public static final int MAX_BODY_LENGTH = 2000;

    /** id is null until the insert assigns one. */
    public static Note fromOperator(String alertId, String body, Instant at) {
        return new Note(null, alertId, body, OPERATOR_AUTHOR, at);
    }

    public static Note statusChange(String alertId, AlertStatus from, AlertStatus to, Instant at) {
        String body = "Status changed: %s -> %s"
                .formatted(from.name().toLowerCase(), to.name().toLowerCase());
        return new Note(null, alertId, body, SYSTEM_AUTHOR, at);
    }

    public Note withId(Long assigned) {
        return new Note(assigned, alertId, body, author, createdAt);
    }

    public boolean isSystem() { return SYSTEM_AUTHOR.equals(author); }
}
