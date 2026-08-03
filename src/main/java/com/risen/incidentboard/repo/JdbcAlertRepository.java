package com.risen.incidentboard.repo;

import com.risen.incidentboard.domain.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ordering lives in SQL rather than in Java.
 *
 * Because the signal is persisted on the alerts row, the whole three-level sort
 * expresses as one ORDER BY, so there is exactly one implementation of the
 * ordering rules and the client renders the array it is handed. Two independent
 * sort implementations would eventually disagree, and the disagreement would
 * show up as rows that move when you navigate.
 *
 * This query is also the reason the project needs no ORM: it was always going to
 * be hand-written SQL, and an ORM would only have stood between it and the
 * three tables it reads.
 */
@Repository
public class JdbcAlertRepository implements AlertRepository {

    /**
     * Severity is the outer key and is never violated. The signal ranking is
     * nested inside a guard so it applies only to the top two tiers -- medium
     * and low collapse to a constant and fall through to occurred_at.
     *
     * COALESCE(ai_signal,'none') places unanalysed alerts at the 'none' rank, so
     * pressing Analyze rearranges an already-sensible list rather than
     * populating an empty one.
     */
    private static final String ORDER_BY = """
            ORDER BY
              CASE a.severity
                WHEN 'critical' THEN 0
                WHEN 'high'     THEN 1
                WHEN 'medium'   THEN 2
                WHEN 'low'      THEN 3
                ELSE 4 END,
              CASE WHEN a.severity IN ('critical','high') THEN
                CASE COALESCE(a.ai_signal, 'none')
                  WHEN 'safety_hazard'        THEN 0
                  WHEN 'escalation_risk'      THEN 1
                  WHEN 'site_wide_impact'     THEN 2
                  WHEN 'field_visit_required' THEN 3
                  WHEN 'none'                 THEN 4
                  WHEN 'likely_transient'     THEN 5
                  ELSE 4 END
              ELSE 0 END,
              a.occurred_at DESC,
              a.id ASC
            """;

    private static final String SELECT_ALERT =
            "SELECT " + RowMappers.ALERT_COLUMNS + " FROM alerts a JOIN sites s ON s.id = a.site_id ";

    private final JdbcClient jdbc;

    public JdbcAlertRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Alert> findOrdered(String siteId, List<AlertStatus> statuses) {
        boolean bySite = siteId != null && !siteId.isBlank();
        boolean byStatus = statuses != null && !statuses.isEmpty();

        StringBuilder sql = new StringBuilder(SELECT_ALERT).append("WHERE 1=1 ");
        if (bySite) sql.append("AND a.site_id = :siteId ");
        if (byStatus) sql.append("AND a.status IN (:statuses) ");
        sql.append(ORDER_BY);

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        if (bySite) spec = spec.param("siteId", siteId);
        if (byStatus) {
            List<String> values = new ArrayList<>();
            for (AlertStatus s : statuses) values.add(DbValues.toDb(s));
            spec = spec.param("statuses", values);
        }
        return spec.query(RowMappers.ALERT).list();
    }

    @Override
    public long countBySite(String siteId) {
        if (siteId == null || siteId.isBlank()) {
            return jdbc.sql("SELECT COUNT(*) FROM alerts").query(Long.class).single();
        }
        return jdbc.sql("SELECT COUNT(*) FROM alerts WHERE site_id = :siteId")
                .param("siteId", siteId)
                .query(Long.class)
                .single();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM alerts").query(Long.class).single();
    }

    @Override
    public Optional<Alert> findById(String id) {
        return jdbc.sql(SELECT_ALERT + "WHERE a.id = :id")
                .param("id", id)
                .query(RowMappers.ALERT)
                .optional();
    }

    @Override
    public void updateStatus(String id, AlertStatus status) {
        jdbc.sql("UPDATE alerts SET status = :status WHERE id = :id")
                .param("status", DbValues.toDb(status))
                .param("id", id)
                .update();
    }

    @Override
    public void saveClassification(String id, AiSignal signal, String action, AiPath path,
                                   Instant runAt, String ruleVersion) {
        jdbc.sql("""
                UPDATE alerts SET
                  ai_signal = :signal, ai_action = :action, ai_path = :path,
                  ai_run_at = :runAt, ai_rule_version = :ruleVersion
                WHERE id = :id
                """)
                .param("signal", DbValues.toDb(signal))
                .param("action", action)
                .param("path", DbValues.toDb(path))
                .param("runAt", DbValues.toDb(runAt))
                .param("ruleVersion", ruleVersion)
                .param("id", id)
                .update();
    }

    @Override
    public void markSkipped(String id, Instant runAt, String ruleVersion) {
        saveClassification(id, null, null, AiPath.SKIPPED, runAt, ruleVersion);
    }

    @Override
    public void insert(Alert alert) {
        jdbc.sql("""
                INSERT INTO alerts
                  (id, site_id, occurred_at, type, severity, status, description)
                VALUES
                  (:id, :siteId, :occurredAt, :type, :severity, :status, :description)
                """)
                .param("id", alert.id())
                .param("siteId", alert.site().id())
                .param("occurredAt", DbValues.toDb(alert.occurredAt()))
                .param("type", alert.type())
                .param("severity", DbValues.toDb(alert.severity()))
                .param("status", DbValues.toDb(alert.status()))
                .param("description", alert.description())
                .update();
    }
}
