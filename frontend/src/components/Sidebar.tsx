import { useEffect } from 'react';
import { useChatStore, type ChatMessage } from '../store/chatStore';
import { fetchSessions, fetchMessages, createSession, deleteSession } from '../api/chat';
import type { Conversation } from '../types';

export default function Sidebar() {
  const { sessions, currentSessionId, setSessions, setCurrentSession, clearMessages, addMessage } = useChatStore();

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      const data = await fetchSessions();
      const convs: Conversation[] = data.map((d) => ({
        sessionId: d.SESSION_ID,
        title: d.TITLE,
        createdAt: d.CREATED_AT,
      }));
      setSessions(convs);
    } catch (err) {
      console.error('Failed to load sessions:', err);
    }
  };

  const handleSelectSession = async (sessionId: string) => {
    setCurrentSession(sessionId);
    try {
      const data = await fetchMessages(sessionId);
      clearMessages();
      data.forEach((m) => {
        const msg: ChatMessage = {
          id: m.ID,
          role: m.ROLE === 'ASSISTANT' ? 'assistant' : m.ROLE === 'USER' ? 'user' : 'system',
          content: m.CONTENT,
        };
        addMessage(msg);
      });
    } catch (err) {
      console.error('Failed to load messages:', err);
    }
  };

  const handleNewSession = async () => {
    try {
      const { sessionId } = await createSession();
      await loadSessions();
      setCurrentSession(sessionId);
      clearMessages();
    } catch (err) {
      console.error('Failed to create session:', err);
    }
  };

  const handleDeleteSession = async (sessionId: string, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await deleteSession(sessionId);
      if (currentSessionId === sessionId) {
        setCurrentSession('');
        clearMessages();
      }
      await loadSessions();
    } catch (err) {
      console.error('Failed to delete session:', err);
    }
  };

  return (
    <div className="w-64 bg-gray-50 border-r border-gray-200 flex flex-col h-full">
      <div className="p-4 border-b border-gray-200">
        <button
          onClick={handleNewSession}
          className="w-full py-2 px-4 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
        >
          + 新会话
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {sessions.map((s) => (
          <div
            key={s.sessionId}
            onClick={() => handleSelectSession(s.sessionId)}
            className={`px-4 py-3 cursor-pointer hover:bg-gray-100 transition-colors border-b border-gray-100 ${
              currentSessionId === s.sessionId ? 'bg-blue-50 border-l-2 border-l-blue-600' : ''
            }`}
          >
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-700 truncate flex-1">{s.title}</span>
              <button
                onClick={(e) => handleDeleteSession(s.sessionId, e)}
                className="ml-2 text-gray-400 hover:text-red-500 text-xs opacity-0 hover:opacity-100 transition-opacity"
                title="删除会话"
              >
                ？
              </button>
            </div>
            <span className="text-xs text-gray-400">{s.createdAt?.substring(0, 10)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
