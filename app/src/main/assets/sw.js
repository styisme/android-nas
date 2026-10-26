const CACHE_NAME = 'nas-cache-v4.5.0';
const urlsToCache = ['/', '/index.html'];

self.addEventListener('install', event => {
  self.skipWaiting();
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(urlsToCache)));
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(cacheNames => {
    return Promise.all(cacheNames.map(cacheName => {
      if (cacheName !== CACHE_NAME) return caches.delete(cacheName);
    }));
  }));
});

self.addEventListener('fetch', event => {
  if (event.request.headers.has('range')) return; // 🔥 放行 Range 请求
  event.respondWith(caches.match(event.request).then(response => response || fetch(event.request)));
});