package com.risen.incidentboard.web;

import com.risen.incidentboard.domain.AlertStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Translates the single-select status dropdown into a set of statuses.
 *
 * Resolved and dismissed are hidden by default, but as default filter state
 * rather than a hard exclusion: "all" reaches them, and so does naming either
 * one directly.
 */
public final class StatusFilter {

    public static final String OPEN = "open";
    public static final String ALL = "all";

    private StatusFilter() { }

    public static List<AlertStatus> parse(String param) {
        if (param == null || param.isBlank() || OPEN.equalsIgnoreCase(param)) {
            return AlertStatus.OPEN;
        }
        if (ALL.equalsIgnoreCase(param)) {
            return Arrays.asList(AlertStatus.values());
        }
        try {
            return List.of(AlertStatus.valueOf(param.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status filter: " + param);
        }
    }
}
