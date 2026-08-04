import { useState } from 'react'
import { addNote } from '../api/client.js'
import { NOTE_MAX_LENGTH } from '../constants.js'

export default function NoteComposer({ alertId, onAdded }) {
  const [body, setBody] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState(null)

  const length = body.trim().length
  const tooLong = length > NOTE_MAX_LENGTH
  // Client validation is additive, not a replacement: the server rejects the
  // same cases independently, and its 400 is still displayed if it fires.
  const blocked = saving || length === 0 || tooLong

  async function submit() {
    setSaving(true)
    setError(null)
    try {
      await addNote(alertId, body)
      setBody('')
      await onAdded()
    } catch (e) {
      setError(e.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="block">
      <h3>Add a follow-up note</h3>
      <textarea
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder="What did you find, or what did you do?"
        disabled={saving}
      />
      <div className="row-inline spread">
        <span className={tooLong ? 'count over' : 'count'}>
          {length} / {NOTE_MAX_LENGTH}
        </span>
        {/* Disabled for the whole round trip. In an append-only log a double
            submission is permanent, so the cheap guard is worth having. */}
        <button onClick={submit} disabled={blocked}>
          {saving ? 'Adding…' : 'Add note'}
        </button>
      </div>
      <p className="hint">Notes cannot be edited or deleted once added.</p>
      {error && <p className="error">{error}</p>}
    </section>
  )
}
