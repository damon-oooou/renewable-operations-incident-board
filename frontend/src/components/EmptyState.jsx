export default function EmptyState({ onReset }) {
  return (
    <div className="empty">
      <p>No alerts match this filter.</p>
      <button className="ghost" onClick={onReset}>
        Show all open alerts
      </button>
    </div>
  )
}
