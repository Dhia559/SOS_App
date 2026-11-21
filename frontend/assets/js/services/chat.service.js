/**
 * ChatService — backend chat API (friends only):
 *   GET  /api/friends                          -> getFriends()
 *   GET  /api/conversations                    -> listConversations()
 *   POST /api/conversations/with/:userId       -> openConversation(friendId)
 *   GET  /api/conversations/:id/messages       -> getMessages(id)
 *   POST /api/conversations/:id/messages       -> sendMessage(id, content)
 *   PUT  /api/conversations/:id/read           -> markRead(id)
 *   PUT    /api/conversations/:id/messages/:mid -> editMessage(id, mid, content)   (author only)
 *   DELETE /api/conversations/:id/messages/:mid -> deleteMessage(id, mid)          (author only)
 * Live updates: Realtime.onMessage / onMessageUpdated / onMessageDeleted.
 */
const ChatService = (() => {
  const toMessage = (m) => ({
    id: m.id,
    conversationId: m.conversationId,
    senderId: m.senderId,
    content: m.content,
    createdAt: Date.parse(m.createdAt),
    read: m.read,
  });

  async function getFriends() {
    const friends = await Api.get('/friends');
    return friends.map((f) => AuthService.toUser(f.user));
  }

  async function listConversations() {
    const convs = await Api.get('/conversations');
    return convs
      .map((c) => ({
        id: c.id,
        friend: AuthService.toUser(c.otherUser),
        last: c.lastMessage ? toMessage(c.lastMessage) : null,
        unread: c.unreadCount,
        createdAt: Date.parse(c.createdAt),
      }))
      .sort((a, b) => (b.last?.createdAt || b.createdAt) - (a.last?.createdAt || a.createdAt));
  }

  async function openConversation(friendId) {
    const c = await Api.post(`/conversations/with/${friendId}`);
    return c.id;
  }

  async function getMessages(convId) {
    const messages = await Api.get(`/conversations/${convId}/messages?limit=50`);
    return messages.map(toMessage);
  }

  const markRead = (convId) => Api.put(`/conversations/${convId}/read`);

  async function sendMessage(convId, content) {
    return toMessage(await Api.post(`/conversations/${convId}/messages`, { content }));
  }

  async function editMessage(convId, msgId, content) {
    return toMessage(await Api.put(`/conversations/${convId}/messages/${msgId}`, { content }));
  }

  const deleteMessage = (convId, msgId) => Api.del(`/conversations/${convId}/messages/${msgId}`);

  return { toMessage, getFriends, listConversations, openConversation, getMessages, markRead, sendMessage, editMessage, deleteMessage };
})();
