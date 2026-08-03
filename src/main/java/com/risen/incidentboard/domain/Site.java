package com.risen.incidentboard.domain;

/** Immutable row type. Identifiers keep their source case: SITE-01 is the id. */
public record Site(String id, String name, String region,
                   Technology technology, String capacity) { }
