/**
 * Notifications — bell in the nav + floating list (feed and messages pages).
 *   GET /api/notifications?limit=20 · GET /api/notifications/unseen-count
 *   PUT /api/notifications/{id}/seen · PUT /api/notifications/seen-all
 * Live: Realtime.onNotification / onNotificationCount.
 */
(() => {
  if (!Session.me) return; // redirecting to login
  const { $, escapeHTML } = UI;
  let items = [];
  let loaded = false;

  const BELL = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/></svg>';

  function timeAgo(iso) {
    const s = (Date.now() - new Date(iso)) / 1000;
    if (s < 60) return "À l'instant";
    if (s < 3600) return `Il y a ${Math.floor(s / 60)} min`;
    if (s < 86400) return `Il y a ${Math.floor(s / 3600)} h`;
    if (s < 172800) return 'Hier';
    return new Date(iso).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
  }

  /* ---------- DOM ---------- */
  const actions = $('.nav-actions');
  actions.insertAdjacentHTML('afterbegin', `
    <button class="icon-btn notif-btn" id="btn-notif" aria-label="Notifications" aria-expanded="false" aria-controls="notif-panel">
      ${BELL}<span class="notif-badge" id="notif-badge" style="display:none">0</span>
    </button>`);
  document.body.insertAdjacentHTML('beforeend', `
    <section class="notif-panel" id="notif-panel" aria-label="Notifications">
      <header class="notif-head">
        <h4>Notifications</h4>
        <button type="button" id="notif-all">Tout marquer comme lu</button>
      </header>
      <div class="notif-list" id="notif-list"><p class="notif-empty">Chargement…</p></div>
    </section>`);

  const btn = $('#btn-notif');
  const panel = $('#notif-panel');

  function setCount(n) {
    const badge = $('#notif-badge');
    badge.textContent = n > 99 ? '99+' : n;
    badge.style.display = n ? '' : 'none';
    $('#notif-all').disabled = !n;
  }

  function render() {
    $('#notif-list').innerHTML = items.length
      ? items.map((n) => `
        <button class="notif-item ${n.seen ? '' : 'is-unseen'}" data-id="${n.id}">
          <span class="notif-dot"></span>
          <span>
            <strong>${escapeHTML(n.title)}</strong>
            <p>${escapeHTML(n.content)}</p>
            <small>${escapeHTML(timeAgo(n.createdAt))}</small>
          </span>
        </button>`).join('')
      : '<p class="notif-empty">Aucune notification pour le moment.</p>';
  }

  async function refreshCount() {
    try { setCount((await Api.get('/notifications/unseen-count')).count); } catch (_) {}
  }

  async function load() {
    try {
      items = await Api.get('/notifications?limit=20');
      loaded = true;
      render();
    } catch (err) {
      $('#notif-list').innerHTML = `<p class="notif-empty">${escapeHTML(err.message)}</p>`;
    }
  }

  function toggle(open = !panel.classList.contains('is-open')) {
    panel.classList.toggle('is-open', open);
    btn.setAttribute('aria-expanded', String(open));
    if (open && !loaded) load();
  }

  /* ---------- Events ---------- */
  btn.addEventListener('click', (e) => { e.stopPropagation(); toggle(); });
  document.addEventListener('click', (e) => { if (!panel.contains(e.target)) toggle(false); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') toggle(false); });

  $('#notif-list').addEventListener('click', async (e) => {
    const el = e.target.closest('.notif-item');
    if (!el) return;
    const n = items.find((x) => x.id === Number(el.dataset.id));
    if (n.seen) return;
    n.seen = true;
    el.classList.remove('is-unseen');
    try {
      await Api.put(`/notifications/${n.id}/seen`);
    } catch (_) { /* count is resynced below */ }
    refreshCount();
  });

  $('#notif-all').addEventListener('click', async () => {
    try {
      await Api.put('/notifications/seen-all');
      items.forEach((n) => (n.seen = true));
      render();
      setCount(0);
    } catch (err) { UI.toast(err.message); }
  });

  Realtime.onNotification((n) => {
    if (loaded) { items.unshift(n); render(); }
  });
  Realtime.onNotificationCount(({ count }) => setCount(count));

  refreshCount();
})();
