/**
 * AuthService — login / signup against the backend and session storage.
 *   POST /api/auth/login     {email, password}
 *   POST /api/auth/register  {firstName, lastName, email, password, roleId, clubId, birthday?, sex?}
 * Pages only depend on this interface.
 */
const AuthService = (() => {
  const TOKEN_KEY = 'cs_token';
  const USER_KEY = 'cs_user';

  // Same ids and names as the `roles` table (id 0 = ALL is only a post target)
  const ROLES = {
    1: { name: 'President Association', label: "Président d'association", chip: 'chip--asso-board', color: '#1D3768', soft: 'rgba(29,55,104,.12)' },
    2: { name: 'Membre Association', label: "Membre d'association", chip: 'chip--asso', color: '#4a6aa5', soft: 'rgba(74,106,165,.14)' },
    3: { name: 'President Club', label: 'Président de club', chip: 'chip--bureau', color: '#b93348', soft: 'rgba(226,85,106,.15)' },
    4: { name: 'Membre Club', label: 'Membre de club', chip: 'chip--club', color: '#0679a1', soft: 'rgba(8,169,224,.15)' },
  };

  const TONES = ['cyan', 'coral', 'navy', 'light'];

  /** The backend sends roleId on full users but only roleName on short ones (post authors, friends...). */
  const roleIdOf = (u) => u.roleId ?? Number(Object.keys(ROLES).find((id) => ROLES[id].name === u.roleName)) ?? null;

  /** UserDto or UserSummary from the backend -> the shape the pages use. */
  function toUser(u) {
    if (!u) return null;
    const roleId = roleIdOf(u) || null;
    return {
      id: u.id,
      firstName: u.firstName,
      lastName: u.lastName,
      name: `${u.firstName} ${u.lastName}`.trim(),
      email: u.email,
      roleId,
      roleName: roleId ? ROLES[roleId].label : (u.roleName || ''),
      clubId: u.clubId ?? null,
      clubName: u.clubName || '',
      tone: TONES[u.id % TONES.length],
    };
  }

  function save(data, remember) {
    const user = toUser(data.user);
    logout();
    const storage = remember ? localStorage : sessionStorage;
    try {
      storage.setItem(TOKEN_KEY, data.token);
      storage.setItem(USER_KEY, JSON.stringify(user));
    } catch (_) { /* storage unavailable */ }
    return user;
  }

  async function login({ email, password, remember }) {
    return save(await Api.post('/auth/login', { email, password }), remember);
  }

  async function register({ remember, ...fields }) {
    return save(await Api.post('/auth/register', fields), remember);
  }

  function logout() {
    try {
      [localStorage, sessionStorage].forEach((s) => { s.removeItem(TOKEN_KEY); s.removeItem(USER_KEY); });
    } catch (_) {}
  }

  function getToken() {
    try { return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY); } catch (_) { return null; }
  }

  function getUser() {
    try {
      const raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_) { return null; }
  }

  return { ROLES, toUser, login, register, logout, getToken, getUser };
})();
