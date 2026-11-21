(() => {
  const { $, $$, escapeHTML } = UI;

  document.addEventListener('DOMContentLoaded', () => {
    const stepRole = $('#step-role');
    const stepForm = $('#step-form');
    const dots = $$('.progress-dots i');
    const roleButtons = $$('.role');
    const btnContinue = $('#btn-continue');
    const pill = $('#role-pill');
    const form = $('#signup-form');
    const alertBox = $('#form-alert');
    const btnSubmit = $('#btn-submit');
    let roleId = null;

    function showStep(step, back = false) {
      [stepRole, stepForm].forEach((s) => {
        s.classList.toggle('is-active', s === step);
        s.classList.toggle('is-back', s === step && back);
      });
      dots.forEach((d, i) => d.classList.toggle('on', i === (step === stepRole ? 0 : 1)));
    }

    /* ---- Step 1: role ---- */
    function selectRole(id) {
      roleId = Number(id);
      roleButtons.forEach((b) => {
        const on = b.dataset.role === id;
        b.classList.toggle('is-selected', on);
        b.setAttribute('aria-checked', String(on));
      });
      btnContinue.disabled = false;
    }
    roleButtons.forEach((b) => {
      b.addEventListener('click', () => selectRole(b.dataset.role));
      b.addEventListener('dblclick', goToForm);
    });

    function goToForm() {
      if (!roleId) return;
      const meta = AuthService.ROLES[roleId];
      $('#role-pill-label').textContent = meta.label;
      pill.style.setProperty('--pill-color', meta.color);
      pill.style.setProperty('--pill-soft', meta.soft);
      hideAlert();
      showStep(stepForm);
      setTimeout(() => $('#firstName').focus(), 300);
    }
    btnContinue.addEventListener('click', goToForm);
    $('#btn-back').addEventListener('click', () => showStep(stepRole, true));
    $('#btn-change-role').addEventListener('click', () => showStep(stepRole, true));

    /* ---- Clubs (id 0 = ALL is only a post target) ---- */
    Api.get('/clubs')
      .then((clubs) => {
        $('#clubId').insertAdjacentHTML('beforeend', clubs
          .filter((c) => !c.all)
          .map((c) => `<option value="${c.id}">${escapeHTML(c.name)}</option>`).join(''));
      })
      .catch((err) => showAlert(err.message));

    /* ---- Step 2: details ---- */
    $('#toggle-pass').addEventListener('click', (e) => {
      const input = $('#password');
      const hidden = input.type === 'password';
      input.type = hidden ? 'text' : 'password';
      e.currentTarget.textContent = hidden ? 'Masquer' : 'Afficher';
    });

    function setFieldError(name, msg) {
      const field = $(`[data-field="${name}"]`);
      field.classList.remove('has-error');
      if (msg) { void field.offsetWidth; field.classList.add('has-error'); }
      field.querySelector('.field-error').textContent = msg || '';
    }

    function validate(p) {
      const errors = {
        firstName: !p.firstName && 'Le prénom est requis.',
        lastName: !p.lastName && 'Le nom est requis.',
        email: !p.email ? "L'e-mail est requis." : !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(p.email) && 'Adresse e-mail invalide.',
        password: !p.password ? 'Le mot de passe est requis.' : p.password.length < 6 && '6 caractères minimum.',
        clubId: !p.clubId && 'Choisissez votre club.',
      };
      Object.entries(errors).forEach(([k, msg]) => setFieldError(k, msg || ''));
      return Object.values(errors).every((m) => !m);
    }

    function showAlert(msg, success = false) {
      alertBox.textContent = msg;
      alertBox.classList.toggle('is-success', success);
      alertBox.classList.add('is-visible');
    }
    function hideAlert() { alertBox.classList.remove('is-visible'); }

    function setLoading(on) {
      btnSubmit.disabled = on;
      btnSubmit.classList.toggle('is-loading', on);
      $('.btn-label', btnSubmit).textContent = on ? 'Création…' : 'Créer mon compte';
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideAlert();
      const payload = {
        firstName: $('#firstName').value.trim(),
        lastName: $('#lastName').value.trim(),
        email: $('#email').value.trim(),
        password: $('#password').value,
        roleId,
        clubId: Number($('#clubId').value) || null,
      };
      if (!validate(payload)) return;

      setLoading(true);
      try {
        const user = await AuthService.register({ ...payload, remember: true });
        showAlert(`Bienvenue ${user.firstName} ! Redirection…`, true);
        setTimeout(() => { window.location.href = 'feed.html'; }, 900);
      } catch (err) {
        showAlert(err.status === 409 ? 'Un compte existe déjà avec cet e-mail.' : err.message);
        setLoading(false);
      }
    });

    ['firstName', 'lastName', 'email', 'password', 'clubId'].forEach((n) => $(`#${n}`).addEventListener('input', () => {
      const f = $(`[data-field="${n}"]`);
      f.classList.remove('has-error');
      f.querySelector('.field-error').textContent = '';
    }));
  });
})();
