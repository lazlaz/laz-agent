import MessageList from './MessageList';
import InputBar from './InputBar';

export default function ChatArea() {
  return (
    <div className="flex-1 flex flex-col bg-gray-50">
      <MessageList />
      <InputBar />
    </div>
  );
}
