# H Search backend

A small server between the app and a search provider. It keeps API keys off the phone,
removes tracking parameters from result links, returns only http and https links, caches results for 10 minutes,
and does not log queries.

## Files
- `server.mjs`: the HTTP server (providers, cache, rate limit, routes).
- `lib.mjs`: pure helpers (link cleaning, text cleaning, provider choice, rate-limit window). Tested directly.
- `test/lib.test.mjs`: unit tests for the helpers.

## Run the tests

    node --test test/lib.test.mjs

Node 20 or newer is needed (the tests use the built-in test runner).

## Run it

Brave Search API (paid per query after a monthly credit):

    docker build -t hsearch .
    docker run -p 8080:8080 -e BRAVE_API_KEY=your_key hsearch

SearXNG (your own instance; enable `format: json` under `search.formats` in its settings.yml).
Give the address of the instance only, without a path:

    docker run -p 8080:8080 -e SEARXNG_URL=https://your-searxng.example.com hsearch

The server refuses to start if neither provider is set, or if the SearXNG address is not a valid web address.

## Check it is running

    curl https://your-server/health

Answers `{"ok":true,"provider":"brave"}` (or `searxng`). Use this for uptime monitoring.

## Before the app can use it

1. Put it behind HTTPS (a reverse proxy such as Caddy or nginx, or a host that gives you HTTPS).
   Android blocks plain HTTP by default.
2. Give the app the HTTPS address at build time, never in the source: set the repository variable
   `SEARCH_API_BASE` in GitHub (Settings, then Variables), or pass `-PsearchApiBase=https://...` to Gradle.
3. Behind a proxy, rate limits apply to the proxy's address, so the limit is shared. Forward client addresses if you need per-user limits.
