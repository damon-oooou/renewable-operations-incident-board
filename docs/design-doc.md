# Renewable Operations Incident Board — Design Document

**Author:** Damon Ou
**Date:** 3 August 2026
**Status:** Draft for review

---

## 1. Problem

An operations team monitoring solar and battery sites receives a continuous stream of alerts from SCADA, BMS and market systems. On a normal day the volume is manageable; on a bad day it is not, and the alerts that matter are buried among curtailment notices and sensor niggles.

Severity alone is a blunt instrument. It is assigned by the source system from a fixed rule set, so a routine `critical` comms dropout and a `critical` thermal runaway precursor sort identically. The information that separates them lives in the free-text description, which is exactly the part no rules engine reads.

This board gives the team three things: a triaged list of what is open, enough context to understand what happened, and a durable record of what was done about it.

## 2. Severity and priority are different things

This distinction is the core of the design and everything else follows from it.

**Severity is ground truth.** It arrives from the source system, it is not editable in this application, and the model does not get a vote. An alert never moves between severity tiers as a result of AI analysis.

**Priority is a judgment.** Within a single severity tier, some alerts warrant attention before others, and what separates them is the free text. A `high` describing a cell voltage spread that is actively widening under rising temperature is more urgent than a `high` describing a network event the plant already rode through. Both are correctly classified as `high`. Only the description tells you which to open first.

The AI operates exclusively in that second space. It reorders *within* a tier and never across tiers. An operator can therefore trust that the top of the list is a critical alert without having to trust the model at all.

## 3. Goals and non-goals

**Goals**

- Present open alerts ordered by severity, with AI-assisted priority ordering inside each of the top two tiers.
- Let an operator change status and record follow-up notes against an alert.
- Preserve a complete, append-only record of what was done and when.
- Degrade to a fully usable application when the LLM is unavailable.

**Non-goals for this iteration**

- Authentication, user accounts, roles. Single user, no login.
- Real-time push, notifications, escalation timers, SLA tracking.
- Editing any alert field other than status.
- Multi-tenancy, pagination, alert grouping.

## 4. Stack, assumptions and data model

### 4.1 Stack

- **Backend:** Java 17, Spring Boot 4.0.7 (Spring MVC, Spring JDBC)
- **Database:** SQLite via `sqlite-jdbc`, accessed with `JdbcClient`
- **API documentation:** springdoc-openapi 3.x (the Boot 4 line), served at `/swagger-ui.html`
- **AI:** a single `AlertClassifier` interface with two implementations — `LlmAlertClassifier` and `KeywordAlertClassifier`

SQLite is chosen because the reviewer should be able to clone and run with no database to install and no container to start.

**There is no ORM, and that is a decision rather than an omission.** SQLite has no dialect in Hibernate core; JPA on SQLite depends on `hibernate-community-dialects`, an out-of-tree artifact whose currency against each Hibernate release is a risk this project has no reason to carry. More to the point, the design never asked for an ORM. There are three tables and no object graph. The ordering in §5.1 was always going to be hand-written SQL, because a `CASE` ranking over two columns is not something a criteria API expresses well. An ORM would have sat between that query and the three tables it reads, and earned nothing.

So persistence is `JdbcClient` with plain records as row types and explicit `RowMapper`s. The layering is unchanged — each repository is still an interface with one implementation, so the service layer neither knows nor cares. What changes is that rows are immutable and every write is an explicit statement: there is no dirty checking, so nothing persists at a flush boundary that the code did not ask for.

Three SQLite and Boot 4 specifics that matter in practice and are easy to get wrong:

- **Foreign keys are not enforced by default.** `PRAGMA foreign_keys = ON` must be set per connection; it is applied via the JDBC URL so that it holds for every pooled connection rather than only the first.
- **There is no native enum, boolean or timestamp type.** Enums are stored as `TEXT` with `CHECK` constraints; timestamps as `TEXT` in ISO-8601 UTC. With no ORM there are no `AttributeConverter`s, so this translation lives in one class, `DbValues`, and nowhere else.
- **Boot 4 ships Jackson 3**, which moved from `com.fasterxml.jackson` to `tools.jackson` and replaced the mutable `ObjectMapper` with an immutable `JsonMapper`. Only the annotations stayed put. This affects the seed loader and the model client.

