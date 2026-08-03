package com.risen.incidentboard.service.classifier;

/** Raised by the LLM classifier only. The keyword classifier never throws. */
public class ClassificationException extends RuntimeException {
    public ClassificationException(String message) { super(message); }
    public ClassificationException(String message, Throwable cause) { super(message, cause); }
}
