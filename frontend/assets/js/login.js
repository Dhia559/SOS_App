(() => {
  const { $ } = UI;

  document.addEventListener('DOMContentLoaded', () => {
    const form = $('#login-form');
    const alertBox = $('#form-alert');
    const btnSubmit = $('#btn-submit');

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

    function validate({ email, password }) {
      let ok = true;
      if (!email) { setFieldError('email', "L'e-mail est requis."); ok = false; }
      else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { setFieldError('email', 'Adresse e-mail invalide.'); ok = false; }
      else setFieldError('email', '');
      if (!password) { setFieldError('password', 'Le mot de passe est requis.'); ok = false; }
      else setFieldError('password', '');
      return ok;
    }

    function showAlert(msg, success = false) {
      alertBox.textContent = msg;
      alertBox.classList.toggle('is-success', success);
      alertBox.classList.add('is-visible');
    }
    const hideAlert = () => alertBox.classList.remove('is-visible');

    function setLoading(on) {
      btnSubmit.disabled = on;
      btnSubmit.classList.toggle('is-loading', on);
      $('.btn-label', btnSubmit).textContent = on ? 'Connexion…' : 'Se connecter';
    }

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      hideAlert();
      const payload = {
        email: $('#email').value.trim(),
        password: $('#password').value,
        remember: $('#remember').checked,
      };
      if (!validate(payload)) return;

      setLoading(true);
      try {
        const user = await AuthService.login(payload);
        showAlert(`Bienvenue ${user.firstName} ! Redirection…`, true);
        setTimeout(() => { window.location.href = 'feed.html'; }, 900);
      } catch (err) {
        showAlert(err.message);
        setLoading(false);
      }
    });

    ['email', 'password'].forEach((n) => $(`#${n}`).addEventListener('input', () => {
      const f = $(`[data-field="${n}"]`);
      f.classList.remove('has-error');
      f.querySelector('.field-error').textContent = '';
    }));
  });
})();
