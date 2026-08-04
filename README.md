# Renewable Operations Incident Board

A small board for a renewable energy operations team: triage open alerts from solar
and battery sites, understand what happened, and record follow-up actions.

**Live demo:** <https://renewable-operations-incident-board-production.up.railway.app>

Java 17 · Spring Boot 4.0.7 · SQLite via JdbcClient · vanilla JS front end.

The demo runs without a persistent volume, so the board resets to the seed fixture
on every restart. Status changes and notes you make there are real, but temporary.

Design notes, including the decisions behind the ordering model and the AI layer,
are in [`docs/design-doc.md`](docs/design-doc.md).

## Run it

```bash
git clone https://github.com/damon-ooooou/renewable-operations-incident-board.git
cd renewable-operations-incident-board
```

Copy `.env.example` to `.env` and put your key in it:

```
ANTHROPIC_API_KEY=sk-ant-...

```

Then:

```bash
./mvnw spring-boot:run
```

Board: <http://localhost:8080>  ·  API docs: <http://localhost:8080/swagger-ui.html>

The database is a file, `incident-board.db`, created on first run and gitignored.

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--reset   # fresh database
./mvnw test
```

**Locally,Your changes survive a restart.** Seeding is skipped when the `alerts` table is
non-empty, so a status change or a note you add is still there next time. Use
`--reset` when you want the fixture back.


## Deploy (Railway)

Deployed at
<https://renewable-operations-incident-board-production.up.railway.app>.


## Project structure

```
src/main/java/com/risen/incidentboard/
├── IncidentBoardApplication.java   entry point; handles --reset before Spring starts
├── domain/                         records and enums, no framework annotations
│   ├── Alert · Site · Note         immutable row types
│   ├── Severity · AlertStatus      ground truth and workflow vocabularies
│   ├── AiSignal · AiPath           the closed classification set, and how it was reached
│   └── DbValues                    the only place Java and SQLite conventions meet
├── repo/                           one interface + one JdbcClient implementation each
│   ├── AlertRepository             ← the ordering query lives here
│   ├── NoteRepository              insert and read only: no update, no delete
│   ├── SiteRepository
│   └── RowMappers                  explicit column → record mapping
├── service/
│   ├── AlertService                status transitions, note validation
│   ├── AnalysisService             run scope, LLM → fallback degradation
│   └── classifier/
│       ├── AlertClassifier         the seam that makes failure testable
│       ├── LlmAlertClassifier      throws on every failure mode
│       └── KeywordAlertClassifier  never throws
├── seed/DataSeeder                 idempotent; normalises case and converts to UTC
└── web/                            controllers, DTOs, filter parsing, error mapping

frontend/src/main/resources/
├── application.yaml                datasource, AI config, .env import
├── schema.sql                      hand-written DDL with CHECK constraints
├── seed/alerts.json                8 sites, 20 alerts — uppercase, source offsets
└── static/index.html               the whole front end
```

## What it does

**Ordering.** Severity first, always: critical, high, medium, low. Within the
critical and high bands, alerts are ordered by the AI signal (§ below). Medium
and low order by time, newest first. Ties break on timestamp then id, so the list
does not shuffle between loads.

**Filters.** Site and status, combining as AND. Resolved and dismissed are hidden
on load — as default filter state, not a hard exclusion. The count of what is
hidden is shown, and "All statuses" reaches it.

**Detail.** Open an alert to change its status or add a follow-up note.

## API

Browsable at `/swagger-ui.html`. Six endpoints.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/sites` | All sites, including any with no current alerts |
| `GET` | `/api/alerts?siteId=&status=` | Filtered, ordered list plus `hiddenCount` |
| `GET` | `/api/alerts/{id}` | One alert with its full note timeline |
| `POST` | `/api/alerts/analyze` | Classify the alerts passing the current filter |
| `PATCH` | `/api/alerts/{id}/status` | Change status; appends the system note |
| `POST` | `/api/alerts/{id}/notes` | Append a note |

`status` accepts `open` (the default), `all`, or any single status.

```jsonc
// GET /api/alerts
{
  "alerts": [{
    "id": "ALT-0003",
    "siteId": "SITE-03", "siteName": "Broken Hill BESS", "region": "NSW",
    "occurredAt": "2026-08-02T23:05:00Z",
    "type": "protection_trip", "severity": "critical", "status": "new",
    "description": "DC contactor on rack B04-07 opened on overtemperature…",
    "aiSignal": "safety_hazard",
    "aiAction": "Restrict site access, dispatch crew",
    "aiPath": "llm",
    "aiRunAt": "2026-08-04T01:12:33Z",
    "aiRuleVersion": "v1"
  }],
  "hiddenCount": 9
}
```

