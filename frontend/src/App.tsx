import Sidebar from './components/Sidebar';
import ChatArea from './components/ChatArea';

export default function App() {
  return (
    <div className="h-screen flex">
      <Sidebar />
      <ChatArea />
    </div>
  );
}
