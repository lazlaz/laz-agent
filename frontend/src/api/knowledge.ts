const BASE = '/api/knowledge';

export interface KnowledgeDocument {
  id: string;
  name: string;
  size: number;
  updatedAt: string;
}

export async function uploadDocument(file: File): Promise<{ status: string; filename: string; message: string }> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${BASE}/upload`, { method: 'POST', body: formData });
  if (!res.ok) throw new Error(`Upload failed: ${res.status}`);
  return res.json();
}

export async function fetchDocuments(): Promise<KnowledgeDocument[]> {
  const res = await fetch(`${BASE}/documents`);
  if (!res.ok) throw new Error(`Fetch documents failed: ${res.status}`);
  return res.json();
}

export async function deleteDocument(id: string): Promise<{ status: string; message: string }> {
  const res = await fetch(`${BASE}/documents/${encodeURIComponent(id)}`, { method: 'DELETE' });
  if (!res.ok) throw new Error(`Delete failed: ${res.status}`);
  return res.json();
}

export async function rebuildIndex(): Promise<{ status: string; documentCount: number; message: string }> {
  const res = await fetch(`${BASE}/rebuild`, { method: 'POST' });
  if (!res.ok) throw new Error(`Rebuild failed: ${res.status}`);
  return res.json();
}
