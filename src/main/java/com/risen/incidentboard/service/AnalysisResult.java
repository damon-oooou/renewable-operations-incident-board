package com.risen.incidentboard.service;

import com.risen.incidentboard.domain.AiPath;
import com.risen.incidentboard.domain.AiSignal;

import java.util.List;

/** Outcome of one Analyze run: per-alert results plus a summary for the banner. */
public record AnalysisResult(int analyzed, int llm, int fallback, int skipped,
                             boolean apiKeyConfigured, List<Item> items) {

    public record Item(String alertId, AiSignal signal, String action, AiPath path) { }
}
