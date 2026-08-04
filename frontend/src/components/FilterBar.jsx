import { STATUS_FILTERS } from '../constants.js'

export default function FilterBar({ sites, filters, onFilterChange, onAnalyze, analysing }) {
  return (
    <div className="controls">
      <div className="field">
        <label htmlFor="site">Site</label>
        <select
          id="site"
          value={filters.siteId}
          onChange={(e) => onFilterChange('siteId', e.target.value)}
        >
          <option value="">All sites</option>
          {sites.map((site) => (
            <option key={site.id} value={site.id}>
              {site.name} · {site.region}
            </option>
          ))}
        </select>
      </div>

      <div className="field">
        <label htmlFor="status">Status</label>
        <select
          id="status"
          value={filters.status}
          onChange={(e) => onFilterChange('status', e.target.value)}
        >
          {STATUS_FILTERS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </div>

      {/* Analysis is explicitly triggered rather than automatic, so the operator
          can attribute any reordering to something they did. */}
      <button onClick={onAnalyze} disabled={analysing}>
        {analysing ? 'Analysing…' : 'Run AI analysis'}
      </button>
    </div>
  )
}
