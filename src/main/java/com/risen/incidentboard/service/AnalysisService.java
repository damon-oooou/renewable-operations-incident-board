package com.risen.incidentboard.service;

import com.risen.incidentboard.domain.*;
import com.risen.incidentboard.repo.AlertRepository;
import com.risen.incidentboard.service.classifier.Classification;
import com.risen.incidentboard.service.classifier.KeywordAlertClassifier;
import com.risen.incidentboard.service.classifier.LlmAlertClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private final AlertRepository alerts;
    private final LlmAlertClassifier llm;
    private final KeywordAlertClassifier keyword;
    private final String ruleVersion;

    public AnalysisService(AlertRepository alerts,
                           LlmAlertClassifier llm,
                           KeywordAlertClassifier keyword,
                           @Value("${ai.rule-version}") String ruleVersion) {
        this.alerts = alerts;
        this.llm = llm;
        this.keyword = keyword;
        this.ruleVersion = ruleVersion;
    }

    /**
     * Classifies the alerts currently passing the filter and persists the result.
     *
     * Scope comes from the filter rather than a list of ids supplied by the
     * client, so the server resolves "what is on screen" using the same query
     * that built the screen. Passing ids would let the client's idea of the
     * current filter drift from the server's.
     */
    @Transactional
    public AnalysisResult analyze(String siteId, List<AlertStatus> statuses) {
        List<Alert> scope = alerts.findOrdered(siteId, statuses);
        Instant runAt = Instant.now();

        int llmCount = 0, fallbackCount = 0, skippedCount = 0;
        List<AnalysisResult.Item> items = new ArrayList<>();

        for (Alert alert : scope) {
            if (!alert.severity().isClassified()) {
                // Medium and low are in scope for the run but deliberately not
                // classified. Reordering the low-severity tail does not change
                // what anyone does next.
                alerts.markSkipped(alert.id(), runAt, ruleVersion);
                skippedCount++;
                items.add(new AnalysisResult.Item(alert.id(), null, null, AiPath.SKIPPED));
                continue;
            }

            Classification classification;
            AiPath path;
            try {
                classification = llm.classify(alert);
                path = AiPath.LLM;
                llmCount++;
            } catch (RuntimeException e) {
                // Every LLM failure mode -- timeout, non-200, malformed JSON,
                // out-of-set signal, missing key -- lands here and degrades to
                // keyword matching rather than failing the request.
                log.warn("LLM classification failed for {}, using keyword fallback: {}",
                        alert.id(), e.getMessage());
                classification = keyword.classify(alert);
                path = AiPath.FALLBACK;
                fallbackCount++;
            }

            alerts.saveClassification(alert.id(), classification.signal(),
                    classification.action(), path, runAt, ruleVersion);
            items.add(new AnalysisResult.Item(alert.id(), classification.signal(),
                    classification.action(), path));
        }

        log.info("Analyze run: {} alerts ({} llm, {} fallback, {} skipped), rules {}",
                scope.size(), llmCount, fallbackCount, skippedCount, ruleVersion);

        return new AnalysisResult(scope.size(), llmCount, fallbackCount, skippedCount,
                llm.isConfigured(), items);
    }
}
