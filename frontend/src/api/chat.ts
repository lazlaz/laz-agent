const BASE = '/api';

export async function sendMessage(sessionId: string, message: string): Promise<{ messageId: string; streamUrl: string }> {
  const res = await fetch(`${BASE}/chat/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sessionId, message }),
  });
  if (!res.ok) throw new Error(`Send failed: ${res.status}`);
  return res.json();
}

export async function fetchSessions(): Promise<{ SESSION_ID: string; TITLE: string; CREATED_AT: string }[]> {
  const res = await fetch(`${BASE}/sessions`);
  if (!res.ok) throw new Error(`Fetch sessions failed: ${res.status}`);
  return res.json();
}

export async function fetchMessages(sessionId: string): Promise<{ ID: string; ROLE: string; CONTENT: string; METADATA: string; CREATED_AT: string }[]> {
  const res = await fetch(`${BASE}/sessions/${sessionId}/messages`);
  if (!res.ok) throw new Error(`Fetch messages failed: ${res.status}`);
  return res.json();
}

export async function createSession(): Promise<{ sessionId: string }> {
  const res = await fetch(`${BASE}/sessions`, { method: 'POST' });
  if (!res.ok) throw new Error(`Create session failed: ${res.status}`);
  return res.json();
}

export async function deleteSession(sessionId: string): Promise<void> {
  await fetch(`${BASE}/sessions/${sessionId}`, { method: 'DELETE' });
}
