// Severity is ground truth from the source system. Listed here only for display
// order in controls -- the alert list itself is ordered by the server and the
// client must never re-sort it.
export const SEVERITIES = ['critical', 'high', 'medium', 'low']

export const STATUSES = [
  'new',
  'acknowledged',
  'investigating',
  'resolved',
  'dismissed',
]

// The status dropdown. 'open' is the default and hides resolved and dismissed --
// as filter state, not as a hard exclusion, which is why 'all' and each
// individual status are also reachable.
export const STATUS_FILTERS = [
  { value: 'open', label: 'Open only' },
  { value: 'all', label: 'All statuses' },
  ...STATUSES.map((s) => ({ value: s, label: s[0].toUpperCase() + s.slice(1) })),
]

export const DEFAULT_STATUS_FILTER = 'open'

export const NOTE_MAX_LENGTH = 2000
