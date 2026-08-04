import { humanise, relativeTime } from '../lib/format.js'

function AiSummary({ alert }) {
  if (alert.aiSignal) {
    return (
      <div className="ai">
        <span className="signal">{humanise(alert.aiSignal)}</span>
        <span className="action sans">{alert.aiAction}</span>
        {alert.aiPath === 'fallback' && <span className="tag">keyword fallback</span>}
      </div>
    )
  }

  // 'skipped' and null are different states and the row says which. Skipped
  // means a run considered this alert and deliberately did not classify it;
  // null means no run has reached it.
  if (alert.aiPath === 'skipped') {
    return (
      <div className="ai">
        <span className="signal quiet">not analysed</span>
        <span className="action sans">
          Ordered by time; {alert.severity} alerts skip AI analysis
        </span>
      </div>
    )
  }
  return null
}

export default function AlertRow({ alert, onOpen }) {
  return (
    <article
      className={`row sev-${alert.severity}${alert.status === 'new' ? ' latched' : ''}`}
      onClick={() => onOpen(alert.id)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') onOpen(alert.id)
      }}
      tabIndex={0}
      role="button"
    >
      <div className="gutter" />
      <div className="row-body">
        <div className="row-head">
          <span className="sev">{alert.severity}</span>
          <span className="site">{alert.siteName}</span>
          <span className="muted">{humanise(alert.type)}</span>
          <span className="muted">{relativeTime(alert.occurredAt)}</span>
          <span className="chip">{alert.status}</span>
        </div>
        <div className="desc sans">{alert.description}</div>
        <AiSummary alert={alert} />
      </div>
    </article>
  )
}
