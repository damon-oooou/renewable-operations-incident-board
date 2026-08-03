package com.risen.incidentboard.repo;

import com.risen.incidentboard.domain.*;
import org.springframework.jdbc.core.RowMapper;

/** Explicit mapping, one place per row type. */
final class RowMappers {

    private RowMappers() { }

    /** Columns are listed explicitly in the queries rather than using a.*, so
     *  the joined site columns cannot collide with the alert columns. */
    static final String ALERT_COLUMNS = """
            a.id, a.site_id, a.occurred_at, a.type, a.severity, a.status, a.description,
            a.ai_signal, a.ai_action, a.ai_path, a.ai_run_at, a.ai_rule_version,
            s.name AS site_name, s.region AS site_region,
            s.technology AS site_technology, s.capacity AS site_capacity
            """;

    static final RowMapper<Site> SITE = (rs, n) -> new Site(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("region"),
            DbValues.toEnum(Technology.class, rs.getString("technology")),
            rs.getString("capacity"));

    static final RowMapper<Alert> ALERT = (rs, n) -> new Alert(
            rs.getString("id"),
            new Site(rs.getString("site_id"),
                     rs.getString("site_name"),
                     rs.getString("site_region"),
                     DbValues.toEnum(Technology.class, rs.getString("site_technology")),
                     rs.getString("site_capacity")),
            DbValues.toInstant(rs.getString("occurred_at")),
            rs.getString("type"),
            DbValues.toEnum(Severity.class, rs.getString("severity")),
            DbValues.toEnum(AlertStatus.class, rs.getString("status")),
            rs.getString("description"),
            DbValues.toEnum(AiSignal.class, rs.getString("ai_signal")),
            rs.getString("ai_action"),
            DbValues.toEnum(AiPath.class, rs.getString("ai_path")),
            DbValues.toInstant(rs.getString("ai_run_at")),
            rs.getString("ai_rule_version"));

    static final RowMapper<Note> NOTE = (rs, n) -> new Note(
            rs.getLong("id"),
            rs.getString("alert_id"),
            rs.getString("body"),
            rs.getString("author"),
            DbValues.toInstant(rs.getString("created_at")));
}
