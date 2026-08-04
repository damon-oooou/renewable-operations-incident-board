import { useCallback, useEffect, useState } from 'react'
import { listAlerts, listSites } from '../api/client.js'
import { DEFAULT_STATUS_FILTER } from '../constants.js'

/**
 * Owns the list and the filters that produce it.
 *
 * Filters live here rather than in FilterBar because two other things need
 * them: the reload after a mutation, and the analysis run, whose scope is
 * defined as "the alerts passing the current filter".
 */
export function useAlerts() {
  const [sites, setSites] = useState([])
  const [alerts, setAlerts] = useState([])
  const [hiddenCount, setHiddenCount] = useState(0)
  const [filters, setFilters] = useState({ siteId: '', status: DEFAULT_STATUS_FILTER })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  // Sites are fetched once and include sites with no current alerts -- an
  // operator confirming a site is quiet needs it to still be selectable.
  useEffect(() => {
    listSites()
      .then(setSites)
      .catch((e) => setError(e.message))
  }, [])

  const reload = useCallback(async () => {
    setLoading(true)
    try {
      const data = await listAlerts(filters)
      setAlerts(data.alerts)
      setHiddenCount(data.hiddenCount)
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [filters])

  useEffect(() => {
    reload()
  }, [reload])

  const setFilter = useCallback((key, value) => {
    setFilters((current) => ({ ...current, [key]: value }))
  }, [])

  const resetFilters = useCallback(() => {
    setFilters({ siteId: '', status: DEFAULT_STATUS_FILTER })
  }, [])

  return {
    sites,
    alerts,
    hiddenCount,
    filters,
    loading,
    error,
    reload,
    setFilter,
    resetFilters,
  }
}
