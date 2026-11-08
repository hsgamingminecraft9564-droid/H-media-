import { test } from "node:test"
import assert from "node:assert/strict"
import { cleanUrl, plainText, chooseProvider, isValidSearxngUrl, allowRequest, pruneHits } from "../lib.mjs"

test("tracking parameters are removed and other parameters kept", () => {
  assert.equal(cleanUrl("https://example.com/a?utm_source=x&id=7&fbclid=abc"), "https://example.com/a?id=7")
})

test("only http and https links are returned", () => {
  assert.equal(cleanUrl("javascript:alert(1)"), null)
  assert.equal(cleanUrl("intent://example#Intent;end"), null)
  assert.equal(cleanUrl("file:///etc/passwd"), null)
  assert.equal(cleanUrl("not a url"), null)
  assert.equal(cleanUrl("http://example.org/b"), "http://example.org/b")
})

test("snippets lose tags and decode entities", () => {
  assert.equal(plainText('A &quot;great&quot; <em>show</em> &amp; more &#x27;x&#39;'), `A "great" show & more 'x'`)
  assert.equal(plainText(undefined), "")
})

test("provider comes from the environment and BRAVE wins when both are set", () => {
  assert.equal(chooseProvider({}), null)
  assert.equal(chooseProvider({ SEARXNG_URL: "https://s.example" }), "searxng")
  assert.equal(chooseProvider({ BRAVE_API_KEY: "k" }), "brave")
  assert.equal(chooseProvider({ BRAVE_API_KEY: "k", SEARXNG_URL: "https://s.example" }), "brave")
})

test("SearXNG address must be a web address", () => {
  assert.equal(isValidSearxngUrl("https://s.example.com"), true)
  assert.equal(isValidSearxngUrl("not a url"), false)
  assert.equal(isValidSearxngUrl("ftp://s.example.com"), false)
})

test("rate limit allows the set number per minute, then refuses", () => {
  const hits = new Map()
  const now = 1_000_000
  for (let i = 0; i < 30; i++) assert.equal(allowRequest(hits, "1.2.3.4", now, 30), true)
  assert.equal(allowRequest(hits, "1.2.3.4", now, 30), false)
  assert.equal(allowRequest(hits, "5.6.7.8", now, 30), true) // other clients are not affected
  assert.equal(allowRequest(hits, "1.2.3.4", now + 61_000, 30), true) // window has passed
})

test("quiet clients are pruned from the rate-limit map", () => {
  const hits = new Map()
  for (let i = 0; i < 10; i++) hits.set(`ip${i}`, [0])
  pruneHits(hits, 120_000, 5)
  assert.equal(hits.size, 0)
})
