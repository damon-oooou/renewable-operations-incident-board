package com.risen.incidentboard.domain;

/** Ground truth from the source system. Never modified by the application. */
public enum Severity {
    CRITICAL(0), HIGH(1), MEDIUM(2), LOW(3);

    private final int rank;

    Severity(int rank) { this.rank = rank; }

    public int rank() { return rank; }

    /** Only the top two tiers are sent to the classifier. See design doc 6.2. */
    public boolean isClassified() { return this == CRITICAL || this == HIGH; }
}
