/** "safety_hazard" -> "safety hazard" */
export function humanise(value) {
  return (value || '').replace(/_/g, ' ')
}

/** Absolute local time, for detail views where precision matters. */
export function timestamp(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString([], {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

/** Relative time, for list rows where "how long has this been open" is the question. */
export function relativeTime(iso) {
  if (!iso) return ''
  const minutes = Math.round((Date.now() - new Date(iso)) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  if (minutes < 1440) return `${Math.round(minutes / 60)}h ago`
  return `${Math.round(minutes / 1440)}d ago`
}
