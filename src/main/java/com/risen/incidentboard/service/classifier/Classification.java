package com.risen.incidentboard.service.classifier;

import com.risen.incidentboard.domain.AiSignal;

public record Classification(AiSignal signal, String action) { }