**Timestamps are stored as UTC, and this is a correctness requirement rather than a convention.** SQLite compares `TEXT` lexicographically, so if timestamps retained their source offsets the sort would be wrong wherever offsets differ:

```
2026-08-02T12:10:00+09:30   =  02:40 UTC
2026-08-02T12:20:00+10:00   =  02:20 UTC
```

Lexicographically `12:10` precedes `12:20`, so the South Australian alert would sort first despite having happened twenty minutes later.

The current seed data does not in fact trigger this — checked, and the naive and correct orderings coincide for every band. The point is that it spans `+10:00` and `+09:30`, so the bug is one added alert away, and it would surface as a quietly misordered list rather than as an error. Normalising to UTC on the way in removes the class of bug rather than the instance of it, and keeps `ORDER BY occurred_at` correct with no conversion at query time. Values are also truncated to whole seconds so that every stored string has the same width — variable-width timestamps break a lexicographic sort just as reliably as mixed offsets do. Offsets are applied only at render.

### 4.2 Assumptions

- Alerts are seeded from a fixture representing upstream ingestion. Within the application they are read-only apart from status and the AI result columns.
- The dataset is small — tens of open alerts. Ordering is computed per request in SQL; no materialised views, no pagination.
- Single user, so no concurrent-edit handling. `author` is a fixed placeholder for operator-written notes.
- Descriptions are untrusted third-party text. They are rendered as plain text and treated strictly as data when passed to the model.

### 4.3 Schema

Three tables. `sites` has many `alerts`; `alerts` has many `notes`. Both relationships are one-to-many, so the foreign key sits on the many side in each case. There are no other relationships — in particular there is no status history table (see §7.2) and no separate AI results table (see §6.1).

```sql
PRAGMA foreign_keys = ON;

CREATE TABLE sites (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    region      TEXT NOT NULL,
    technology  TEXT NOT NULL CHECK (technology IN ('solar','battery','hybrid')),
    capacity    TEXT NOT NULL
);

CREATE TABLE alerts (
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

    -- AI analysis result; all five null until a run has covered this alert
    ai_signal       TEXT CHECK (ai_signal IN ('safety_hazard','escalation_risk',
                                              'site_wide_impact','field_visit_required',
                                              'none','likely_transient')),
    ai_action       TEXT,
    ai_path         TEXT CHECK (ai_path IN ('llm','fallback','skipped')),
    ai_run_at       TEXT,                   -- UTC ISO-8601
    ai_rule_version TEXT
);

CREATE TABLE notes (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_id    TEXT NOT NULL REFERENCES alerts(id),
    body        TEXT NOT NULL,
    author      TEXT NOT NULL,
    created_at  TEXT NOT NULL               -- UTC ISO-8601
);

CREATE INDEX idx_alerts_site   ON alerts(site_id);
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_notes_alert   ON notes(alert_id, created_at);
```

Notes on the choices:

- **`sites.id` and `alerts.id` are `TEXT`, `notes.id` is an autoincrementing integer.** Sites and alerts arrive from outside the system carrying their own identifiers, so those are natural keys and preserving them keeps the board's ids matching whatever an operator sees in SCADA. Notes are created by the application and have no external identity, so a surrogate key is correct. The tables genuinely differ in origin, and the key strategy reflects that rather than being applied uniformly out of habit.
- **`occurred_at` rather than `created_at`** on alerts, because the meaningful time is when the condition occurred at the site, not when the row was written.   
- **`CHECK` constraints on every enum column.** With no ORM validating on the way in, these carry more weight than they would under JPA: they are what guarantees a lowercase vocabulary in the file, and they still hold if someone edits it directly with the `sqlite3` CLI — which, for a take-home a reviewer will poke at, is a realistic scenario.
- **`notes` starts empty.** Only `sites` and `alerts` are seeded. Every row in `notes` is the product of an operator action or a status change within the running application.

### 4.4 Seeding

`sites` and `alerts` are loaded from a JSON fixture on startup. `notes` is never seeded.

**Seeding is idempotent.** On startup the seeder counts rows in `alerts`; if the table is non-empty it does nothing and logs that it skipped. A reviewer who changes a status, writes a note and restarts the application finds their work intact. A seeder that ran unconditionally would silently discard exactly the evidence that the write path works, which is the thing the reviewer is trying to check.

