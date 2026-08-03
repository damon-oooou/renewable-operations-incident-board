package com.risen.incidentboard.domain;

/**
 * Which route produced the stored classification.
 *
 * A null column means no analysis run has covered the alert -- distinct from
 * SKIPPED, which records that a run considered the alert and deliberately did
 * not classify it. Collapsing the two would make it impossible to tell whether
 * a medium-severity alert was excluded by design or simply never reached.
 */
public enum AiPath { LLM, FALLBACK, SKIPPED }
