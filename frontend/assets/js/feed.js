(() => {
  if (!Session.me) return; // redirecting to login
  const { $, $$, escapeHTML, initials, toast, observeReveals, reduced } = UI;
  const me = Session.me;
  const ROLES = AuthService.ROLES;

  let posts = [];
  let nextBefore = null;
  let filter = 'all';
  let query = '';

  const ICON = {
    heart: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round"><path d="M12 21s-7.5-4.6-9.5-9.3C1.1 8.3 3.3 4.5 7 4.5c2 0 3.6 1.1 5 2.8 1.4-1.7 3-2.8 5-2.8 3.7 0 5.9 3.8 4.5 7.2C19.5 16.4 12 21 12 21z"/></svg>',
    comment: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12a8 8 0 0 1-11.6 7.1L4 21l1.9-5.4A8 8 0 1 1 21 12z"/></svg>',
    share: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 12v7a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7M16 6l-4-4-4 4M12 2v13"/></svg>',
    send: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4z"/></svg>',
    trash: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6"/></svg>',
  };

  /* ---------- Mapping backend -> view ---------- */
  function timeAgo(iso) {
    const d = new Date(iso);
    const s = (Date.now() - d) / 1000;
    if (s < 60) return "À l'instant";
    if (s < 3600) return `Il y a ${Math.floor(s / 60)} min`;
    if (s < 86400) return `Il y a ${Math.floor(s / 3600)} h`;
    if (s < 172800) return 'Hier';
    return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
  }

  function audience(p) {
    const parts = [];
    if (p.visibleRoleId) parts.push(p.visibleRoleName);
    if (p.visibleClubId) parts.push(p.visibleClubName);
    return parts.length ? parts.join(' · ') : 'Tout le monde';
  }

  function toPost(p) {
    const author = AuthService.toUser(p.author);
    return {
      id: p.id,
      author,
      audience: audience(p),
      createdAt: p.createdAt,
      text: p.content,
      likes: p.likeCount,
      liked: p.likedByMe,
      commentCount: p.commentCount,
      comments: null, // loaded when the comments panel is opened
      mine: author.id === me.id,
      hasImage: p.hasImage,
      event: p.event, // { title, startsAt, place } or null
    };
  }

  const toComment = (c) => {
    const u = AuthService.toUser(c.author);
    return { author: u.name, tone: u.tone, text: c.content };
  };

  /* ---------- Rendering ---------- */
  function commentHTML(c) {
    return `<div class="comment">
      <span class="avatar avatar--sm" data-tone="${c.tone || 'cyan'}">${escapeHTML(initials(c.author))}</span>
      <div class="bubble"><strong>${escapeHTML(c.author)}</strong>${escapeHTML(c.text)}</div>
    </div>`;
  }

  const countLabel = (n) => `${n} commentaire${n > 1 ? 's' : ''}`;

  function postHTML(p, i) {
    const role = ROLES[p.author.roleId];
    const chip = role ? `<span class="chip ${role.chip}">${escapeHTML(role.label)}</span>` : '';
    return `<article class="panel post" data-id="${p.id}" data-reveal style="--d:${Math.min(i, 4) * 0.08}s">
      <header class="post-head">
        <span class="avatar" data-tone="${p.author.tone}">${escapeHTML(initials(p.author.name))}</span>
        <div class="post-meta">
          <strong>${escapeHTML(p.author.name)}${p.author.clubName ? ` <span class="post-club">· ${escapeHTML(p.author.clubName)}</span>` : ''}</strong>
          <small>${chip}${escapeHTML(timeAgo(p.createdAt))} · 👁 ${escapeHTML(p.audience)}</small>
        </div>
        ${p.mine ? `<button class="icon-btn" data-action="delete" aria-label="Supprimer la publication">${ICON.trash}</button>` : ''}
      </header>
      <p class="post-text">${escapeHTML(p.text)}</p>
      ${p.event ? eventHTML(p.event) : ''}
      ${p.hasImage ? `<div class="post-visual pv-photo" data-photo="${p.id}"></div>` : ''}
      <div class="post-stats"><span data-likes>${p.likes} j'aime</span><span data-ccount>${countLabel(p.commentCount)}</span></div>
      <div class="post-actions">
        <button class="act ${p.liked ? 'is-liked' : ''}" data-action="like" aria-pressed="${p.liked}">${ICON.heart}<span>J'aime</span></button>
        <button class="act" data-action="comment" aria-expanded="false">${ICON.comment}<span>Commenter</span></button>
        <button class="act" data-action="share">${ICON.share}<span>Partager</span></button>
      </div>
      <div class="comments"><div><div class="comments-inner">
        <div class="comments-list">${(p.comments || []).map(commentHTML).join('')}</div>
        <form class="comment-form">
          <span class="avatar avatar--sm" data-tone="${me.tone}">${escapeHTML(initials(me.name))}</span>
          <input type="text" placeholder="Écrire un commentaire…" aria-label="Écrire un commentaire" maxlength="2000" />
          <button class="icon-btn" type="submit" aria-label="Envoyer">${ICON.send}</button>
        </form>
      </div></div></div>
    </article>`;
  }

  /* ---------- Event card + photos ---------- */
  function eventHTML(ev) {
    const d = new Date(ev.startsAt);
    const day = d.toLocaleDateString('fr-FR', { weekday: 'short', day: '2-digit', month: 'short' }).toUpperCase();
    const time = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return `<div class="post-visual pv-event">
      <span class="pv-date">${escapeHTML(day)} · ${time}</span>
      <div><h3>${escapeHTML(ev.title)}</h3>${ev.place ? `<span class="pv-place">📍 ${escapeHTML(ev.place)}</span>` : ''}</div>
    </div>`;
  }

  // Photos need the auth header, so they're fetched as blobs (cached per post)
  const photoUrls = new Map();
  function loadPhotos(root) {
    $$('[data-photo]', root).forEach(async (box) => {
      const id = box.dataset.photo;
      try {
        if (!photoUrls.has(id)) photoUrls.set(id, URL.createObjectURL(await Api.blob(`/posts/${id}/image`)));
        box.innerHTML = `<img src="${photoUrls.get(id)}" alt="Photo de la publication" loading="lazy" />`;
      } catch (_) {
        box.remove();
      }
    });
  }

  function renderPosts() {
    const list = posts.filter((p) =>
      (filter === 'all' || p.author.roleId === Number(filter)) &&
      (!query || (p.text + ' ' + p.author.name).toLowerCase().includes(query))
    );
    const root = $('#posts');
    root.innerHTML = (list.length
      ? list.map(postHTML).join('')
      : '<div class="panel empty">Aucune publication ne correspond.</div>')
      + (nextBefore ? '<button class="btn btn-white btn-block" id="load-more" type="button">Voir plus</button>' : '');
    observeReveals(root);
    loadPhotos(root);
  }

  async function loadFeed(more = false) {
    const qs = new URLSearchParams({ limit: 20 });
    if (more && nextBefore) qs.set('before', nextBefore);
    try {
      const page = await Api.get('/feed?' + qs);
      const fresh = page.posts.map(toPost);
      posts = more ? posts.concat(fresh) : fresh;
      nextBefore = page.nextBefore;
      renderPosts();
    } catch (err) {
      $('#posts').innerHTML = `<div class="panel empty">${escapeHTML(err.message)}</div>`;
    }
  }

  /* ---------- Side panels ---------- */
  function personRow(u, action) {
    return `<div class="club-row" data-user="${u.id}">
      <span class="avatar" data-tone="${u.tone}">${escapeHTML(initials(u.name))}</span>
      <div><strong>${escapeHTML(u.name)}</strong><small>${escapeHTML([u.roleName, u.clubName].filter(Boolean).join(' · '))}</small></div>
      ${action}
    </div>`;
  }

  async function renderSides() {
    $('#my-clubs').innerHTML = me.clubName
      ? `<div class="club-row">
          <span class="avatar" data-tone="navy">${escapeHTML(initials(me.clubName))}</span>
          <div><strong>${escapeHTML(me.clubName)}</strong><small>${escapeHTML(me.roleName)}</small></div>
        </div>`
      : '<small>Aucun club.</small>';

    loadUpcoming();

    try {
      const [friends, incoming, sent, myPosts] = await Promise.all([
        Api.get('/friends'),
        Api.get('/friends/requests'),
        Api.get('/friends/requests/sent'),
        Api.get(`/users/${me.id}/posts?limit=100`),
      ]);

      $('#stat-clubs').textContent = me.allClubs ? 'Tous' : me.clubId ? 1 : 0;
      $('#stat-friends').textContent = friends.length;
      $('#stat-posts').textContent = myPosts.posts.length + (myPosts.nextBefore ? '+' : '');

      friends.forEach((f) => relation.set(f.user.id, 'friend'));
      incoming.forEach((f) => relation.set(f.user.id, 'incoming'));
      sent.forEach((f) => relation.set(f.user.id, 'sent'));
    } catch (err) {
      toast(err.message);
    }
    loadPeople();
  }

  /* ---------- "À venir": events attached to posts I can see ---------- */
  async function loadUpcoming() {
    try {
      const events = await Api.get('/feed/events?limit=4');
      $('#events').innerHTML = events.length ? events.map((e) => {
        const d = new Date(e.startsAt);
        const month = d.toLocaleDateString('fr-FR', { month: 'short' }).replace('.', '').toUpperCase();
        const time = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
        return `<div class="event-row" data-post="${e.postId}" title="Voir la publication">
          <div class="event-date"><b>${String(d.getDate()).padStart(2, '0')}</b><small>${escapeHTML(month)}</small></div>
          <div><strong>${escapeHTML(e.title)}</strong><small>${escapeHTML([time, e.place].filter(Boolean).join(' · '))}</small></div>
        </div>`;
      }).join('') : '<small>Aucun événement à venir. Ajoutez-en un avec l\'icône 📅.</small>';
    } catch (err) {
      $('#events').innerHTML = `<small>${escapeHTML(err.message)}</small>`;
    }
  }

  function onUpcomingClick(e) {
    const row = e.target.closest('[data-post]');
    if (!row) return;
    const post = $(`.post[data-id="${row.dataset.post}"]`);
    if (post) post.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'center' });
    else toast('Publication plus ancienne : faites défiler le feed');
  }

  /* ---------- People: every member, searchable ---------- */
  const relation = new Map(); // userId -> 'friend' | 'incoming' | 'sent'
  const ACTION = {
    incoming: '<button class="join-btn" data-act="accept">Accepter</button>',
    sent: '<button class="join-btn is-joined" disabled>Envoyée ✓</button>',
    friend: '<button class="join-btn is-joined" disabled>Ami ✓</button>',
    none: '<button class="join-btn" data-act="add">Ajouter</button>',
  };
  const ORDER = { incoming: 0, none: 1, sent: 2, friend: 3 };
  let peopleQuery = '';

  async function loadPeople() {
    const q = peopleQuery;
    const list = $('#suggestions');
    try {
      const users = await Api.get(`/users?limit=100${q ? '&search=' + encodeURIComponent(q) : ''}`);
      if (q !== peopleQuery) return; // a newer search is running
      const people = users
        .filter((u) => u.id !== me.id)
        .map((u) => ({ u: AuthService.toUser(u), rel: relation.get(u.id) || 'none' }))
        .sort((a, b) => ORDER[a.rel] - ORDER[b.rel]);
      $('#people-count').textContent = people.length;
      list.innerHTML = people.length
        ? people.map(({ u, rel }) => personRow(u, ACTION[rel])).join('')
        : `<small>${q ? 'Aucun membre ne correspond.' : 'Aucun autre membre.'}</small>`;
    } catch (err) {
      list.innerHTML = `<small>${escapeHTML(err.message)}</small>`;
    }
  }

  function initPeopleSearch() {
    let t;
    $('#people-search').addEventListener('input', (e) => {
      clearTimeout(t);
      t = setTimeout(() => { peopleQuery = e.target.value.trim(); loadPeople(); }, 250);
    });
  }

  async function onSuggestionClick(e) {
    const b = e.target.closest('.join-btn');
    if (!b || b.disabled) return;
    const userId = Number(b.closest('[data-user]').dataset.user);
    b.disabled = true;
    try {
      if (b.dataset.act === 'accept') {
        await Api.post(`/friends/${userId}/accept`);
        relation.set(userId, 'friend');
        b.textContent = 'Ami ✓';
        const n = $('#stat-friends');
        n.textContent = Number(n.textContent) + 1;
      } else {
        const f = await Api.post(`/friends/${userId}`);
        relation.set(userId, f.status === 'accepted' ? 'friend' : 'sent');
        b.textContent = f.status === 'accepted' ? 'Ami ✓' : 'Envoyée ✓';
      }
      b.classList.add('is-joined');
    } catch (err) {
      b.disabled = false;
      toast(err.message);
    }
  }

  /* ---------- Interactions ---------- */
  function burst(btn) {
    if (reduced) return;
    for (let k = 0; k < 8; k++) {
      const s = document.createElement('i');
      s.className = 'burst';
      s.style.setProperty('--a', `${k * 45}deg`);
      s.style.background = k % 2 ? 'var(--coral)' : 'var(--cyan)';
      const svg = btn.querySelector('svg');
      s.style.left = svg.offsetLeft + svg.offsetWidth / 2 + 'px';
      btn.appendChild(s);
      setTimeout(() => s.remove(), 700);
    }
  }

  async function onPostClick(e) {
    if (e.target.closest('#load-more')) {
      e.target.closest('#load-more').disabled = true;
      return loadFeed(true);
    }
    const article = e.target.closest('.post');
    if (!article) return;
    const post = posts.find((p) => p.id === Number(article.dataset.id));
    const btn = e.target.closest('[data-action]');
    if (!btn) return;

    if (btn.dataset.action === 'like') {
      if (btn.disabled) return;
      btn.disabled = true;
      try {
        const s = post.liked ? await Api.del(`/posts/${post.id}/like`) : await Api.post(`/posts/${post.id}/like`);
        post.liked = s.liked;
        post.likes = s.likeCount;
        btn.classList.toggle('is-liked', post.liked);
        btn.setAttribute('aria-pressed', String(post.liked));
        $('[data-likes]', article).textContent = `${post.likes} j'aime`;
        btn.classList.remove('pop'); void btn.offsetWidth; btn.classList.add('pop');
        if (post.liked) burst(btn);
      } catch (err) {
        toast(err.message);
      } finally {
        btn.disabled = false;
      }
    }
    if (btn.dataset.action === 'comment') {
      const box = $('.comments', article);
      const open = box.classList.toggle('is-open');
      btn.setAttribute('aria-expanded', String(open));
      if (open && !post.comments) {
        try {
          post.comments = (await Api.get(`/posts/${post.id}/comments`)).map(toComment);
          $('.comments-list', article).innerHTML = post.comments.map(commentHTML).join('');
        } catch (err) { toast(err.message); }
      }
      if (open) setTimeout(() => $('.comment-form input', article).focus(), 250);
    }
    if (btn.dataset.action === 'share') {
      navigator.clipboard?.writeText(`${location.origin}${location.pathname}#post-${post.id}`).catch(() => {});
      toast('Lien copié ✓');
    }
    if (btn.dataset.action === 'delete') {
      if (!confirm('Supprimer cette publication ?')) return;
      try {
        await Api.del(`/posts/${post.id}`);
        posts = posts.filter((p) => p !== post);
        renderPosts();
        toast('Publication supprimée');
      } catch (err) { toast(err.message); }
    }
  }

  async function onCommentSubmit(e) {
    if (!e.target.matches('.comment-form')) return;
    e.preventDefault();
    const article = e.target.closest('.post');
    const input = $('input', e.target);
    const text = input.value.trim();
    if (!text) return;
    const post = posts.find((p) => p.id === Number(article.dataset.id));
    input.disabled = true;
    try {
      const c = toComment(await Api.post(`/posts/${post.id}/comments`, { content: text }));
      (post.comments ||= []).push(c);
      post.commentCount++;
      $('.comments-list', article).insertAdjacentHTML('beforeend', commentHTML(c));
      $('[data-ccount]', article).textContent = countLabel(post.commentCount);
      input.value = '';
    } catch (err) {
      toast(err.message);
    } finally {
      input.disabled = false;
      input.focus();
    }
  }

  function initTabs() {
    const tabs = $('.tabs');
    const ind = $('.tab-ind', tabs);
    const move = (b) => { ind.style.left = b.offsetLeft + 'px'; ind.style.width = b.offsetWidth + 'px'; };
    $$('button', tabs).forEach((b) => b.addEventListener('click', () => {
      $$('button', tabs).forEach((x) => { x.classList.toggle('is-active', x === b); x.setAttribute('aria-selected', String(x === b)); });
      move(b);
      filter = b.dataset.filter;
      renderPosts();
    }));
    requestAnimationFrame(() => move($('button.is-active', tabs)));
    window.addEventListener('resize', () => move($('button.is-active', tabs)));
  }

  /* Visibility selects: id 0 = ALL (everyone) */
  async function initAudience() {
    const fill = (select, items, allLabel) => {
      select.innerHTML = items.map((x) => `<option value="${x.id}">${escapeHTML(x.all ? allLabel : x.name)}</option>`).join('');
    };
    try {
      const [roles, clubs] = await Promise.all([Api.get('/roles'), Api.get('/clubs')]);
      fill($('#composer-role'), roles, 'Tous les rôles');
      fill($('#composer-club'), clubs, 'Tous les clubs');
    } catch (_) { /* keep the "everyone" defaults from the HTML */ }
  }

  /** Resize in the browser (max 1600px, JPEG) so uploads stay small. */
  function resizeImage(file, max = 1600) {
    return new Promise((resolve, reject) => {
      const url = URL.createObjectURL(file);
      const img = new Image();
      img.onload = () => {
        const scale = Math.min(1, max / Math.max(img.width, img.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(img.width * scale);
        canvas.height = Math.round(img.height * scale);
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#fff'; // transparent PNGs get a white background
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        URL.revokeObjectURL(url);
        resolve(canvas.toDataURL('image/jpeg', 0.85));
      };
      img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Image illisible')); };
      img.src = url;
    });
  }

  function initComposer() {
    const form = $('#composer');
    const ta = $('#composer-text');
    const submit = $('#composer-submit');
    const fileInput = $('#composer-file');
    const photoBox = $('#composer-photo');
    const eventBox = $('#composer-event');
    const btnPhoto = $('#btn-photo');
    const btnEvent = $('#btn-event');
    let photo = null; // data URL

    const grow = () => { ta.style.height = 'auto'; ta.style.height = ta.scrollHeight + 'px'; };
    ta.addEventListener('focus', () => form.classList.add('is-open'));
    ta.addEventListener('input', () => { grow(); submit.disabled = !ta.value.trim(); });

    /* Photo */
    function setPhoto(dataUrl) {
      photo = dataUrl;
      photoBox.hidden = !photo;
      $('#composer-photo-img').src = photo || '';
      btnPhoto.classList.toggle('is-on', !!photo);
    }
    btnPhoto.addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', async () => {
      const file = fileInput.files[0];
      fileInput.value = '';
      if (!file) return;
      if (!file.type.startsWith('image/')) return toast('Choisissez une image');
      if (file.size > 20 * 1024 * 1024) return toast('Image trop lourde (20 Mo max)');
      try {
        setPhoto(await resizeImage(file));
        form.classList.add('is-open');
      } catch (err) { toast(err.message); }
    });
    $('#composer-photo-remove').addEventListener('click', () => setPhoto(null));

    /* Event */
    function setEvent(open) {
      eventBox.hidden = !open;
      btnEvent.classList.toggle('is-on', open);
      btnEvent.setAttribute('aria-pressed', String(open));
      if (open) { form.classList.add('is-open'); $('#event-title').focus(); }
      else ['#event-title', '#event-date', '#event-place'].forEach((s) => ($(s).value = ''));
    }
    btnEvent.addEventListener('click', () => setEvent(eventBox.hidden));
    $('#composer-event-remove').addEventListener('click', () => setEvent(false));

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const text = ta.value.trim();
      if (!text) return;

      const event = eventBox.hidden ? null : {
        title: $('#event-title').value.trim(),
        date: $('#event-date').value,
        place: $('#event-place').value.trim(),
      };
      if (event && (!event.title || !event.date)) {
        toast("Ajoutez un titre et une date à l'événement");
        (event.title ? $('#event-date') : $('#event-title')).focus();
        return;
      }

      submit.disabled = true;
      try {
        const p = await Api.post('/posts', {
          content: text,
          visibleRoleId: Number($('#composer-role').value),
          visibleClubId: Number($('#composer-club').value),
          image: photo,
          eventTitle: event?.title || null,
          eventStartsAt: event ? new Date(event.date).toISOString() : null,
          eventPlace: event?.place || null,
        });
        posts.unshift(toPost(p));
        ta.value = ''; grow(); form.classList.remove('is-open');
        setPhoto(null);
        setEvent(false);
        if (p.event) loadUpcoming();
        renderPosts();
        const n = $('#stat-posts');
        if (/^\d+$/.test(n.textContent)) n.textContent = Number(n.textContent) + 1;
        toast('Publication partagée ✓');
      } catch (err) {
        submit.disabled = false;
        toast(err.message);
      }
    });
  }

  function initSearch() {
    const input = $('#search');
    let t;
    input.addEventListener('input', () => {
      clearTimeout(t);
      t = setTimeout(() => { query = input.value.trim().toLowerCase(); renderPosts(); }, 200);
    });
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('#posts').innerHTML = '<div class="panel empty">Chargement…</div>';
    loadFeed();
    renderSides();
    initTabs();
    initAudience();
    initComposer();
    initSearch();
    $('#posts').addEventListener('click', onPostClick);
    $('#posts').addEventListener('submit', onCommentSubmit);
    $('#suggestions').addEventListener('click', onSuggestionClick);
    $('#events').addEventListener('click', onUpcomingClick);
    initPeopleSearch();
  });
})();
