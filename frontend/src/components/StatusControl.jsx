import { useState } from 'react'
import { changeStatus } from '../api/client.js'
import { STATUSES } from '../constants.js'

export default function StatusControl({ alert, onChanged }) {
  const [target, setTarget] = useState(alert.status)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  async function submit() {
    setSaving(true)
    setError(null)
    try {
      await changeStatus(alert.id, target)
      await onChanged()
    } catch (e) {
      // Includes the 400 for setting a status to its current value. Shown in
      // place rather than swallowed: the server's message already explains it.
      setError(e.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="block">
      <h3>Change status</h3>
      <div className="row-inline">
        <select value={target} onChange={(e) => setTarget(e.target.value)} disabled={saving}>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>
        {/* Any transition is allowed, including backward: closing an alert in
            error is common and a duplicate alert is the worse alternative. */}
        <button onClick={submit} disabled={saving}>
          {saving ? 'Saving…' : 'Change status'}
        </button>
      </div>
      {error && <p className="error">{error}</p>}
    </section>
  )
}
