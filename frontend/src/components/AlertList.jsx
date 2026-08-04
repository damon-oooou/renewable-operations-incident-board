import AlertRow from './AlertRow.jsx'
import EmptyState from './EmptyState.jsx'

export default function AlertList({ alerts, onOpen, onReset }) {
  if (alerts.length === 0) {
    return <EmptyState onReset={onReset} />
  }

  // Rendered in the order the server returned. No sort, no reverse, no
  // groupBy -- the ordering rules live in one SQL query and this is the only
  // place they are displayed.
  return (
    <div>
      {alerts.map((alert) => (
        <AlertRow key={alert.id} alert={alert} onOpen={onOpen} />
      ))}
    </div>
  )
}
