// Lounge Remote service worker. Cache number MUST equal APP_VERSION in index.html.
const CACHE = 'lounge-remote-v1';
const SHELL = ['./', './index.html', './manifest.webmanifest',
  './icons/icon-192.png', './icons/icon-512.png', './icons/icon-maskable-512.png'];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE)
    .then((c) => c.addAll(SHELL.map((u) => new Request(u, { cache: 'reload' }))))
    .then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys
      .filter((k) => k.startsWith('lounge-remote-') && k !== CACHE)
      .map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  // Only our own origin + scope. Firebase (gstatic SDK, RTDB websockets/long-poll) is never cached.
  if (url.origin !== self.location.origin) return;
  const scope = new URL(self.registration.scope);
  if (!url.pathname.startsWith(scope.pathname)) return;

  const isPage = req.mode === 'navigate' || url.pathname === scope.pathname ||
    url.pathname.endsWith('/index.html');
  if (isPage) {
    // Network-first, bypassing the HTTP cache (GitHub Pages sends max-age=600).
    e.respondWith(fetch(new Request(url.pathname.endsWith('/index.html') ? req.url : './index.html', { cache: 'reload' }))
      .then((res) => {
        if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put('./index.html', copy)); }
        return res;
      })
      .catch(() => caches.match('./index.html')));
    return;
  }

  // Static assets (icons, manifest): cache-first.
  e.respondWith(caches.match(req).then((hit) => hit || fetch(req).then((res) => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(req, copy)); }
    return res;
  })));
});
