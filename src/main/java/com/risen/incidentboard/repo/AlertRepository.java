package com.risen.incidentboard.repo;

import com.risen.incidentboard.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository {

    /** Filtered and fully ordered per design doc 5.1. */
    List<Alert> findOrdered(String siteId, List<AlertStatus> statuses);

    /** Alerts matching the site filter regardless of status, for the hidden count. */
    long countBySite(String siteId);

    long count();

    Optional<Alert> findById(String id);

    void updateStatus(String id, AlertStatus status);

    /** Writes a completed classification. */
    void saveClassification(String id, AiSignal signal, String action, AiPath path,
                            Instant runAt, String ruleVersion);

    /**
     * Records that a run covered the alert but did not classify it. Signal and
     * action stay null; the run stamp is still written, which is what separates
     * "we decided not to" from "nobody has run this yet".
     */
    void markSkipped(String id, Instant runAt, String ruleVersion);

    void insert(Alert alert);
}
