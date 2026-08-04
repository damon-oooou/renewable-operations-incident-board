import { timestamp } from '../lib/format.js'

export default function NoteTimeline({ notes }) {
  if (notes.length === 0) {
    return (
      <section className="block">
        <h3>History</h3>
        <p className="hint">Nothing recorded yet. Status changes and notes appear here.</p>
      </section>
    )
  }

  return (
    <section className="block">
      <h3>History</h3>
      {notes.map((note) => (
        <div className="note" key={note.id}>
          <div className="note-head">
            <span>{note.author}</span>
            <span>{timestamp(note.createdAt)}</span>
          </div>
          {/* System-written status changes stay monospace; operator notes render
              in a sans face. The two voices in the timeline are distinguishable
              before you read a word of them. */}
          <div className={note.author === 'system' ? 'note-body' : 'note-body sans'}>
            {note.body}
          </div>
        </div>
      ))}
    </section>
  )
}
