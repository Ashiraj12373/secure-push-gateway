import { useState, useRef, useEffect } from "react";
import { useNotifications } from "../hooks/useNotifications";

export default function NotificationBell({ userId }) {
  const { notifications, unreadCount, markRead } = useNotifications(userId);
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClick(e) {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen((o) => !o)}
        className="relative p-2 text-gray-400 hover:text-gray-600 focus:outline-none"
        aria-label="Notifications"
      >
        <span className="text-xl leading-none">🔔</span>
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 bg-red-500 text-white text-xs font-bold rounded-full min-w-[18px] h-[18px] flex items-center justify-center px-1">
            {unreadCount > 9 ? "9+" : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 bg-white shadow-xl rounded-xl border border-gray-200 z-50 overflow-hidden">
          <div className="px-4 py-3 border-b bg-gray-50 flex items-center justify-between">
            <span className="font-semibold text-sm text-gray-700">Notifications</span>
            {unreadCount > 0 && (
              <span className="text-xs text-indigo-600 font-medium">{unreadCount} unread</span>
            )}
          </div>
          <div className="max-h-80 overflow-y-auto divide-y">
            {notifications.length === 0 ? (
              <p className="text-center text-gray-400 text-sm py-8">No notifications yet</p>
            ) : (
              notifications.slice(0, 20).map((n) => (
                <div
                  key={n.id}
                  onClick={() => !n.read && markRead(n.id)}
                  className={`px-4 py-3 cursor-pointer hover:bg-gray-50 transition-colors ${
                    !n.read ? "bg-indigo-50 border-l-2 border-indigo-400" : ""
                  }`}
                >
                  <p className="text-sm text-gray-800 leading-snug">{n.message}</p>
                  {n.createdAt && (
                    <p className="text-xs text-gray-400 mt-1">
                      {new Date(n.createdAt).toLocaleString()}
                    </p>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
