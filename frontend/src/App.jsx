import { useCallback, useState } from 'react'
import { analyze } from './api/client.js'
import { useAlerts } from './hooks/useAlerts.js'
import { DEFAULT_STATUS_FILTER } from './constants.js'
import FilterBar from './components/FilterBar.jsx'
import AnalysisBanner from './components/AnalysisBanner.jsx'
import AlertList from './components/AlertList.jsx'
import AlertDetail from './components/AlertDetail.jsx'

export default function App() {
  const {
    sites, alerts, hiddenCount, filters, loading, error,
    reload, setFilter, resetFilters,
  } = useAlerts()

  const [openId, setOpenId] = useState(null)
  const [analysing, setAnalysing] = useState(false)
  const [summary, setSummary] = useState(null)
  const [analysisError, setAnalysisError] = useState(null)

  const runAnalysis = useCallback(async () => {
    setAnalysing(true)
    setAnalysisError(null)
    try {
      // Scope is the filter, so the request carries it rather than a list of
      // ids the client happens to be holding.
      const result = await analyze(filters)
      setSummary(result)
      await reload()
    } catch (e) {
      setAnalysisError(e.message)
    } finally {
      setAnalysing(false)
    }
  }, [filters, reload])

  // "Closed" is only accurate under the default filter, where everything
  // withheld genuinely is resolved or dismissed. Filter to New and the withheld
  // set includes open alerts in other statuses, so the wording changes with it.
  const hiddenLabel =
    filters.status === DEFAULT_STATUS_FILTER
      ? `${hiddenCount} closed alerts hidden`
      : `${hiddenCount} alerts hidden by this filter`

  return (
    <>
      <header>
        <div className="masthead">
          <h1>Incident Board</h1>
        </div>
        <FilterBar
          sites={sites}
          filters={filters}
          onFilterChange={setFilter}
          onAnalyze={runAnalysis}
          analysing={analysing}
        />
        <AnalysisBanner summary={summary} error={analysisError} />
      </header>

      <main>
        <div className="meta">
          <span>
            {loading ? 'Loading…' : `${alerts.length} ${alerts.length === 1 ? 'alert' : 'alerts'}`}
          </span>
          <span>{hiddenCount > 0 && hiddenLabel}</span>
        </div>

        {error ? (
          <div className="empty">
            <p>Could not load the board: {error}</p>
          </div>
        ) : (
          <AlertList alerts={alerts} onOpen={setOpenId} onReset={resetFilters} />
        )}
      </main>

      {openId && (
        <AlertDetail alertId={openId} onClose={() => setOpenId(null)} onMutated={reload} />
      )}
    </>
  )
}
