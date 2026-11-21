/* =========================================================
   Core UI — shared by every page
   nav · mobile menu · reveal · split text · magnetic · parallax
   ========================================================= */
const UI = (() => {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];

  /* ---- Navbar: scrolled state + sliding active indicator ---- */
  function initNav() {
    const nav = $('.nav');
    if (!nav) return;
    const onScroll = () => nav.classList.toggle('is-scrolled', window.scrollY > 30);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });

    const links = $('.nav-links');
    const indicator = $('.nav-indicator');
    if (links && indicator) {
      const moveTo = (a) => {
        if (!a) { indicator.style.opacity = 0; return; }
        indicator.style.left = a.offsetLeft + 'px';
        indicator.style.width = a.offsetWidth + 'px';
        indicator.style.opacity = 1;
      };
      const active = () => $('a.is-active', links);
      $$('a', links).forEach((a) => a.addEventListener('mouseenter', () => moveTo(a)));
      links.addEventListener('mouseleave', () => moveTo(active()));
      requestAnimationFrame(() => moveTo(active()));
      window.addEventListener('resize', () => moveTo(active()));
    }

    const burger = $('.burger');
    if (burger) {
      burger.addEventListener('click', () => {
        const open = document.body.classList.toggle('menu-open');
        burger.setAttribute('aria-expanded', String(open));
        document.body.style.overflow = open ? 'hidden' : '';
      });
      $$('.mobile-menu a').forEach((a, i) => {
        a.style.setProperty('--i', i);
        a.addEventListener('click', () => {
          document.body.classList.remove('menu-open');
          document.body.style.overflow = '';
        });
      });
    }
  }

  /* ---- Split headings into masked words ---- */
  function splitText(root = document) {
    $$('.split', root).forEach((el) => {
      if (el.dataset.splitDone) return;
      let i = 0;
      const walk = (node) => {
        [...node.childNodes].forEach((child) => {
          if (child.nodeType === 3) {
            const frag = document.createDocumentFragment();
            child.textContent.split(/(\s+)/).forEach((part) => {
              if (!part) return;
              if (/^\s+$/.test(part)) { frag.appendChild(document.createTextNode(' ')); return; }
              const w = document.createElement('span');
              w.className = 'w';
              const inner = document.createElement('span');
              inner.textContent = part;
              inner.style.setProperty('--i', i++);
              w.appendChild(inner);
              frag.appendChild(w);
            });
            child.replaceWith(frag);
          } else if (child.nodeType === 1 && child.classList.contains('keep')) {
            const w = document.createElement('span');
            w.className = 'w';
            const inner = document.createElement('span');
            inner.style.setProperty('--i', i++);
            child.replaceWith(w);
            inner.appendChild(child);
            w.appendChild(inner);
          } else if (child.nodeType === 1 && child.tagName !== 'BR') {
            walk(child);
          }
        });
      };
      walk(el);
      el.dataset.splitDone = '1';
    });
  }

  /* ---- Scroll reveal ---- */
  let io;
  function observeReveals(root = document) {
    const targets = $$('[data-reveal], .split, .reveal-mask', root).filter((el) => !el.classList.contains('is-in'));
    if (reduced || !('IntersectionObserver' in window)) { targets.forEach((el) => el.classList.add('is-in')); return; }
    io = io || new IntersectionObserver((entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) { e.target.classList.add('is-in'); io.unobserve(e.target); }
      });
    }, { threshold: 0.15, rootMargin: '0px 0px -40px 0px' });
    targets.forEach((el) => io.observe(el));
  }

  /* ---- Magnetic buttons ---- */
  function initMagnetic() {
    if (reduced || window.matchMedia('(hover: none)').matches) return;
    $$('[data-magnetic]').forEach((el) => {
      const strength = Number(el.dataset.magnetic) || 0.35;
      el.addEventListener('mousemove', (e) => {
        const r = el.getBoundingClientRect();
        const x = e.clientX - r.left - r.width / 2;
        const y = e.clientY - r.top - r.height / 2;
        el.style.transform = `translate(${x * strength}px, ${y * strength}px)`;
      });
      el.addEventListener('mouseleave', () => { el.style.transform = ''; });
    });
  }

  /* ---- Parallax on scroll (data-parallax="0.2") + pointer (data-depth) ---- */
  function initParallax() {
    if (reduced) return;
    const scrollEls = $$('[data-parallax]');
    const depthEls = $$('[data-depth]');
    let mx = 0, my = 0, ticking = false;

    const render = () => {
      const vh = window.innerHeight;
      scrollEls.forEach((el) => {
        const r = el.getBoundingClientRect();
        const offset = (r.top + r.height / 2 - vh / 2) * Number(el.dataset.parallax);
        el.style.translate = `0 ${offset.toFixed(1)}px`;
      });
      depthEls.forEach((el) => {
        const d = Number(el.dataset.depth);
        el.style.setProperty('--px', `${(mx * d).toFixed(1)}px`);
        el.style.setProperty('--py', `${(my * d).toFixed(1)}px`);
      });
      ticking = false;
    };
    const request = () => { if (!ticking) { ticking = true; requestAnimationFrame(render); } };
    window.addEventListener('scroll', request, { passive: true });
    if (depthEls.length && window.matchMedia('(hover: hover)').matches) {
      window.addEventListener('mousemove', (e) => {
        mx = (e.clientX / window.innerWidth - 0.5) * 2;
        my = (e.clientY / window.innerHeight - 0.5) * 2;
        request();
      });
    }
    render();
  }

  /* ---- Count-up numbers ---- */
  function initCounters() {
    const els = $$('[data-count]');
    if (!els.length) return;
    const run = (el) => {
      const target = Number(el.dataset.count);
      const suffix = el.dataset.suffix || '';
      if (reduced) { el.textContent = target + suffix; return; }
      const start = performance.now();
      const dur = 1800;
      const step = (now) => {
        const p = Math.min((now - start) / dur, 1);
        const eased = 1 - Math.pow(1 - p, 4);
        el.textContent = Math.round(target * eased) + suffix;
        if (p < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    };
    const obs = new IntersectionObserver((entries) => {
      entries.forEach((e) => { if (e.isIntersecting) { run(e.target); obs.unobserve(e.target); } });
    }, { threshold: 0.6 });
    els.forEach((el) => obs.observe(el));
  }

  /* ---- Page transition curtain on internal links ---- */
  function initPageTransitions() {
    if (reduced) return;
    const curtain = document.createElement('div');
    curtain.className = 'page-curtain';
    document.body.appendChild(curtain);
    document.addEventListener('click', (e) => {
      const a = e.target.closest('a[href$=".html"]');
      if (!a || a.target === '_blank' || e.metaKey || e.ctrlKey) return;
      e.preventDefault();
      curtain.classList.add('is-leaving');
      setTimeout(() => { window.location.href = a.href; }, 450);
    });
    window.addEventListener('pageshow', (e) => { if (e.persisted) curtain.classList.remove('is-leaving'); });
  }

  /* ---- Toast ---- */
  let toastEl, toastTimer;
  function toast(msg) {
    if (!toastEl) {
      toastEl = document.createElement('div');
      toastEl.className = 'toast';
      toastEl.setAttribute('role', 'status');
      document.body.appendChild(toastEl);
    }
    toastEl.textContent = msg;
    requestAnimationFrame(() => toastEl.classList.add('is-visible'));
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toastEl.classList.remove('is-visible'), 2600);
  }

  /* ---- Helpers ---- */
  const escapeHTML = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const initials = (name) => name.split(' ').map((p) => p[0]).slice(0, 2).join('').toUpperCase();

  function init() {
    initNav();
    splitText();
    observeReveals();
    initMagnetic();
    initParallax();
    initCounters();
    initPageTransitions();
    const y = $('[data-year]');
    if (y) y.textContent = new Date().getFullYear();
  }

  document.addEventListener('DOMContentLoaded', init);
  return { $, $$, reduced, splitText, observeReveals, toast, escapeHTML, initials };
})();
