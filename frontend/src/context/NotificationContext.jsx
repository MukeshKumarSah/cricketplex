import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useAuth } from './AuthContext';
import API from '../api/axios';
import { WS_URL } from '../api/config';

const NotificationContext = createContext(null);

export function NotificationProvider({ children }) {
  const { user } = useAuth();
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const clientRef = useRef(null);

  const fetchNotifications = useCallback(async () => {
    if (!user) return;
    try {
      const { data } = await API.get('/notifications');
      setNotifications(data);
    } catch {}
  }, [user]);

  const fetchUnreadCount = useCallback(async () => {
    if (!user) return;
    try {
      const { data } = await API.get('/notifications/unread-count');
      setUnreadCount(data.count);
    } catch {}
  }, [user]);

  const markRead = useCallback(async (id) => {
    try {
      await API.post(`/notifications/${id}/read`);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: true } : n))
      );
      setUnreadCount((prev) => Math.max(0, prev - 1));
    } catch {}
  }, []);

  const markAllRead = useCallback(async () => {
    try {
      await API.post('/notifications/read-all');
      setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
      setUnreadCount(0);
    } catch {}
  }, []);

  // WebSocket subscription
  useEffect(() => {
    if (!user) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/notifications/${user.id}`, (frame) => {
          const notification = JSON.parse(frame.body);
          setNotifications((prev) => [notification, ...prev].slice(0, 30));
          setUnreadCount((prev) => prev + 1);
        });
      },
    });
    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [user]);

  // Initial load + poll every 60s for unread count
  useEffect(() => {
    if (!user) return;
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 60000);
    return () => clearInterval(interval);
  }, [user, fetchUnreadCount]);

  return (
    <NotificationContext.Provider
      value={{ notifications, unreadCount, fetchNotifications, markRead, markAllRead }}
    >
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  return useContext(NotificationContext);
}
