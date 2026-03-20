// src/hooks/useNotifications.js
import { useState, useEffect, useCallback } from "react";
import { notificationsApi } from "../services/api";

/**
 * Polls /api/notifications/{userId} every 30 seconds.
 * Returns notifications array, unread count, and markRead handler.
 */
export function useNotifications(userId) {
  const [notifications, setNotifications] = useState([]);
  const [loading, setLoading] = useState(false);

  const fetch = useCallback(async () => {
    if (!userId) return;
    try {
      setLoading(true);
      const { data } = await notificationsApi.getByUser(userId);
      setNotifications(data);
    } catch (err) {
      console.error("Failed to fetch notifications", err);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  // Initial load + 30s polling
  useEffect(() => {
    fetch();
    const interval = setInterval(fetch, 30_000);
    return () => clearInterval(interval);
  }, [fetch]);

  const markRead = useCallback(async (notifId) => {
    try {
      await notificationsApi.markRead(notifId);
      setNotifications(prev =>
        prev.map(n => n.id === notifId ? { ...n, read: true } : n)
      );
    } catch (err) {
      console.error("Failed to mark notification read", err);
    }
  }, []);

  const unreadCount = notifications.filter(n => !n.read).length;

  return { notifications, unreadCount, loading, markRead, refresh: fetch };
}
