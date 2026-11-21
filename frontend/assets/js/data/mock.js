/**
 * Static data for the parts the backend doesn't provide yet.
 * Everything else (users, posts, friends, chat) comes from the API.
 */
const MockData = (() => {
  // "À venir" panel on the feed: the backend has no events/calendar endpoint.
  const EVENTS = [
    { day: '24', month: 'NOV', title: 'Workshop Git & GitHub', club: 'ISIMM' },
    { day: '28', month: 'NOV', title: 'Assemblée générale', club: 'FSM' },
    { day: '12', month: 'DÉC', title: 'Hackathon 48h', club: 'ENIM' },
  ];

  return { EVENTS };
})();