`alerts` is the right table to test rather than `sites`, because `sites` is populated first — testing it would leave a window where a crash between the two inserts produces a database that looks seeded but has no alerts. The whole seed runs in a single transaction for the same reason: a partial seed is not a state the guard can reason about, so it must not be reachable.

**Case is normalised at the seam.** The fixture uses uppercase for the closed vocabularies (`CRITICAL`, `NEW`, `INVERTER_FAULT`, `SOLAR`); the database stores lowercase, matching the `CHECK` constraints. Conversion happens once, in the seeder. Normalising at the boundary means no query, comparison or enum binding anywhere downstream needs to be case-insensitive — there is exactly one place where the two conventions meet, rather than a case-insensitive comparison scattered through the service layer.

Normalisation applies **only to the closed-vocabulary columns**: `severity`, `status`, `type`, `technology`. It does not apply to:

- **Identifiers** (`sites.id`, `alerts.site_id`, `alerts.id`). These come from an external system and lowercasing them changes them. `SITE-01` is the identifier; `site-01` is a different string that happens to look similar, and preserving the original keeps the board's ids matching what an operator sees in SCADA.
- **Free text** (`name`, `region`, `capacity`, `description`). Lowercasing `NSW` to `nsw` or `85 MW` to `85 mw` corrupts data that is displayed verbatim.

Timestamps are converted from their source offset to UTC in the same pass (§4.1).

**A `--reset` flag drops and recreates the database.** Run as `java -jar app.jar --reset`, it deletes the SQLite file before schema creation, so the next startup finds an empty `alerts` table and seeds normally. This gives a clean demo without asking anyone to hunt down and delete a file by hand, and it keeps the idempotency guard simple: there is one way to get a fresh database, and it is explicit. The flag is read from `ApplicationArguments` and acts before the schema initialiser, so ordering is deterministic rather than dependent on bean creation order.

## 5. The alert list

### 5.1 Ordering

Ordering is a three-level key. Severity is the outer key and is never violated.

```
1. Apply filters (site AND status).
2. Partition by severity rank: critical=0, high=1, medium=2, low=3.
3. Within critical, and separately within high:
     sort by signal priority rank ASC
     then occurred_at DESC
     then id ASC
4. Within medium and low:
     sort by occurred_at DESC
     then id ASC
5. Concatenate bands in severity order.
```

**Signal priority rank**, applied only inside the critical and high bands:

| Rank | Signal | Meaning |
| --- | --- | --- |
| 0 | `safety_hazard` | Risk to people or equipment integrity |
| 1 | `escalation_risk` | Condition is degrading and will worsen if left |
| 2 | `site_wide_impact` | Affects the whole site rather than one asset |
| 3 | `field_visit_required` | Needs a crew on site to clear |
| 4 | `none` | Nothing notable in the description |
| 5 | `likely_transient` | Already contained, or self-clearing |

Two properties of this scale are deliberate. `none` sits above `likely_transient`, so a description that positively indicates the issue has passed is actively demoted below one the model had nothing to say about — absence of a signal is not the same as evidence of harmlessness. And alerts with `ai_signal IS NULL` sort as `none`, so pressing **AI analyze** rearranges the list rather than populating an empty one.

Ordering is a closed enum comparison rather than a numeric score, which makes it fully deterministic: the same alerts with the same signals always produce the same order, and ties break on `occurred_at` then `id`. Because signals are persisted (§6.1), the whole ordering expresses as a single `ORDER BY` with a `CASE` mapping over `severity` and `ai_signal`, and is computed in the database rather than in application code.

### 5.2 Filters

| Filter | Control | Default |
| --- | --- | --- |
| Site | Single-select dropdown, all known sites | All sites |
| Status | Single-select dropdown | Open only (excludes `resolved`, `dismissed`) |

The two combine as **AND**.

The site dropdown is populated from `sites`, not from the sites present in the current alert set, so a site with no alerts still appears. A site disappearing from the filter because it currently has nothing wrong is worse than an empty result: it removes the operator's ability to confirm that a site is quiet, which is a thing they check.

Deselecting all statuses shows everything, including resolved and dismissed. This is the escape hatch from the default.