```jsonc
// POST /api/alerts/analyze   — body is the filter, not a list of ids
{ "siteId": "SITE-03", "status": "open" }

// 200, even when every call fell back
{ "analyzed": 4, "llm": 0, "fallback": 3, "skipped": 1, "apiKeyConfigured": false }
```

Four decisions worth flagging to anyone reading the API.

**Ordering is server-side.** The list arrives in display order and the client
renders it as given. Two independent sort implementations would eventually
disagree, and the disagreement would show up as rows that move when you navigate.

**Analyze takes the filter, not alert ids.** The server resolves "what is on
screen" with the same query that built the screen. Passing ids would let the
client's idea of the current filter drift from the server's.

**Analyze returns 200 even when the model never answered.** The keyword fallback
is a designed path, not an error, so degradation is reported in the payload via
`aiPath` and `apiKeyConfigured` rather than through the status code. A caller
that only checks the status code still gets a usable result.

**There is no endpoint to edit or delete a note.** `PATCH` and `DELETE` on a note
return 405, because the method does not exist rather than because a guard rejects
it.

Errors are `{"error": "…"}` with `400` for validation and `404` for an unknown
alert. An unknown id is 404 even when the body is also invalid — a bad id is not
reported as a bad note.


## Severity and priority are different things

This distinction drives the whole design.

*Severity* is ground truth from the source system. It is not editable here and the
model does not get a vote. *Priority* is a judgment: within one severity tier,
which alert to open first, and the only thing that separates them is the free-text
description.

The AI works only in that second space. It reorders **within** a tier, never
across one. So the top of the list is always a critical alert, whether or not you
trust the model.

## AI analysis

Press **Run AI analysis**. It is not automatic — an explicit trigger makes the
model's contribution legible (you see the list before and after) and means the
board is fully usable for someone who never presses it.

Per alert, the model returns one signal from a closed set plus a short action:

```json
{"signal": "safety_hazard", "suggested_action": "Restrict site access, dispatch crew"}
```

| Rank | Signal | |
|---|---|---|
| 0 | `safety_hazard` | Risk to people or equipment integrity |
| 1 | `escalation_risk` | Degrading; will worsen if left |
| 2 | `site_wide_impact` | Whole site rather than one asset |
| 3 | `field_visit_required` | Needs a crew on site |
| 4 | `none` | Nothing notable in the description |
| 5 | `likely_transient` | Contained or self-clearing |

Two things are deliberate here. **`none` outranks `likely_transient`**, so a
description that positively says the issue has passed is demoted below one the
model had nothing to say about — absence of a signal is not evidence of
harmlessness. And **the model returns a bucket, not a score or a ranking**:
ordering is then derived in SQL. That is what makes the feature testable — a test
asserts a description maps to an expected bucket, which is a stable claim, where
asserting a list position is not.

**Scope.** Critical and high only, and only alerts passing the current filter.
Reordering the low-severity tail does not change what anyone does next, so
spending inference on it buys nothing.

**Fallback.** Any LLM failure — timeout, non-200, malformed JSON, out-of-set
signal, missing key — degrades to keyword matching against the same closed set,
evaluated in priority order, first match wins. The fallback cannot raise: it is
string matching over a static table with no I/O, and is additionally wrapped so
an unforeseen error yields `none`. An analysis run cannot fail the request.

**`ai_path` is shown, not just stored.** A run that silently degraded to keyword
matching while still producing plausible-looking actions is exactly the failure an
operator must be able to see.

| `ai_path` | |
|---|---|
| `llm` | Model returned a valid signal |
| `fallback` | Model failed; keyword matching used |
| `skipped` | Medium or low — in scope, deliberately not classified |
| `null` | No run has reached this alert yet |

`skipped` versus `null` is the distinction between a decision and an absence.
Collapsing them would make it impossible to tell whether a medium-severity alert
was excluded by design or simply never analysed.

The model never changes severity and never changes status. Suggested actions are
display-only and never enter the note history.

## Notes and status

Notes are **append-only**. No edit, no delete, no endpoint for either. Correction
is by appending: a mistaken note is followed by a corrective one and both remain.
This record may end up supporting a compliance response or a warranty claim, and
one that can be quietly rewritten is worth less in that context.

**Every status change writes a system-authored note:**

```
Status changed: acknowledged -> investigating
```

That is what lets the schema carry no status history table. The transitions sit in
the same timeline as the operator's notes, interleaved chronologically, and are
written in the same transaction as the status update so the two cannot diverge.

