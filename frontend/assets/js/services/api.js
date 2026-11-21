/**
 * Api — thin fetch() wrapper for the SOS backend (Spring Boot).
 *  - base URL: window.SOS_API_URL if set, otherwise port 8080 on the machine that served the page
 *    (http://172.16.x.x:3000 -> http://172.16.x.x:8080, http://localhost:3000 -> http://localhost:8080)
 *  - sends "Authorization: Bearer <token>" when logged in
 *  - throws an Error carrying the backend's { message } and the HTTP status
 *  - on 401 (expired/invalid token) clears the session and goes back to login
 */
const Api = (() => {
  const host = location.hostname || 'localhost'; // empty when the page is opened as a file
  const BASE = (window.SOS_API_URL || `http://${host}:8080`).replace(/\/$/, '');
  const API_URL = BASE + '/api';
  const WS_URL = BASE.replace(/^http/, 'ws') + '/ws';

  async function request(method, path, body) {
    const headers = { Accept: 'application/json' };
    const token = AuthService.getToken();
    if (token) headers.Authorization = 'Bearer ' + token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    let res;
    try {
      res = await fetch(API_URL + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    } catch (_) {
      throw Object.assign(new Error('Serveur injoignable. Vérifiez que le backend tourne.'), { status: 0 });
    }

    if (res.status === 204) return null;
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch (_) { data = null; }

    if (!res.ok) {
      if (res.status === 401 && token) {
        AuthService.logout();
        if (!/login\.html$/.test(location.pathname)) location.href = 'login.html';
      }
      throw Object.assign(new Error(data?.message || `Erreur ${res.status}`), { status: res.status, data });
    }
    return data;
  }

  return {
    API_URL,
    WS_URL,
    get: (p) => request('GET', p),
    post: (p, b) => request('POST', p, b),
    put: (p, b) => request('PUT', p, b),
    del: (p) => request('DELETE', p),
  };
})();
