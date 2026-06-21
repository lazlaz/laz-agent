import { useEffect, useRef, useState } from 'react';
import { useKnowledgeStore } from '../store/knowledgeStore';

export default function KnowledgeManager() {
  const { documents, loading, message, loadDocuments, upload, remove, rebuild, clearMessage } = useKnowledgeStore();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);

  useEffect(() => { loadDocuments(); }, [loadDocuments]);

  useEffect(() => {
    if (message) {
      const timer = setTimeout(clearMessage, 5000);
      return () => clearTimeout(timer);
    }
  }, [message, clearMessage]);

  const handleUpload = async () => {
    const file = fileInputRef.current?.files?.[0];
    if (!file) return;
    setUploading(true);
    await upload(file);
    setUploading(false);
    if (fileInputRef.current) fileInputRef.current.value = '';
    await loadDocuments();
  };

  const handleDelete = async (id: string) => {
    if (!confirm(`确定删除 "${id}"?`)) return;
    await remove(id);
    await loadDocuments();
  };

  const handleRebuild = async () => {
    if (!confirm('确定重建索引? 这将清空所有现有索引并重新构建。')) return;
    await rebuild();
    await loadDocuments();
  };

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* 顶部栏 */}
      <div className="bg-white border-b px-6 py-4">
        <h1 className="text-xl font-bold text-gray-800">知识库管理</h1>
        <p className="text-sm text-gray-500 mt-1">
          管理售后政策文档，上传后需重建索引才能生效
        </p>
      </div>

      {/* 消息提示 */}
      {message && (
        <div className="mx-6 mt-4 px-4 py-3 bg-blue-50 border border-blue-200 rounded-lg text-blue-700 text-sm">
          {message}
        </div>
      )}

      {/* 上传区 */}
      <div className="mx-6 mt-4 p-4 bg-white rounded-lg border">
        <div className="flex items-center gap-3">
          <span className="text-gray-600 text-sm font-medium">📎 上传文档</span>
          <input
            ref={fileInputRef}
            type="file"
            accept=".md,.txt"
            className="text-sm text-gray-500 file:mr-3 file:py-1.5 file:px-3 file:border-0 file:text-sm file:font-medium file:bg-gray-100 file:text-gray-700 hover:file:bg-gray-200 file:rounded"
          />
          <button
            onClick={handleUpload}
            disabled={uploading}
            className="px-4 py-1.5 bg-blue-600 text-white text-sm rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {uploading ? '上传中...' : '上传'}
          </button>
        </div>
        <p className="text-xs text-gray-400 mt-2">支持 .md / .txt 格式</p>
      </div>

      {/* 文档列表 */}
      <div className="mx-6 mt-4 p-4 bg-white rounded-lg border flex-1 overflow-auto">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-sm font-semibold text-gray-700">
            已上传文档 ({documents.length} 篇)
          </h2>
          <button
            onClick={handleRebuild}
            disabled={loading}
            className="px-3 py-1.5 text-sm bg-amber-500 text-white rounded hover:bg-amber-600 disabled:opacity-50"
          >
            🔃 {loading ? '处理中...' : '重建索引'}
          </button>
        </div>

        {documents.length === 0 ? (
          <p className="text-gray-400 text-sm text-center py-8">暂无文档，请上传 .md 政策文件</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-gray-500 border-b">
                <th className="pb-2 font-medium">文档名</th>
                <th className="pb-2 font-medium">大小</th>
                <th className="pb-2 font-medium">更新时间</th>
                <th className="pb-2 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => (
                <tr key={doc.id} className="border-b last:border-0 hover:bg-gray-50">
                  <td className="py-2.5 text-gray-800 font-medium">{doc.name}</td>
                  <td className="py-2.5 text-gray-500">{(doc.size / 1024).toFixed(1)} KB</td>
                  <td className="py-2.5 text-gray-500">{doc.updatedAt}</td>
                  <td className="py-2.5">
                    <button
                      onClick={() => handleDelete(doc.id)}
                      className="text-red-500 hover:text-red-700 text-sm"
                    >
                      🗑 删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
