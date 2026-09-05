const CACHE_NAME = 'loansaas-v2';

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((k) => k !== CACHE_NAME).map((k) => caches.delete(k)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return; // mutations go through the offline queue, not the SW

  const url = new URL(request.url);


  if (url.origin !== self.location.origin) return;

  // Never cache auth/sensitive API calls — always require a live network round-trip.
  if (url.pathname.startsWith('/api/auth')) return;


  if (request.mode === 'navigate' && (url.pathname === '/apply' || url.pathname.startsWith('/apply/'))) {
    return;
  }

  event.respondWith(
    fetch(request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(request, copy));
        }
        return response;
      })
      .catch(() =>
        caches.match(request).then((cached) => {
          if (cached) return cached;
          // Navigations with nothing cached yet: let the app's own
          // client-side offline banner explain the situation.
          if (request.mode === 'navigate') {
            return caches.match('/offline.html');
          }
          return new Response(JSON.stringify({ success: false, error: 'You are offline' }), {
            status: 503,
            headers: { 'Content-Type': 'application/json' },
          });
        })
      )
  );
});