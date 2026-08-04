package com.risen.incidentboard;

import com.risen.incidentboard.domain.*;
import com.risen.incidentboard.repo.AlertRepository;
import com.risen.incidentboard.service.AnalysisResult;
import com.risen.incidentboard.service.AnalysisService;
import com.risen.incidentboard.service.classifier.ClassificationException;
import com.risen.incidentboard.service.classifier.KeywordAlertClassifier;
import com.risen.incidentboard.service.classifier.LlmAlertClassifier;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Every LLM failure mode is exercised through the classifier seam, so these run
 * with no network, no API key and no flakiness.
 */
class AnalysisServiceTest {

    private final Site site = new Site("SITE-01", "Test Site", "NSW", Technology.SOLAR, "85 MW");

    private Alert alert(String id, Severity severity) {
        return Alert.unanalysed(id, site, Instant.parse("2026-08-03T00:00:00Z"),
                "inverter_fault", severity, AlertStatus.NEW, "Smoke detected in container B04.");
    }

    @Test
    void fallsBackWhenTheModelFails() {
        AlertRepository repo = mock(AlertRepository.class);
        when(repo.findOrdered(any(), any())).thenReturn(List.of(alert("ALT-0001", Severity.CRITICAL)));
        LlmAlertClassifier llm = mock(LlmAlertClassifier.class);
        when(llm.classify(any())).thenThrow(new ClassificationException("timeout"));

        AnalysisResult result = new AnalysisService(repo, llm, new KeywordAlertClassifier(), "v1")
                .analyze(null, AlertStatus.OPEN);

        verify(repo).saveClassification(eq("ALT-0001"), eq(AiSignal.SAFETY_HAZARD),
                anyString(), eq(AiPath.FALLBACK), any(), eq("v1"));
        assertThat(result.fallback()).isEqualTo(1);
    }

    @Test
    void aFailedRunStillReturnsNormally() {
        // The fallback is a designed path, not an error. The run completes.
        AlertRepository repo = mock(AlertRepository.class);
        when(repo.findOrdered(any(), any())).thenReturn(List.of(alert("ALT-0001", Severity.HIGH)));
        LlmAlertClassifier llm = mock(LlmAlertClassifier.class);
        when(llm.classify(any())).thenThrow(new RuntimeException("anything at all"));

        AnalysisResult result = new AnalysisService(repo, llm, new KeywordAlertClassifier(), "v1")
                .analyze(null, AlertStatus.OPEN);

        assertThat(result.analyzed()).isEqualTo(1);
    }

    @Test
    void mediumAndLowAreSkippedNotClassified() {
        AlertRepository repo = mock(AlertRepository.class);
        when(repo.findOrdered(any(), any())).thenReturn(List.of(alert("ALT-0002", Severity.MEDIUM)));
        LlmAlertClassifier llm = mock(LlmAlertClassifier.class);

        AnalysisResult result = new AnalysisService(repo, llm, new KeywordAlertClassifier(), "v1")
                .analyze(null, AlertStatus.OPEN);

        // Not verifyNoInteractions: the service legitimately calls isConfigured()
        // when building the result, so the mock IS touched. What matters is that
        // the alert was never sent for classification.
        verify(llm, never()).classify(any());
        // The run stamp is still written: that is what distinguishes "we decided
        // not to classify this" from "no run has reached it".
        verify(repo).markSkipped(eq("ALT-0002"), any(), eq("v1"));
        verify(repo, never()).saveClassification(any(), any(), any(), any(), any(), any());
        assertThat(result.skipped()).isEqualTo(1);
    }
}