`resolved` and `dismissed` are hidden on first load, but as **default filter state, not a hard exclusion**. The consequences:

- The status control shows its current value, so it is visible that a filter is active rather than the list simply being short.
- A count of suppressed alerts appears beside it (`14 closed alerts hidden`).
- Selecting `resolved` explicitly overrides the default and shows them.

The status vocabulary is fixed: `new`, `acknowledged`, `investigating`, `resolved`, `dismissed`.

### 5.3 Row content

Severity, site, type, timestamp, status, truncated description. Analysed alerts additionally show the signal as a labelled chip and the suggested action beneath. Severity is encoded by colour *and* text label — colour alone fails for colour-blind users and in printouts, and control rooms print things.

## 6. AI analysis

### 6.1 Trigger and persistence

Analysis runs only when the operator presses **AI analyze**. It is not automatic on page load and not on a timer.

**Results are persisted.** A run writes `ai_signal`, `ai_action`, `ai_path`, `ai_run_at` and `ai_rule_version` onto the `alerts` row itself. Three things follow from that:

- **Ordering is stable across page loads.** An operator who analyses the list, opens an alert and navigates back finds the same order. Recomputing on each load with a nondeterministic model would reshuffle the list under them, which in a triage tool is worse than no ordering at all.
- **Ordering is computable server-side.** Because the signal lives in a column, the entire sort is a SQL `ORDER BY` (§5.1). The client renders the array it is given and holds no sort logic.
- **Cost is bounded by operator action, not by traffic.** An alert is analysed when someone asks for it to be, and not again until someone asks again.

Re-running **AI analyze** overwrites the previous result for every alert in scope. There is no result history — the current classification is the one that matters, and keeping superseded classifications would invite the question of which one the ordering used.

The results live on the `alerts` row rather than in a separate table because the relationship is strictly one-to-one and the result has no independent lifecycle: it is created, overwritten and read only ever in the context of its alert. A separate table would add a join to the hottest query in the application in exchange for nothing.

`ai_rule_version` records which version of the classification rules produced a stored result — the signal set, the keyword table, and the prompt. Without it, a result written under an old rule set is indistinguishable from a current one, and after any change to the classifier the database silently holds a mixture of both. With it, stale rows can be identified and re-analysed, and the UI can mark them.

The button reports progress (`Analyzing 6 alerts…`) and is disabled while a run is in flight.

### 6.2 Scope

A run covers alerts **currently passing the active filters**. Within that set:

- `critical` and `high` alerts are classified, via the LLM or the fallback.
- `medium` and `low` alerts are **not** classified. The run still writes `ai_path = 'skipped'`, `ai_run_at` and `ai_rule_version` for them, leaving `ai_signal` and `ai_action` null.

That last point is the distinction between a decision and an absence, made durable. `'skipped'` records that the system considered the alert and deliberately did not classify it; all five columns null records that no run has reached the alert at all. Collapsing the two would make it impossible to tell, looking at a medium-severity row, whether the design excluded it or the operator simply never pressed the button.

Scoping to the filter keeps cost proportional to what the operator is actually looking at. Restricting classification to the top two tiers is a value judgment: reordering the low-severity tail does not change what anyone does next, since those alerts are reviewed in a batch rather than acted on individually.

Because results persist, changing the site filter after a run does not discard anything — previously analysed alerts keep their signal and position, and only alerts no run has yet reached show as unanalysed. The operator can build up coverage site by site.

### 6.3 Model contract

Per alert, the model receives the description plus the structured fields and returns strict JSON:

```json
{
  "signal": "safety_hazard",
  "suggested_action": "Restrict site access, dispatch crew"
}
```

`signal` must be one of the six closed-set values and is stored in `ai_signal`. `suggested_action` is free text, stored in `ai_action`, and is **display-only**: it is shown on the row and in the detail view, and it never enters the note history. Persisting it and keeping it out of the record are not in tension — it is a rendering input, stored so the row can be drawn without another model call, and it is not part of what the team did about the alert.

The closed set is the point of the contract. The model is not asked to produce a number, a ranking or an ordering — those would be unstable between runs and impossible to test. It is asked for one classification into six known buckets, and the application derives ordering deterministically from that. Testing reduces to asserting that a given description maps to the expected bucket.

