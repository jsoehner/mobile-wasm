/**
 * MobileWasm PWA — service-worker.js
 *
 * Implements a Cache-First strategy for app shell assets so the app
 * loads offline after the first visit.
 */

'use strict';

const CACHE_NAME = 'mobile-wasm-pwa-v1';

/** App-shell assets to pre-cache on install. */
const PRECACHE_ASSETS = [
  './',
  './index.html',
  './styles.css',
  './app.js',
  './manifest.json',
  './icons/icon-192.png',
  './icons/icon-512.png',
];

/* ── Install: pre-cache app shell ──────────────────────────────── */
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(PRECACHE_ASSETS)),
  );
  // Activate immediately without waiting for existing tabs to close.
  self.skipWaiting();
});

/* ── Activate: remove stale caches ─────────────────────────────── */
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys
          .filter(key => key !== CACHE_NAME)
          .map(key => caches.delete(key)),
      ),
    ),
  );
  // Take control of all open clients immediately.
  self.clients.claim();
});

/* ── Fetch: cache-first for same-origin, network-only for cross-origin ── */
self.addEventListener('fetch', event => {
  const { request } = event;
  const url = new URL(request.url);

  // Only intercept GET requests from the same origin.
  if (request.method !== 'GET' || url.origin !== self.location.origin) return;

  event.respondWith(
    caches.match(request).then(cached => {
      if (cached) return cached;

      return fetch(request).then(response => {
        // Cache successful responses for app-shell assets.
        if (response.ok && PRECACHE_ASSETS.some(a => request.url.endsWith(a.replace('./', '')))) {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(request, clone));
        }
        return response;
      });
    }),
  );
});
