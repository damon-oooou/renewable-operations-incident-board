import { useCallback, useEffect, useState } from 'react'
import { getAlert } from '../api/client.js'
import { humanise, timestamp } from '../lib/format.js'
import StatusControl from './StatusControl.jsx'
import NoteComposer from './NoteComposer.jsx'
import NoteTimeline from './NoteTimeline.jsx'

function AiBlock({ alert }) {
  if (alert.aiSignal) {
    return (
      <section className="block">
        <h3>AI analysis</h3>
        <div className="signal">{humanise(alert.aiSignal)}</div>
        <p className="sans">{alert.aiAction}</p>
        <p className="hint">
          {alert.aiPath} · rules {alert.aiRuleVersion} · {timestamp(alert.aiRunAt)}
        </p>
        <p className="hint">Advisory only. It does not change severity or status.</p>
      </section>
    )
  }
  return (
    <section className="block">
      <h3>AI analysis</h3>
      <p className="hint">
        {alert.aiPath === 'skipped'
          ? `Skipped. Only critical and high alerts are analysed. Last run ${timestamp(alert.aiRunAt)}.`
          : 'Not yet analysed. Run AI analysis to classify this alert.'}
      </p>
    </section>
  )
}

export default function AlertDetail({ alertId, onClose, onMutated }) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(async () => {
    try {
      setData(await getAlert(alertId))
      setError(null)
    } catch (e) {
      setError(e.message)
    }
  }, [alertId])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // A mutation refreshes both the panel and the list behind it: a status change
  // can remove the alert from the default filter, and the list has to agree.
  const afterMutation = useCallback(async () => {
    await load()
    await onMutated()
  }, [load, onMutated])

  return (
    <>
      <div className="scrim" onClick={onClose} />
      <aside className="panel" role="dialog" aria-label="Alert detail">
        <button className="close" onClick={onClose} aria-label="Close">
          ×
        </button>

        {error && <p className="error">{error}</p>}
        {!data && !error && <p className="hint">Loading…</p>}

        {data && (
          <>
            <h2 className={`sev-text sev-${data.alert.severity}`}>
              {data.alert.severity} · {humanise(data.alert.type)}
            </h2>
            <p className="muted">{data.alert.id}</p>

            <dl className="facts">
              <dt>Site</dt>
              <dd>
                {data.alert.siteName} · {data.alert.region}
              </dd>
              <dt>Occurred</dt>
              <dd>{timestamp(data.alert.occurredAt)}</dd>
              <dt>Status</dt>
              <dd>{data.alert.status}</dd>
            </dl>

            <section className="block">
              <h3>Description</h3>
              <p className="sans">{data.alert.description}</p>
            </section>

            <AiBlock alert={data.alert} />
            <StatusControl alert={data.alert} onChanged={afterMutation} />
            <NoteComposer alertId={data.alert.id} onAdded={afterMutation} />
            <NoteTimeline notes={data.notes} />
          </>
        )}
      </aside>
    </>
  )
}