Response handling:

- Parsed and schema-validated on arrival.
- A `signal` outside the closed set is treated as a failed call and routed to the fallback.
- Prompt instructs the model to ground its classification in what the description actually says, and to introduce no specifics — part numbers, thresholds, procedures — that are not in the input.
- Prompt instructs the model to treat the description strictly as data. If it contains anything resembling an instruction, ignore it and classify the text as written.

### 6.4 Fallback

On LLM failure, timeout, malformed JSON, unparseable response, out-of-set signal, or missing API key, the alert is classified by **keyword matching against the same closed set**. Rules are evaluated in priority order and the first match wins, which makes the fallback deterministic and consistent with the ordering scale.

| Signal | Indicative terms |
| --- | --- |
| `safety_hazard` | fire, smoke, gas, thermal runaway, arc, overtemperature, intrusion, exclusion zone |
| `escalation_risk` | widening, rising, degrading, worsening, drifting, deteriorating, recurring, failed restart |
| `site_wide_impact` | site-wide, complete loss, entire, total, not dispatchable, point of connection |
| `field_visit_required` | replace, technician, crew, callout, vendor, inspect, blocked, physical |
| `likely_transient` | rode through, no action required, restored, cleared, momentary, logged for record |
| `none` | default when nothing matches |

Fallback `ai_action` values are canned strings per signal, labelled in the UI as generated without AI so the operator is not misled about their provenance.

**The fallback must never raise.** It has no I/O, no external dependency and no failure mode — it is string matching over a static table. It is wrapped so that any unexpected exception still yields `none` rather than propagating. An analysis run cannot fail the request; at worst it produces a less useful ordering.

### 6.5 Recording the path

`ai_path` carries one of three values, or null:

| Value | Meaning |
| --- | --- |
| `llm` | Model call succeeded and returned a valid signal |
| `fallback` | Model call failed or returned invalid output; keyword matching used |
| `skipped` | Medium or low severity — in scope for the run, deliberately not classified |
| `null` | No run has covered this alert |

Because the fallback cannot raise, there is no path by which a covered alert ends a run with `ai_path` null. Null means only that **AI analyze** has not yet reached the alert, which is the state every alert is in on a fresh database.

The path is surfaced in the UI, not just stored. A run that silently degraded to keyword matching because an API key was missing, while still producing plausible-looking suggested actions, is exactly the failure an operator must be able to see. A banner reports it (`6 alerts analyzed — 2 used keyword fallback`), and affected rows are marked.

### 6.6 Presenting it honestly

- Suggested actions are visually distinct from operator notes and never enter the note history.
- The signal chip is always shown alongside the suggested action, so the operator can see the basis for the row's position.
- `ai_run_at` is displayed in the detail view, so it is clear how current the classification is.
- The model never changes status and never changes severity. Both are exclusively human decisions.

## 7. Alert detail, status and notes

### 7.1 Notes

Append-only. No edit, no delete, no endpoint for either.

- Empty or whitespace-only body → `400`.
- Body over **2000 characters** → `400`.
- Displayed in the detail view ordered by `created_at`.
- Notes can be added to an alert in **any** status, including `resolved` and `dismissed`. Closing an alert does not close the conversation about it; late-arriving information is common and has to land somewhere.

Correction is by appending. A mistaken note is followed by a corrective one and both remain visible. The team's operational record may end up supporting a market compliance response or a warranty claim, and a history that can be quietly rewritten is worth considerably less in that context.

### 7.2 Status

Only the current status is stored, in `alerts.status`. There is no status history table. Status is the single editable field on an alert.

**Every status change writes an automatic note** with author `system`:

```
Status changed: acknowledged -> investigating
```

This is the piece that makes the absence of a history table safe. The transition record lives in the append-only `notes` stream, interleaved chronologically with the operator's own notes, so the detail view reads as one continuous account of what happened rather than a status field beside an unrelated comment thread. History is reconstructable from `notes` without a second table to keep in sync.

The status update and its system note are written in one transaction, so the two cannot diverge.

**Backward transitions are allowed.** `resolved -> investigating` is valid. Closing something in error is common, and the alternative — a duplicate alert with no link to the original — is worse. Any transition between any two statuses is permitted; the system note records what happened either way. A no-op transition (setting a status to its current value) is rejected with `400` rather than writing a note that says nothing.

