import { useState } from 'react';
import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';
import KnowledgeManager from './components/KnowledgeManager';

type Page = 'chat' | 'knowledge';

export default function App() {
  const [page, setPage] = useState<Page>('chat');

  return (
    <div className="h-screen flex">
      {page === 'chat' ? (
        <>
          <Sidebar onNavigateKnowledge={() => setPage('knowledge')} />
          <ChatArea />
        </>
      ) : (
        <>
          <div className="w-16 bg-gray-900 flex flex-col items-center pt-4 gap-2">
            <button
              onClick={() => setPage('chat')}
              className="text-white text-xs p-2 hover:bg-gray-700 rounded w-12 text-center"
              title="返回聊天"
            >
              💬
            </button>
          </div>
          <KnowledgeManager />
        </>
      )}
    </div>
  );
}
