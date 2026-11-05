// Kill switch for the old /lounge/remote/ and /lounge/app/ service workers (Lounge Remote moved to
// cheungeric02.github.io/lounge-remote/ in web v7; its cache is loungeremote-vN, which this never touches).
// Browsers re-fetch this file on navigation; it removes the old caches and unregisters itself.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k.startsWith('lounge-remote-') || k.startsWith('lounge-app-')).map((k) => caches.delete(k))))
    .then(() => self.registration.unregister())
    .then(() => self.clients.matchAll({ type: 'window' }))
    .then((cs) => cs.forEach((c) => c.navigate(c.url))));
});
