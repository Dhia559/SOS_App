/**
 * Realtime — STOMP over WebSocket to the backend (ws://host:8080/ws).
 * Needs @stomp/stompjs (global StompJs) loaded before this file.
 *   onMessage(cb)            <- /user/queue/messages             (MessageDto, for both participants)
 *   onMessageUpdated(cb)     <- /user/queue/messages.updated     (MessageDto)
 *   onMessageDeleted(cb)     <- /user/queue/messages.deleted     ({conversationId, messageId})
 *   onRead(cb)               <- /user/queue/read                 ({conversationId, readerId, count})
 *   onNotification(cb)       <- /user/queue/notifications        (NotificationDto)
 *   onNotificationCount(cb)  <- /user/queue/notifications.count  ({count})
 *   markRead(id)             -> /app/chat.read
 */
const Realtime = (() => {
  const QUEUES = {
    message: '/user/queue/messages',
    messageUpdated: '/user/queue/messages.updated',
    messageDeleted: '/user/queue/messages.deleted',
    read: '/user/queue/read',
    notification: '/user/queue/notifications',
    notificationCount: '/user/queue/notifications.count',
    error: '/user/queue/errors',
  };
  const handlers = Object.fromEntries(Object.keys(QUEUES).map((k) => [k, []]));
  let client = null;

  const emit = (type, frame) => {
    let data;
    try { data = JSON.parse(frame.body); } catch (_) { return; }
    handlers[type].forEach((cb) => cb(data));
  };

  function connect() {
    const token = AuthService.getToken();
    if (client || !token) return;
    if (!window.StompJs) { console.warn('[realtime] @stomp/stompjs not loaded, live updates disabled'); return; }

    client = new StompJs.Client({
      brokerURL: Api.WS_URL,
      connectHeaders: { Authorization: 'Bearer ' + token },
      reconnectDelay: 3000,
    });
    client.onConnect = () => {
      Object.entries(QUEUES).forEach(([type, dest]) => client.subscribe(dest, (f) => emit(type, f)));
    };
    client.onStompError = (f) => console.warn('[realtime]', f.headers.message || 'STOMP error');
    client.activate();
  }

  function markRead(conversationId) {
    if (client?.connected) client.publish({ destination: '/app/chat.read', body: JSON.stringify({ conversationId }) });
  }

  return {
    connect,
    markRead,
    onMessage: (cb) => handlers.message.push(cb),
    onMessageUpdated: (cb) => handlers.messageUpdated.push(cb),
    onMessageDeleted: (cb) => handlers.messageDeleted.push(cb),
    onRead: (cb) => handlers.read.push(cb),
    onNotification: (cb) => handlers.notification.push(cb),
    onNotificationCount: (cb) => handlers.notificationCount.push(cb),
    onError: (cb) => handlers.error.push(cb),
  };
})();
