/**
 * Session helpers for app pages (feed, conversations).
 * Not logged in -> back to login.html, and Session.me is null
 * (page scripts stop when they see it).
 */
const Session = (() => {
  const me = AuthService.getUser();
  if (!me || !AuthService.getToken()) {
    location.replace('login.html');
    return { me: null, refreshUnread: () => {} };
  }

  function setUnread(count) {
    UI.$$('[data-unread]').forEach((el) => {
      el.textContent = count;
      el.style.display = count ? '' : 'none';
    });
    UI.$$('.tabbar .dot-badge').forEach((el) => (el.style.display = count ? '' : 'none'));
  }

  async function refreshUnread() {
    try {
      const { count } = await Api.get('/conversations/unread-count');
      setUnread(count);
    } catch (_) { /* badge stays as is */ }
  }

  function fillUserSlots() {
    UI.$$('[data-me-first]').forEach((el) => (el.textContent = me.firstName));
    UI.$$('[data-me-name]').forEach((el) => (el.textContent = me.name));
    UI.$$('[data-me-initials]').forEach((el) => (el.textContent = UI.initials(me.name)));

    const roleEl = UI.$('#me-role');
    if (roleEl) {
      const role = AuthService.ROLES[me.roleId];
      roleEl.textContent = role ? role.label : me.roleName;
      roleEl.className = 'chip ' + (role ? role.chip : '');
    }

    setUnread(0);

    const logout = UI.$('#btn-logout');
    if (logout) logout.addEventListener('click', () => {
      AuthService.logout();
      window.location.href = 'login.html';
    });
  }

  // Scripts sit at the end of <body>: the DOM is ready, and this runs
  // before core.js splits headings into words on DOMContentLoaded.
  fillUserSlots();
  refreshUnread();

  // Live updates shared by every app page (the chat page shows its own toasts)
  const onChatPage = document.body.classList.contains('chat-page');
  Realtime.onMessage((m) => {
    refreshUnread();
    if (!onChatPage && m.senderId !== me.id) UI.toast('Nouveau message 💬');
  });
  Realtime.onRead(refreshUnread);
  Realtime.onNotification((n) => { if (!onChatPage || !/message/i.test(n.title)) UI.toast(n.title); });
  Realtime.connect();

  return { me, refreshUnread };
})();
