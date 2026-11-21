/**
 * Static data for the parts the backend doesn't provide yet.
 * Everything else (users, posts, friends, chat) comes from the API.
 */
const MockData = (() => {
  // "À venir" panel on the feed: the backend has no events/calendar endpoint.
  const EVENTS = [
    { day: '28', month: 'NOV', title: 'Assemblée générale', club: 'Association SOS' },
    { day: '05', month: 'DÉC', title: 'Journée du bénévolat', club: 'Village SOS · Tous les clubs' },
    { day: '14', month: 'DÉC', title: 'Collecte de jouets', club: 'Village SOS · Tous les clubs' },
  ];

  return { EVENTS };
})();
