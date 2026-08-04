export default function AnalysisBanner({ summary, error }) {
  if (error) {
    return <div className="banner warn">{error}</div>
  }
  if (!summary) return null

  const parts = [`${summary.analyzed} alerts analysed`]
  if (summary.llm) parts.push(`${summary.llm} by model`)
  if (summary.fallback) parts.push(`${summary.fallback} by keyword fallback`)
  if (summary.skipped) parts.push(`${summary.skipped} skipped as medium or low`)

  // A run that silently degraded to keyword matching still produces
  // plausible-looking suggestions, so the degradation has to be stated. If the
  // key is missing that is almost always the reason, so name it.
  const degraded = summary.fallback > 0

  return (
    <div className={degraded ? 'banner warn' : 'banner'}>
      {parts.join(' · ')}
      {degraded && !summary.apiKeyConfigured && ' — ANTHROPIC_API_KEY is not set'}
    </div>
  )
}
