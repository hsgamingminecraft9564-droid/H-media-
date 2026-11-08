// H Search backend. The app calls this server; API keys stay here, never in the app.
// Choose one provider with an environment variable:
//   BRAVE_API_KEY=...          Brave Search API (metered; key stays on this server)
//   SEARXNG_URL=https://...    your SearXNG instance, origin only (enable format=json in its settings)
// Queries are not logged. Tracking parameters are removed. Only http and https links are returned.
import http from "node:http"
import { chooseProvider, isValidSearxngUrl, cleanUrl, plainText, allowRequest, pruneHits } from "./lib.mjs"

const PORT = Number(process.env.PORT ?? 8080)
const CACHE_MS = 10 * 60 * 1000
const CACHE_MAX = 500
const PER_MINUTE = 30
const UPSTREAM_TIMEOUT_MS = 8000

const PROVIDER = chooseProvider(process.env)
if (!PROVIDER) {
  console.error("Cannot start: set BRAVE_API_KEY or SEARXNG_URL first.")
  process.exit(1)
}
if (PROVIDER === "searxng" && !isValidSearxngUrl(process.env.SEARXNG_URL)) {
  console.error("Cannot start: SEARXNG_URL is not a valid web address.")
  process.exit(1)
}

const cache = new Map()
const hits = new Map()

async function brave(q) {
  const url = new URL("https://api.search.brave.com/res/v1/web/search")
  url.searchParams.set("q", q)
  url.searchParams.set("count", "10")
  const res = await fetch(url, {
    headers: { Accept: "application/json", "X-Subscription-Token": process.env.BRAVE_API_KEY },
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  })
  if (!res.ok) throw new Error(`brave HTTP ${res.status}`)
  const data = await res.json()
  return (data.web?.results ?? []).map((r) => ({ title: plainText(r.title), url: r.url, snippet: plainText(r.description) }))
}

async function searxng(q) {
  const url = new URL("/search", process.env.SEARXNG_URL)
  url.searchParams.set("q", q)
  url.searchParams.set("format", "json")
  const res = await fetch(url, { signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS) })
  if (!res.ok) throw new Error(`searxng HTTP ${res.status}`)
  const data = await res.json()
  return (data.results ?? []).map((r) => ({ title: plainText(r.title), url: r.url, snippet: plainText(r.content) }))
}

async function search(q) {
  return PROVIDER === "brave" ? brave(q) : searxng(q)
}

function send(res, status, body) {
  res.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" })
  res.end(JSON.stringify(body))
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost")
  if (url.pathname === "/health") return send(res, 200, { ok: true, provider: PROVIDER })
  if (url.pathname !== "/search") return send(res, 404, { error: "Not found" })

  const q = (url.searchParams.get("q") ?? "").trim().slice(0, 200)
  if (!q) return send(res, 400, { error: "Type something to search for." })
  if (!allowRequest(hits, req.socket.remoteAddress, Date.now(), PER_MINUTE)) {
    return send(res, 429, { error: "Too many searches. Wait a minute and try again." })
  }
  pruneHits(hits, Date.now(), 5000)

  const key = q.toLowerCase()
  const hit = cache.get(key)
  if (hit && Date.now() - hit.at < CACHE_MS) return send(res, 200, hit.body)

  try {
    const raw = await search(q)
    const results = raw.map((r) => ({ ...r, url: cleanUrl(r.url) })).filter((r) => r.url && r.title)
    const body = { query: q, results }
    if (cache.size >= CACHE_MAX) cache.delete(cache.keys().next().value)
    cache.set(key, { at: Date.now(), body })
    send(res, 200, body)
  } catch (err) {
    // Details go to the server log for the developer; the app shows a plain message
    console.error("search failed:", err.message)
    send(res, 502, { error: "Search is unavailable right now. Please try again soon." })
  }
})

server.listen(PORT, () => console.log(`H Search backend (${PROVIDER}) on port ${PORT}`))
