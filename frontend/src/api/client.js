// The only module that knows the API exists.
//
// '/api' is relative on purpose: in development Vite proxies it to the backend,
// and in production the SPA is served by Spring from the same origin. Neither
// case needs an absolute URL, so there is no environment-specific base to get
// wrong at deploy time.
const BASE_URL = '/api'

class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.status = status
  }
}

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(BASE_URL + path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
  } catch (cause) {
    // A network failure is not the same as a rejected request, and the message
    // the user needs is different.
    throw new ApiError('Could not reach the server.', 0)
  }

  const text = await response.text()
  const body = text ? JSON.parse(text) : null

  if (!response.ok) {
    // The backend returns {"error": "..."} for both 400 and 404, and those
    // messages are written to be shown to an operator.
    throw new ApiError(body?.error || `Request failed (${response.status})`, response.status)
  }
  return body
}

export function listSites() {
  return request('/sites')
}

/**
 * Returns { alerts, hiddenCount }. The array is already in display order --
 * severity band, then AI signal priority within critical and high, then newest
 * first. Do not sort it here. A second sort implementation would eventually
 * disagree with the server's, and the disagreement would show up as rows that
 * move when you navigate.
 */
export function listAlerts({ siteId, status }) {
  const params = new URLSearchParams()
  if (siteId) params.set('siteId', siteId)
  if (status) params.set('status', status)
  const query = params.toString()
  return request(`/alerts${query ? `?${query}` : ''}`)
}

export function getAlert(id) {
  return request(`/alerts/${encodeURIComponent(id)}`)
}

/**
 * Scope is the current filter, not a list of ids: the server resolves what is
 * on screen using the same query that built the screen.
 *
 * Always resolves on a 200, including when every classification fell back to
 * keyword matching. The fallback is a designed path, so degradation arrives in
 * the response body rather than as a rejected promise.
 */
export function analyze({ siteId, status }) {
  return request('/alerts/analyze', {
    method: 'POST',
    body: JSON.stringify({ siteId: siteId || null, status: status || null }),
  })
}

/** Returns the updated alert directly, not wrapped. */
export function changeStatus(id, status) {
  return request(`/alerts/${encodeURIComponent(id)}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

/** Returns 201 with the created note. */
export function addNote(id, body) {
  return request(`/alerts/${encodeURIComponent(id)}/notes`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  })
}
