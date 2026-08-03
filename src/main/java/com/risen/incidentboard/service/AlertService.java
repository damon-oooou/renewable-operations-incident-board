package com.risen.incidentboard.service;

import com.risen.incidentboard.domain.*;
import com.risen.incidentboard.repo.AlertRepository;
import com.risen.incidentboard.repo.NoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AlertService {

    private final AlertRepository alerts;
    private final NoteRepository notes;

    public AlertService(AlertRepository alerts, NoteRepository notes) {
        this.alerts = alerts;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public List<Alert> list(String siteId, List<AlertStatus> statuses) {
        return alerts.findOrdered(siteId, statuses);
    }

    /**
     * How many alerts the status filter is withholding, so the UI can say so
     * rather than leaving the operator to wonder why the list is short.
     */
    @Transactional(readOnly = true)
    public long hiddenCount(String siteId, List<AlertStatus> statuses, int shown) {
        if (statuses == null || statuses.isEmpty()) return 0;
        return alerts.countBySite(siteId) - shown;
    }

    @Transactional(readOnly = true)
    public Alert get(String id) {
        return alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Note> notesFor(String alertId) {
        return notes.findByAlertId(alertId);
    }

    /**
     * Status change and its system note are written in one transaction, which is
     * what allows the schema to carry no status history table: the audit trail
     * cannot drift from the current value because they are never written apart.
     */
    @Transactional
    public Alert changeStatus(String id, AlertStatus target) {
        Alert alert = get(id);
        AlertStatus current = alert.status();

        if (current == target) {
            // Rejected rather than accepted as a no-op. Writing "Status changed:
            // investigating -> investigating" would put noise into a log that by
            // design can never be cleaned up.
            throw new IllegalArgumentException(
                    "Alert is already " + target.name().toLowerCase());
        }

        // Any other transition is permitted, including backward. Closing an
        // alert in error is common, and the alternative -- a duplicate alert
        // with no link to the original -- is worse.
        alerts.updateStatus(id, target);
        notes.insert(Note.statusChange(id, current, target, Instant.now()));
        return get(id);
    }

    @Transactional
    public Note addNote(String alertId, String body) {
        get(alertId); // 404 before validation, so a bad id is not reported as a bad body

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Note cannot be empty");
        }
        String trimmed = body.trim();
        if (trimmed.length() > Note.MAX_BODY_LENGTH) {
            throw new IllegalArgumentException(
                    "Note is limited to " + Note.MAX_BODY_LENGTH + " characters");
        }

        // Notes may be added to an alert in any status, including resolved and
        // dismissed. Closing an alert does not close the conversation about it.
        return notes.insert(Note.fromOperator(alertId, trimmed, Instant.now()));
    }
}
