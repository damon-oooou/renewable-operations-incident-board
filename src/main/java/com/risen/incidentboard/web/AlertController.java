package com.risen.incidentboard.web;

import com.risen.incidentboard.domain.*;
import com.risen.incidentboard.service.AlertService;
import com.risen.incidentboard.service.AnalysisResult;
import com.risen.incidentboard.service.AnalysisService;
import com.risen.incidentboard.web.dto.Dtos.*;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alerts;
    private final AnalysisService analysis;

    public AlertController(AlertService alerts, AnalysisService analysis) {
        this.alerts = alerts;
        this.analysis = analysis;
    }

    @GetMapping
    @Operation(summary = "List alerts, filtered and ordered",
            description = "Ordered by severity, then AI signal priority within the "
                    + "critical and high tiers, then newest first. Ordering is computed "
                    + "server-side; the client renders the array as given.")
    public AlertListResponse list(@RequestParam(required = false) String siteId,
                                  @RequestParam(required = false) String status) {
        List<AlertStatus> statuses = StatusFilter.parse(status);
        List<Alert> found = alerts.list(siteId, statuses);
        long hidden = alerts.hiddenCount(siteId, statuses, found.size());
        return new AlertListResponse(found.stream().map(AlertView::of).toList(), hidden);
    }

    @GetMapping("/{id}")
    @Operation(summary = "One alert with its full note timeline")
    public AlertDetailResponse detail(@PathVariable String id) {
        Alert alert = alerts.get(id);
        List<NoteView> notes = alerts.notesFor(id).stream().map(NoteView::of).toList();
        return new AlertDetailResponse(AlertView.of(alert), notes);
    }

    @PostMapping("/analyze")
    @Operation(summary = "Run AI analysis over the alerts passing the current filter",
            description = "Classifies critical and high alerts and persists the result. "
                    + "Medium and low are recorded as skipped. Returns 200 even when every "
                    + "call fell back, because the fallback is a designed path rather than "
                    + "an error; degradation is reported in the payload.")
    public AnalyzeResponse analyze(@RequestBody(required = false) AnalyzeRequest request) {
        String siteId = request == null ? null : request.siteId();
        String status = request == null ? null : request.status();
        AnalysisResult result = analysis.analyze(siteId, StatusFilter.parse(status));
        return AnalyzeResponse.of(result);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change status",
            description = "Any transition is allowed, including backward. Writes a "
                    + "system-authored note recording the transition, in the same "
                    + "transaction.")
    public AlertView changeStatus(@PathVariable String id, @RequestBody StatusRequest request) {
        if (request == null || request.status() == null || request.status().isBlank()) {
            throw new IllegalArgumentException("A target status is required");
        }
        AlertStatus target;
        try {
            target = AlertStatus.valueOf(request.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + request.status());
        }
        return AlertView.of(alerts.changeStatus(id, target));
    }

    @PostMapping("/{id}/notes")
    @Operation(summary = "Append a note",
            description = "Append-only: there is no endpoint to edit or delete a note. "
                    + "Notes may be added to an alert in any status.")
    public ResponseEntity<NoteView> addNote(@PathVariable String id,
                                            @RequestBody NoteRequest request) {
        Note note = alerts.addNote(id, request == null ? null : request.body());
        return ResponseEntity.status(201).body(NoteView.of(note));
    }
}