In the timeline, system notes stay in monospace and operator notes switch to a
sans face — the two voices are distinguishable before you read a word.

Handled in code:

- Empty or whitespace-only note → `400`
- Over 2000 characters → `400`
- Backward transitions allowed (`resolved -> investigating`)
- Notes can be added in any status, including closed
- No-op transitions rejected — otherwise the append-only log fills with
  `investigating -> investigating` entries that can never be cleaned up

## Data

Three tables: `sites` → `alerts` → `notes`, both one-to-many. `notes` starts empty.

**No ORM.** SQLite has no dialect in Hibernate core, so JPA here means depending on
`hibernate-community-dialects` and hoping it keeps pace with each Hibernate
release. The design never needed one anyway: three tables, no object graph, and
the ordering query below was always going to be hand-written SQL. Persistence is
`JdbcClient` with records as row types and explicit row mappers. The layering is
unchanged — each repository is an interface with one implementation — but rows are
immutable and every write is an explicit statement, so nothing persists at a flush
boundary that the code did not ask for.

The AI result lives on the `alerts` row (`ai_signal`, `ai_action`, `ai_path`,
`ai_run_at`, `ai_rule_version`) rather than in its own table — the relationship is
strictly one-to-one and the result has no independent lifecycle, so a separate
table would add a join to the hottest query in exchange for nothing.

Persisting it also means the whole three-level sort is a single `ORDER BY`, so
ordering has exactly one implementation and the client renders the array it is
handed.

Two SQLite details worth flagging:

- **Foreign keys are off by default.** `foreign_keys=on` travels in the JDBC URL,
  so it holds for every pooled connection rather than only the first.
- **Boot 4 ships Jackson 3**, at `tools.jackson` rather than `com.fasterxml.jackson`,
  with an immutable `JsonMapper` in place of `ObjectMapper`. Only the annotations
  kept their old package.
- **Timestamps are stored as UTC**, fixed-width to the second. SQLite compares
  `TEXT` lexicographically, so mixed offsets would sort wrongly — the fixture
  spans `+10:00` and `+09:30`. (The current data does not actually trigger it;
  the point is that it is one added alert away, and it would surface as a quietly
  misordered list rather than an error.)

`ai_rule_version` records which version of the signal set, keyword table and
prompt produced a stored result, so that after a classifier change you can tell
which rows are stale rather than holding a silent mixture.

## Tests

```bash
./mvnw test                                    # everything
./mvnw test -Dtest=OrderingIntegrationTest     # one class
```

Five classes, split by what they can prove without a database and what they can't.

| Class | Covers |
|---|---|
| `KeywordAlertClassifierTest` | Priority order (first match wins), each signal, and that degenerate input never raises |
| `AnalysisServiceTest` | LLM failure → fallback, medium/low recorded as `skipped` with a run stamp, run completes despite failure |
| `AlertServiceTest` | Exactly one system note per status change, backward transitions allowed, no-op rejected with nothing written, note validation at the 2000-character boundary |
| `StatusFilterTest` | Default hides closed alerts; `all` and naming a status directly both reach them |
| `OrderingIntegrationTest` | Real SQLite, real fixture: severity bands never invert, each band newest-first, seeding is idempotent |

Two deliberate choices here.

**Failure modes are injected, not simulated.** `AlertClassifier` is an interface,
so timeout, malformed JSON, an out-of-set signal and a missing API key are all
just stubs. No network, no key, no flakiness in CI.

**Ordering is tested against real SQLite, not mocked.** The ordering is a SQL
`CASE` expression, so a mocked repository would test nothing. `OrderingIntegrationTest`
writes to a throwaway database under `target/` and leaves your working data alone.

One trap worth knowing, because I fell into it: **medium and low are separate
bands.** The oldest medium legitimately precedes the newest low, so asserting
that the two form one time-ordered sequence fails against correct output. The
test checks each band on its own.


## Possible Improvements

- **Authentication and real authorship.** Notes are currently attributed to a
  fixed placeholder. With auth in place each note would carry its actual author,
  which is what makes a timeline useful for a team rather than an individual.
- **Two-way sync with the source system.** Severity and description are treated
  as read-only here because the source system owns them. A production version
  would consume updates rather than snapshot them once.
- **Pagination and stored ordering.** Ordering is computed per request, which is
  fine at this dataset size. At scale the priority ordering would be persisted
  and paginated, so a scroll position stays stable across reloads.
- **Split the list into four separate queues, and classify every alert.**
Each severity would become its own visually separated queue — critical, then high, then medium, then low — with the AI signal reordering alerts inside the critical and high queues only. Medium and low would keep same order but still receive a signal and suggested action.