### 7.3 Double submission

The submit button is disabled while the request is in flight. This is sufficient for a single-user application and is the honest scope of the protection: it prevents the impatient double-click, which is the realistic failure. It does not defend against a retried request at the network layer, which in an append-only log would produce a permanent duplicate. Documented as a known limitation rather than papered over.

### 7.4 Detail view

Immutable alert fields, current status with its control, the AI signal and suggested action if the alert has been analysed, and the note timeline. The composer states before submission — not after — that notes cannot be edited or deleted.

## 8. API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/sites` | All sites, for the filter dropdown. |
| `GET` | `/api/alerts?siteId=&status=` | Filtered, ordered list. Returns `hiddenCount`. |
| `GET` | `/api/alerts/{id}` | Alert with note history. |
| `POST` | `/api/alerts/analyze` | Body `{ siteId?, status? }` — the current filter. Classifies in-scope alerts, persists results, returns per-alert `{ signal, action, path }` and a run summary. |
| `PATCH` | `/api/alerts/{id}/status` | Body `{ status }`. Updates status and appends the system note in one transaction. |
| `POST` | `/api/alerts/{id}/notes` | Body `{ body }`. Appends a note. `400` on empty or over-length. |

`POST /api/alerts/analyze` takes the filter rather than a list of ids, so the server decides scope from the same criteria it uses to build the list. Passing ids would let the client's idea of the current filter drift from the server's.

It returns `200` even when every call fell back, because from the caller's perspective nothing failed — the fallback is a designed path, not an error. Degradation is reported in the payload via `ai_path`, not via the status code.

All endpoints are documented with springdoc-openapi and browsable at `/swagger-ui.html`.

## 9. Testing

The parts worth testing are the ones a reviewer cannot verify by clicking around.

- **Ordering.** Table-driven over a fixed dataset: severity bands never invert, signal priority orders correctly within a band, `none` sorts above `likely_transient`, null signals sort as `none`, medium and low ignore signals entirely, ties break deterministically.
- **Fallback.** `AlertClassifier` is an interface. Inject timeout, malformed JSON, out-of-set signal and absent API key; assert each yields `ai_path = 'fallback'`, a valid signal, and no exception. The repositories are interfaces too, so these run against mocks with no database.
- **Path recording.** `skipped` on in-scope medium and low, null before any run, `llm` on success. Assert a skipped alert has `ai_run_at` set but `ai_signal` null.
- **Persistence and overwrite.** A second run overwrites the first result for in-scope alerts and leaves out-of-scope alerts untouched.
- **Note validation.** Empty, whitespace-only and over-length all return `400`.
- **System notes.** Every status change produces exactly one system-authored note with the correct transition text, and a failed status update produces none.
- **Filter defaults.** Fresh load excludes closed alerts, reports the correct hidden count, and reaches them by changing the filter.
- **Seed idempotency.** Seed, mutate a status, add a note, seed again; assert the mutation and the note survive and no duplicate alerts exist. This is the test that protects the reviewer's own session.
- **Normalisation.** Uppercase fixture values land lowercase in the enum columns; identifiers and free text keep their original case; mixed-offset timestamps land as UTC and sort in true chronological order.

## 10. Open questions

1. **Should related alerts be grouped?** The seed data contains a causal chain at one site — HVAC failure, widening cell imbalance, rack contactor trip, across 23 minutes. Presenting those as three peers is misleading. Out of scope here, but it is the most valuable next feature.
2. **Stale rule versions.** `ai_rule_version` makes stale results detectable but nothing currently acts on it. The obvious next step is a badge on rows classified under a superseded version, and a re-analyse prompt.
3. **How is classification quality measured?** Currently it is not. The cheapest instrument is logging the position an alert held when the operator opened it: if operators consistently skip the top-ranked alert, the ranking is wrong and the logs will say so.
4. **Timezone display.** Storage is settled — UTC (§4.1). Display is not. Sites span `+10:00` and `+09:30`, and an operator in one region reading an alert from another has to do the arithmetic either way. Currently viewer-local; site-local, or showing both, are defensible alternatives that need an operator to arbitrate.
