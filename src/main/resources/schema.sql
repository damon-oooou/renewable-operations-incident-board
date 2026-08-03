-- Hand-written DDL rather than Hibernate-generated, so that the CHECK
-- constraints survive. IF NOT EXISTS because spring.sql.init runs on every
-- startup and seeding is idempotent (design doc 4.4).

CREATE TABLE IF NOT EXISTS sites (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    region      TEXT NOT NULL,
    technology  TEXT NOT NULL CHECK (technology IN ('solar','battery','hybrid')),
    capacity    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS alerts (
    id              TEXT PRIMARY KEY,
    site_id         TEXT NOT NULL REFERENCES sites(id),
    occurred_at     TEXT NOT NULL,          -- UTC ISO-8601, e.g. 2026-08-02T02:40:00Z
    type            TEXT NOT NULL,
    severity        TEXT NOT NULL
                    CHECK (severity IN ('critical','high','medium','low')),
    status          TEXT NOT NULL
                    CHECK (status IN ('new','acknowledged','investigating',
                                      'resolved','dismissed')),
    description     TEXT NOT NULL,

    -- AI analysis result; all five null until a run has covered this alert.
    ai_signal       TEXT CHECK (ai_signal IN ('safety_hazard','escalation_risk',
                                              'site_wide_impact','field_visit_required',
                                              'none','likely_transient')),
    ai_action       TEXT,
    ai_path         TEXT CHECK (ai_path IN ('llm','fallback','skipped')),
    ai_run_at       TEXT,                   -- UTC ISO-8601
    ai_rule_version TEXT
);

CREATE TABLE IF NOT EXISTS notes (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_id    TEXT NOT NULL REFERENCES alerts(id),
    body        TEXT NOT NULL,
    author      TEXT NOT NULL,
    created_at  TEXT NOT NULL               -- UTC ISO-8601
);

CREATE INDEX IF NOT EXISTS idx_alerts_site   ON alerts(site_id);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts(status);
CREATE INDEX IF NOT EXISTS idx_notes_alert   ON notes(alert_id, created_at);
