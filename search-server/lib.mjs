// Pure helpers for the H Search backend. No network or environment access here, so they can be tested directly.

export const TRACKING = /^(utm_\w+|fbclid|gclid|mc_cid|mc_eid|igshid|yclid|msclkid)$/i

// Returns a clean web link, or null for anything that is not an http or https address
export function cleanUrl(raw) {
  try {
    const u = new URL(raw)
    if (u.protocol !== "https:" && u.protocol !== "http:") return null
    for (const key of [...u.searchParams.keys()]) {
      if (TRACKING.test(key)) u.searchParams.delete(key)
    }
    return u.toString()
  } catch {
    return null
  }
}

// Snippets from some providers contain HTML tags and entities; the app shows plain text
export function plainText(raw) {
  return String(raw ?? "")
    .replace(/<[^>]*>/g, "")
    .replace(/&quot;/g, '"')
    .replace(/&#x27;|&#39;/g, "'")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .trim()
}

// Which provider to use, from the environment. Returns null when none is configured.
export function chooseProvider(env) {
  if (env.BRAVE_API_KEY) return "brave"
  if (env.SEARXNG_URL) return "searxng"
  return null
}

// True only for a usable SearXNG base address (http or https, with a host)
export function isValidSearxngUrl(value) {
  try {
    const u = new URL(value)
    return (u.protocol === "https:" || u.protocol === "http:") && u.host !== ""
  } catch {
    return false
  }
}

// Sliding one-minute window per client. Returns true if the request is allowed, and records it.
export function allowRequest(hits, ip, now, perMinute) {
  const recent = (hits.get(ip) ?? []).filter((t) => now - t < 60_000)
  const ok = recent.length < perMinute
  if (ok) recent.push(now)
  hits.set(ip, recent)
  return ok
}

// Drops clients that have been quiet for a minute, so the map cannot grow without limit
export function pruneHits(hits, now, maxSize) {
  if (hits.size <= maxSize) return
  for (const [key, times] of hits) {
    if (!times.some((t) => now - t < 60_000)) hits.delete(key)
  }
}
