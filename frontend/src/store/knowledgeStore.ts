import { create } from 'zustand';
import type { KnowledgeDocument } from '../api/knowledge';
import { fetchDocuments, uploadDocument, deleteDocument, rebuildIndex } from '../api/knowledge';

interface KnowledgeState {
  documents: KnowledgeDocument[];
  loading: boolean;
  message: string | null;

  loadDocuments: () => Promise<void>;
  upload: (file: File) => Promise<void>;
  remove: (id: string) => Promise<void>;
  rebuild: () => Promise<void>;
  clearMessage: () => void;
}

export const useKnowledgeStore = create<KnowledgeState>((set) => ({
  documents: [],
  loading: false,
  message: null,

  loadDocuments: async () => {
    set({ loading: true });
    try {
      const docs = await fetchDocuments();
      set({ documents: docs, loading: false });
    } catch {
      set({ loading: false, message: '加载文档列表失败' });
    }
  },

  upload: async (file: File) => {
    set({ loading: true, message: null });
    try {
      const result = await uploadDocument(file);
      set({ message: result.message, loading: false });
    } catch {
      set({ loading: false, message: '上传失败' });
    }
  },

  remove: async (id: string) => {
    set({ loading: true, message: null });
    try {
      const result = await deleteDocument(id);
      set({ message: result.message, loading: false });
    } catch {
      set({ loading: false, message: '删除失败' });
    }
  },

  rebuild: async () => {
    set({ loading: true, message: null });
    try {
      const result = await rebuildIndex();
      set({ message: result.message, loading: false });
    } catch {
      set({ loading: false, message: '索引重建失败' });
    }
  },

  clearMessage: () => set({ message: null }),
}));
