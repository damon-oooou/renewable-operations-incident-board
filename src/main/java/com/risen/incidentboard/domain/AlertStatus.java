package com.risen.incidentboard.domain;

import java.util.List;

public enum AlertStatus {
    NEW, ACKNOWLEDGED, INVESTIGATING, RESOLVED, DISMISSED;

    /** The default filter: everything that is not closed. */
    public static final List<AlertStatus> OPEN =
            List.of(NEW, ACKNOWLEDGED, INVESTIGATING);

    public boolean isClosed() { return this == RESOLVED || this == DISMISSED; }
}
