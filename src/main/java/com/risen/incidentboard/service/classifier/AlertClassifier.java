package com.risen.incidentboard.service.classifier;

import com.risen.incidentboard.domain.Alert;

/**
 * One seam, two implementations. Every failure mode of the LLM path is
 * exercised in tests by injecting a stub through this interface -- no network,
 * no API key, no flakiness in CI.
 */
public interface AlertClassifier {
    Classification classify(Alert alert);
}
