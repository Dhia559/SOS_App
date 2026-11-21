(() => {
  if (!Session.me) return; // redirecting to login
  const { $, $$, escapeHTML, initials, toast } = UI;
  const ME = Session.me.id;
  const layout = $('#chat-layout');
  const thread = $('#thread');
  let convs = [];
  let activeId = null;
  let editingId = null;
  let filterQ = '';

  const ICON = {
    edit: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/></svg>',
    trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/></svg>',
    send: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>',
    back: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6"/></svg>',
    phone: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1.9.4 1.8.7 2.7a2 2 0 0 1-.5 2.1L8 9.8a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.7.7a2 2 0 0 1 1.7 2z"/></svg>',
    info: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="12" cy="12" r="9"/><path d="M12 11v6M12 7.5v.01"/></svg>',
  };

  /* ---------- Time formatting ---------- */
  const fmtTime = (ts) => new Date(ts).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  function fmtShort(ts) {
    const d = new Date(ts), now = new Date();
    if (d.toDateString() === now.toDateString()) return fmtTime(ts);
    const y = new Date(now); y.setDate(now.getDate() - 1);
    if (d.toDateString() === y.toDateString()) return 'Hier';
    return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
  }
  function fmtDay(ts) {
    const s = fmtShort(ts);
    return s.includes(':') ? "Aujourd'hui" : s;
  }

  const avatarHTML = (u, size = '') =>
    `<span class="avatar ${size}" data-tone="${u.tone}">${escapeHTML(initials(u.name))}</span>`;
  const subtitle = (u) => [u.roleName, u.clubName].filter(Boolean).join(' · ');

  /* ---------- Conversation list ---------- */
  async function loadConversations() {
    try {
      convs = await ChatService.listConversations();
    } catch (err) {
      toast(err.message);
    }
    renderList();
  }

  function renderList() {
    const list = convs.filter((c) => !filterQ || c.friend.name.toLowerCase().includes(filterQ));
    $('#conv-list').innerHTML = list.length ? list.map((c, i) => {
      const last = c.last;
      const preview = !last ? 'Nouvelle conversation' : (last.senderId === ME ? 'Vous : ' : '') + last.content;
      return `<button class="conv ${c.id === activeId ? 'is-active' : ''} ${c.unread ? 'is-unread' : ''}" data-id="${c.id}" role="listitem" style="--i:${i}">
        ${avatarHTML(c.friend)}
        <div class="conv-body">
          <div class="conv-top"><span class="conv-name">${escapeHTML(c.friend.name)}</span><span class="conv-time">${last ? fmtShort(last.createdAt) : ''}</span></div>
          <div class="conv-bottom"><span class="conv-preview">${escapeHTML(preview)}</span>${c.unread ? `<span class="conv-badge">${c.unread}</span>` : ''}</div>
        </div>
      </button>`;
    }).join('') : `<p style="padding:1rem;color:var(--navy-50);font-weight:600">${convs.length ? 'Aucun ami trouvé.' : 'Aucune conversation. Cliquez sur + pour écrire à un ami.'}</p>`;

    if (!renderList.done) { renderList.done = true; setTimeout(() => $('#conv-list').classList.add('ready'), 1200); }
  }

  async function renderFriends() {
    try {
      const friends = await ChatService.getFriends();
      $('#friends-row').innerHTML = friends.length
        ? friends.map((f) => `
          <button class="friend" data-friend="${f.id}">${avatarHTML(f)}<span>${escapeHTML(f.firstName)}</span></button>`).join('')
        : '<p>Vous n\'avez pas encore d\'amis. Ajoutez-en depuis le feed.</p>';
    } catch (err) {
      toast(err.message);
    }
  }

  /* ---------- Thread ---------- */
  function messageHTML(m, prev) {
    const mine = m.senderId === ME;
    const newDay = !prev || new Date(prev.createdAt).toDateString() !== new Date(m.createdAt).toDateString();
    const gap = prev && prev.senderId !== m.senderId ? 'gap' : '';
    const sep = newDay ? `<span class="day-sep">${fmtDay(m.createdAt)}</span>` : '';
    const tools = mine ? `<div class="msg-tools">
        <button data-act="edit" aria-label="Modifier">${ICON.edit}</button>
        <button data-act="delete" aria-label="Supprimer">${ICON.trash}</button>
      </div>` : '';
    return `${sep}<div class="msg ${mine ? 'me' : 'them'} ${gap}" data-mid="${m.id}">
      ${tools}
      <div class="text">${escapeHTML(m.content)}</div>
      <div class="meta">${fmtTime(m.createdAt)}</div>
    </div>`;
  }

  async function openConversation(id) {
    activeId = id;
    editingId = null;
    let c = convs.find((x) => x.id === id);
    if (!c) { await loadConversations(); c = convs.find((x) => x.id === id); }
    if (!c) return;
    const friend = c.friend;

    let messages = [];
    try {
      messages = await ChatService.getMessages(id);
    } catch (err) {
      toast(err.message);
    }
    if (activeId !== id) return; // another conversation was opened meanwhile

    thread.innerHTML = `
      <header class="thread-head">
        <button class="icon-btn back" id="btn-back" aria-label="Retour">${ICON.back}</button>
        ${avatarHTML(friend)}
        <div class="thread-who">
          <strong>${escapeHTML(friend.name)}</strong>
          <small>${escapeHTML(subtitle(friend))}</small>
        </div>
        <div class="thread-actions">
          <button class="icon-btn" aria-label="Appeler">${ICON.phone}</button>
          <button class="icon-btn" aria-label="Infos">${ICON.info}</button>
        </div>
      </header>
      <div class="messages" id="messages">
        ${messages.map((m, i) => messageHTML(m, messages[i - 1])).join('')}
      </div>
      <form class="chat-composer" id="chat-form">
        <div class="editing-bar" id="editing-bar"><span>✎ Modification du message</span><button type="button" id="cancel-edit">Annuler</button></div>
        <div class="composer-row">
          <label class="composer-input">
            <span class="sr-only">Votre message</span>
            <textarea id="chat-input" rows="1" maxlength="4000" placeholder="Écrire à ${escapeHTML(friend.firstName)}…"></textarea>
          </label>
          <button class="send-btn" type="submit" id="send-btn" disabled aria-label="Envoyer">${ICON.send}</button>
        </div>
      </form>`;

    scrollBottom(false);
    bindComposer();
    $('#btn-back').addEventListener('click', () => layout.classList.remove('show-thread'));
    layout.classList.add('show-thread');
    if (window.matchMedia('(min-width: 901px)').matches) $('#chat-input').focus();

    if (c.unread) {
      c.unread = 0;
      ChatService.markRead(id).then(Session.refreshUnread).catch(() => {});
    }
    renderList();
  }

  function scrollBottom(smooth = true) {
    const box = $('#messages');
    if (box) box.scrollTo({ top: box.scrollHeight, behavior: smooth && !UI.reduced ? 'smooth' : 'auto' });
  }

  // The sender also receives its own message over the WebSocket: skip duplicates
  function appendMessage(m) {
    const box = $('#messages');
    if (!box || $(`[data-mid="${m.id}"]`, box)) return;
    const nodes = $$('.msg', box);
    const prevEl = nodes[nodes.length - 1];
    const prev = prevEl ? { senderId: prevEl.classList.contains('me') ? ME : -1, createdAt: Date.now() } : null;
    box.insertAdjacentHTML('beforeend', messageHTML(m, prev));
    scrollBottom();
  }

  function updateConversation(m, { unread = false } = {}) {
    const c = convs.find((x) => x.id === m.conversationId);
    if (!c) return false;
    if (!c.last || m.createdAt >= c.last.createdAt) c.last = m;
    if (unread) c.unread++;
    convs.sort((a, b) => (b.last?.createdAt || b.createdAt) - (a.last?.createdAt || a.createdAt));
    renderList();
    return true;
  }

  /* ---------- Composer ---------- */
  function bindComposer() {
    const form = $('#chat-form');
    const input = $('#chat-input');
    const btn = $('#send-btn');
    const grow = () => { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 140) + 'px'; };

    input.addEventListener('input', () => { grow(); btn.disabled = !input.value.trim(); });
    input.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); form.requestSubmit(); }
      if (e.key === 'Escape' && editingId) cancelEdit();
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const content = input.value.trim();
      if (!content) return;

      if (editingId) {
        try {
          const m = await ChatService.editMessage(activeId, editingId, content);
          applyUpdate(m);
          cancelEdit();
        } catch (err) { toast(err.message); }
        return;
      }

      btn.classList.remove('fly'); void btn.offsetWidth; btn.classList.add('fly');
      input.value = ''; grow(); btn.disabled = true;
      try {
        const m = await ChatService.sendMessage(activeId, content);
        appendMessage(m);
        updateConversation(m);
      } catch (err) {
        input.value = content; grow(); btn.disabled = false;
        toast(err.message);
      }
    });

    $('#cancel-edit').addEventListener('click', cancelEdit);

    $('#messages').addEventListener('click', async (e) => {
      const b = e.target.closest('[data-act]');
      if (!b) return;
      const el = b.closest('.msg');
      const id = Number(el.dataset.mid);
      if (b.dataset.act === 'edit') {
        $$('.msg.is-editing').forEach((x) => x.classList.remove('is-editing'));
        editingId = id;
        el.classList.add('is-editing');
        input.value = $('.text', el).textContent;
        grow(); btn.disabled = false;
        $('#editing-bar').classList.add('is-on');
        input.focus();
      }
      if (b.dataset.act === 'delete') {
        if (!confirm('Supprimer ce message ?')) return;
        try {
          await ChatService.deleteMessage(activeId, id);
          applyDelete(activeId, id);
          toast('Message supprimé');
        } catch (err) { toast(err.message); }
      }
    });
  }

  function cancelEdit() {
    editingId = null;
    $$('.msg.is-editing').forEach((x) => x.classList.remove('is-editing'));
    $('#editing-bar')?.classList.remove('is-on');
    const input = $('#chat-input');
    if (input) { input.value = ''; input.style.height = 'auto'; $('#send-btn').disabled = true; }
  }

  /* Edited / deleted messages: same code for my own actions and live pushes */
  function applyUpdate(m) {
    const el = $(`#messages [data-mid="${m.id}"]`);
    if (el) $('.text', el).textContent = m.content;
    const c = convs.find((x) => x.id === m.conversationId);
    if (c?.last?.id === m.id) { c.last = { ...c.last, content: m.content }; renderList(); }
  }

  async function applyDelete(conversationId, messageId) {
    $(`#messages [data-mid="${messageId}"]`)?.remove();
    if (editingId === messageId) cancelEdit();
    const c = convs.find((x) => x.id === conversationId);
    if (c?.last?.id === messageId) await loadConversations(); // new last message preview
  }

  /* ---------- Realtime ---------- */
  Realtime.onMessage(async (raw) => {
    const m = ChatService.toMessage(raw);
    const fromOther = m.senderId !== ME;
    const isActive = m.conversationId === activeId;

    if (isActive) {
      appendMessage(m);
      if (fromOther) ChatService.markRead(m.conversationId).then(Session.refreshUnread).catch(() => {});
    }
    if (!updateConversation(m, { unread: fromOther && !isActive })) await loadConversations();

    if (fromOther && !isActive) {
      const c = convs.find((x) => x.id === m.conversationId);
      toast('Nouveau message de ' + (c ? c.friend.firstName : 'un ami'));
    }
  });
  Realtime.onMessageUpdated((raw) => applyUpdate(ChatService.toMessage(raw)));
  Realtime.onMessageDeleted(({ conversationId, messageId }) => applyDelete(conversationId, messageId));
  Realtime.onError((e) => toast(e.error || 'Erreur de messagerie'));

  /* ---------- Init ---------- */
  document.addEventListener('DOMContentLoaded', async () => {
    await loadConversations();
    renderFriends();

    $('#conv-list').addEventListener('click', (e) => {
      const c = e.target.closest('.conv');
      if (c) openConversation(Number(c.dataset.id));
    });

    $('#btn-new').addEventListener('click', (e) => {
      const open = $('#friends-panel').classList.toggle('is-open');
      e.currentTarget.setAttribute('aria-expanded', String(open));
    });

    $('#friends-row').addEventListener('click', async (e) => {
      const f = e.target.closest('[data-friend]');
      if (!f) return;
      try {
        const id = await ChatService.openConversation(Number(f.dataset.friend));
        $('#friends-panel').classList.remove('is-open');
        $('#btn-new').setAttribute('aria-expanded', 'false');
        if (!convs.some((c) => c.id === id)) await loadConversations();
        openConversation(id);
      } catch (err) {
        toast(err.message);
      }
    });

    let t;
    $('#conv-search').addEventListener('input', (e) => {
      clearTimeout(t);
      t = setTimeout(() => { filterQ = e.target.value.trim().toLowerCase(); renderList(); }, 150);
    });

    // Desktop: open the most recent conversation by default
    if (window.matchMedia('(min-width: 901px)').matches && convs.length) openConversation(convs[0].id);
  });
})();
