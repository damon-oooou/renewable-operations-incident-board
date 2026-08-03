package com.risen.incidentboard.domain;

/**
 * The closed set the classifier may return, in priority order.
 *
 * The ordering scale is the reason this is an enum and not a score: the same
 * description always lands in the same bucket, so the list order is reproducible
 * and testable. Note that NONE outranks LIKELY_TRANSIENT -- a description that
 * positively says the issue has passed is demoted below one the classifier had
 * nothing to say about. Absence of a signal is not evidence of harmlessness.
 */
public enum AiSignal {
    SAFETY_HAZARD(0,        "Restrict site access, dispatch crew"),
    ESCALATION_RISK(1,      "Monitor closely, prepare to isolate"),
    SITE_WIDE_IMPACT(2,     "Confirm site availability, notify dispatch"),
    FIELD_VISIT_REQUIRED(3, "Raise a work order, schedule a site visit"),
    NONE(4,                 "Review at next handover"),
    LIKELY_TRANSIENT(5,     "Log and close if no recurrence");

    private final int priority;
    private final String fallbackAction;

    AiSignal(int priority, String fallbackAction) {
        this.priority = priority;
        this.fallbackAction = fallbackAction;
    }

    public int priority() { return priority; }

    /** Canned action used when the keyword classifier ran instead of the model. */
    public String fallbackAction() { return fallbackAction; }
}
