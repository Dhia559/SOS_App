(() => {
  const { $, $$, reduced } = UI;

  /* ---- Manifesto: words light up as you scroll ---- */
  function initManifesto() {
    const root = $('#manifesto-text');
    if (!root) return;
    const words = [];
    const wrap = (node, kind) => {
      [...node.childNodes].forEach((child) => {
        if (child.nodeType === 3) {
          const frag = document.createDocumentFragment();
          child.textContent.split(/(\s+)/).forEach((part) => {
            if (!part) return;
            if (/^\s+$/.test(part)) { frag.appendChild(document.createTextNode(' ')); return; }
            const s = document.createElement('span');
            s.className = 'mw' + (kind ? ' ' + kind : '');
            s.textContent = part;
            words.push(s);
            frag.appendChild(s);
          });
          child.replaceWith(frag);
        } else if (child.nodeType === 1) {
          wrap(child, child.dataset.k);
        }
      });
    };
    wrap(root);

    if (reduced) { words.forEach((w) => w.classList.add('on')); return; }

    let ticking = false;
    const update = () => {
      const r = root.getBoundingClientRect();
      const vh = window.innerHeight;
      const progress = Math.min(Math.max((vh * 0.8 - r.top) / (r.height + vh * 0.35), 0), 1);
      const lit = Math.round(progress * words.length);
      words.forEach((w, i) => w.classList.toggle('on', i < lit));
      ticking = false;
    };
    window.addEventListener('scroll', () => {
      if (!ticking) { ticking = true; requestAnimationFrame(update); }
    }, { passive: true });
    update();
  }

  /* ---- Roles accordion ---- */
  function initAccordion() {
    const items = $$('.acc-item');
    const open = (item) => items.forEach((it) => {
      const on = it === item;
      it.classList.toggle('is-open', on);
      it.setAttribute('aria-selected', String(on));
    });
    items.forEach((it) => {
      it.addEventListener('click', () => open(it));
      if (window.matchMedia('(hover: hover) and (min-width: 761px)').matches) {
        it.addEventListener('mouseenter', () => open(it));
      }
    });
  }

  /* ---- Active link on scroll ---- */
  function initScrollSpy() {
    const links = $$('.nav-links a[href^="#"]');
    const map = links.map((a) => [a, $(a.getAttribute('href'))]).filter(([, s]) => s);
    const obs = new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (!e.isIntersecting) return;
        links.forEach((a) => a.classList.toggle('is-active', a.getAttribute('href') === '#' + e.target.id));
        const active = $('.nav-links a.is-active');
        const ind = $('.nav-indicator');
        if (active && ind) { ind.style.left = active.offsetLeft + 'px'; ind.style.width = active.offsetWidth + 'px'; ind.style.opacity = 1; }
      });
    }, { rootMargin: '-45% 0px -50% 0px' });
    map.forEach(([, s]) => obs.observe(s));
  }

  document.addEventListener('DOMContentLoaded', () => {
    initManifesto();
    initAccordion();
    initScrollSpy();
  });
})();
