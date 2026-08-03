package com.risen.incidentboard.web.dto;

import com.risen.incidentboard.domain.Alert;
import com.risen.incidentboard.domain.Note;
import com.risen.incidentboard.domain.Site;
import com.risen.incidentboard.service.AnalysisResult;

import java.util.List;

public final class Dtos {

    private Dtos() { }

    public record SiteView(String id, String name, String region,
                           String technology, String capacity) {
        public static SiteView of(Site s) {
            return new SiteView(s.id(), s.name(), s.region(),
                    s.technology().name().toLowerCase(), s.capacity());
        }
    }

    public record AlertView(String id, String siteId, String siteName, String region,
                            String occurredAt, String type, String severity, String status,
                            String description, String aiSignal, String aiAction,
                            String aiPath, String aiRunAt, String aiRuleVersion) {
        public static AlertView of(Alert a) {
            return new AlertView(
                    a.id(),
                    a.site().id(),
                    a.site().name(),
                    a.site().region(),
                    a.occurredAt().toString(),
                    a.type(),
                    a.severity().name().toLowerCase(),
                    a.status().name().toLowerCase(),
                    a.description(),
                    a.aiSignal() == null ? null : a.aiSignal().name().toLowerCase(),
                    a.aiAction(),
                    a.aiPath() == null ? null : a.aiPath().name().toLowerCase(),
                    a.aiRunAt() == null ? null : a.aiRunAt().toString(),
                    a.aiRuleVersion());
        }
    }

    public record NoteView(Long id, String body, String author, String createdAt) {
        public static NoteView of(Note n) {
            return new NoteView(n.id(), n.body(), n.author(), n.createdAt().toString());
        }
    }

    public record AlertListResponse(List<AlertView> alerts, long hiddenCount) { }

    public record AlertDetailResponse(AlertView alert, List<NoteView> notes) { }

    public record AnalyzeRequest(String siteId, String status) { }

    public record AnalyzeResponse(int analyzed, int llm, int fallback, int skipped,
                                  boolean apiKeyConfigured) {
        public static AnalyzeResponse of(AnalysisResult r) {
            return new AnalyzeResponse(r.analyzed(), r.llm(), r.fallback(),
                    r.skipped(), r.apiKeyConfigured());
        }
    }

    public record StatusRequest(String status) { }

    public record NoteRequest(String body) { }

    public record ApiError(String error) { }
}
