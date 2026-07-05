const STORAGE_KEY = 'androidNeteaseApiUrl';
const FALLBACK_URL = process.env.VUE_APP_NETEASE_API_URL || '';

function normalizeUrl(url) {
  return (url || '').trim().replace(/\/+$/, '');
}

export function getNeteaseApiBaseUrl() {
  if (process.env.VUE_APP_PLATFORM === 'android') {
    return normalizeUrl(localStorage.getItem(STORAGE_KEY) || FALLBACK_URL);
  }
  return normalizeUrl(FALLBACK_URL);
}

export function setNeteaseApiBaseUrl(url) {
  const normalized = normalizeUrl(url);
  if (normalized) {
    localStorage.setItem(STORAGE_KEY, normalized);
  } else {
    localStorage.removeItem(STORAGE_KEY);
  }
  return getNeteaseApiBaseUrl();
}
