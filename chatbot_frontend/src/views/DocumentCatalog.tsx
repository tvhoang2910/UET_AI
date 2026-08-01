import React, { useState, useEffect, useCallback } from 'react';
import {
    FileText,
    UploadCloud,
    RefreshCw,
    CheckCircle,
    AlertTriangle,
    Globe,
    XCircle,
    Trash2,
} from 'lucide-react';
import type { DocumentStatus } from '../types';
import { api } from '../lib/api';

interface DocumentCatalogProps {
    triggerNotification: (message: string, type?: 'success' | 'error') => void;
}

export default function DocumentCatalog({ triggerNotification }: DocumentCatalogProps) {
    const [documents, setDocuments] = useState<DocumentStatus[]>([]);
    const [uploadTitle, setUploadTitle] = useState('');
    const [uploadFile, setUploadFile] = useState<File | null>(null);
    const [uploadIsPublic, setUploadIsPublic] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const [uploadError, setUploadError] = useState<string | null>(null);
    const [isDragOver, setIsDragOver] = useState(false);

    const fetchDocuments = useCallback(async () => {
        try {
            const response = await api.get('/api/documents');
            setDocuments(response.data);
        } catch (err) {
            console.error('Failed to retrieve catalog of documents', err);
        }
    }, []);

    useEffect(() => {
        fetchDocuments();
    }, [fetchDocuments]);

    // Auto-polling: nếu còn job PENDING/PROCESSING thì fetch mỗi 2s
    useEffect(() => {
        const hasProcessing = documents.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING');
        if (!hasProcessing) return;
        const interval = setInterval(() => {
            fetchDocuments();
        }, 2000);
        return () => clearInterval(interval);
    }, [documents, fetchDocuments]);

    const handleFileUploadDrag = (e: React.DragEvent) => {
        e.preventDefault();
        setIsDragOver(true);
    };

    const handleFileUploadLeave = () => {
        setIsDragOver(false);
    };

    const handleFileDrop = (e: React.DragEvent) => {
        e.preventDefault();
        setIsDragOver(false);
        if (e.dataTransfer.files && e.dataTransfer.files[0]) {
            const file = e.dataTransfer.files[0];
            if (file.type !== 'application/pdf') {
                setUploadError('Hệ thống hiện tại chỉ hỗ trợ phân tích định dạng văn bản PDF.');
                return;
            }
            setUploadFile(file);
            if (!uploadTitle) {
                setUploadTitle(file.name.replace(/\.[^/.]+$/, ''));
            }
            setUploadError(null);
        }
    };

    const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        if (e.target.files && e.target.files[0]) {
            const file = e.target.files[0];
            setUploadFile(file);
            if (!uploadTitle) {
                setUploadTitle(file.name.replace(/\.[^/.]+$/, ''));
            }
            setUploadError(null);
        }
    };

    const handleUploadDocument = async (e: React.FormEvent) => {
        e.preventDefault();
        setUploadError(null);

        if (!uploadFile) {
            setUploadError('Vui lòng chọn hoặc kéo thả tệp tài liệu PDF.');
            return;
        }
        if (!uploadTitle.trim()) {
            setUploadError('Vui lòng nhập tiêu đề lưu trữ cho học liệu.');
            return;
        }

        const formData = new FormData();
        formData.append('file', uploadFile);
        formData.append('title', uploadTitle);
        formData.append('isPublic', String(uploadIsPublic));

        setIsUploading(true);

        try {
            await api.post('/api/documents/upload', formData, {
                headers: { 'Content-Type': 'multipart/form-data' },
            });
            triggerNotification('Tải học liệu thành công! Tiến trình trích xuất vector semantic bắt đầu chạy ngầm.', 'success');
            setUploadFile(null);
            setUploadTitle('');
            setUploadIsPublic(false);
            fetchDocuments();
        } catch (err: any) {
            const errMsg = err.response?.data?.detail || err.response?.data?.message || 'Có lỗi xảy ra khi tải lên.';
            setUploadError(errMsg);
        } finally {
            setIsUploading(false);
        }
    };

    const handleDeleteDocument = async (documentId: string) => {
        if (
            !window.confirm(
                'Hành động này sẽ xóa dữ liệu PDF và thu hồi toàn bộ vector liên quan khỏi cơ sở dữ liệu Qdrant. Tiếp tục?'
            )
        )
            return;

        try {
            await api.delete(`/api/documents/${documentId}`);
            setDocuments((prev) => prev.filter((d) => d.documentId !== documentId));
            triggerNotification('Xóa tệp dữ liệu & hệ cơ sở tri thức thành công!', 'success');
        } catch (err) {
            triggerNotification('Không thể xóa tài liệu này.', 'error');
        }
    };

    const handleReindexDocument = async (documentId: string) => {
        try {
            triggerNotification('Bắt đầu quy trình tái chỉ mục vector dữ liệu...', 'success');
            await api.post(`/api/documents/${documentId}/reindex`);
            fetchDocuments();
        } catch (err) {
            triggerNotification('Có lỗi xảy ra trong quá trình tái chỉ mục.', 'error');
        }
    };

    const formatBytes = (bytes: number) => {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    return (
        <div className="p-6 md:p-8 max-w-6xl mx-auto space-y-8 animate-fade-in">
            {/* Header Panel */}
            <div className="border-b border-slate-800/40 pb-5">
                <h1 className="text-2xl font-bold text-slate-100 flex items-center gap-2">
                    <FileText className="w-7 h-7 text-violet-400" />
                    Quản lý kho học liệu RAG
                </h1>
                <p className="text-slate-400 text-sm mt-1">
                    Đăng tải, lập chỉ mục và quản lý tài liệu PDF để làm hệ cơ sở tri thức cho mô hình AI đối sánh vector.
                </p>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
                {/* PDF file Upload workspace */}
                <div className="lg:col-span-5 bg-slate-900/20 border border-slate-800/60 rounded-2xl p-6 space-y-6">
                    <h3 className="text-md font-bold text-slate-200">Thêm tài liệu mới</h3>

                    {uploadError && (
                        <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-2">
                            <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                            <span>{uploadError}</span>
                        </div>
                    )}

                    <form onSubmit={handleUploadDocument} className="space-y-4">
                        {/* Drag-and-drop workspace */}
                        <div
                            onDragOver={handleFileUploadDrag}
                            onDragLeave={handleFileUploadLeave}
                            onDrop={handleFileDrop}
                            className={`border-2 border-dashed rounded-xl p-6 text-center transition-all cursor-pointer relative ${
                                isDragOver
                                    ? 'border-violet-500 bg-violet-500/10'
                                    : 'border-slate-800 hover:border-slate-700 bg-slate-950/40'
                            }`}
                        >
                            <input
                                type="file"
                                accept=".pdf"
                                id="document-upload"
                                onChange={handleFileChange}
                                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
                            />
                            <div className="flex flex-col items-center justify-center space-y-2">
                                <UploadCloud className="w-10 h-10 text-slate-500 animate-pulse-soft" />
                                {uploadFile ? (
                                    <div className="space-y-1">
                                        <span className="text-sm font-semibold text-violet-300 block max-w-[240px] truncate mx-auto">
                                            {uploadFile.name}
                                        </span>
                                        <span className="text-[10px] text-slate-500 block font-mono">
                                            {formatBytes(uploadFile.size)}
                                        </span>
                                    </div>
                                ) : (
                                    <>
                                        <span className="text-xs font-semibold text-slate-300">
                                            Nhấp để chọn hoặc kéo thả tệp PDF vào đây
                                        </span>
                                        <span className="text-[10px] text-slate-500 block">
                                            Dung lượng tối đa hỗ trợ lên đến 50MB
                                        </span>
                                    </>
                                )}
                            </div>
                        </div>

                        <div>
                            <label className="block text-slate-300 text-xs font-medium mb-1">Tiêu đề lưu trữ</label>
                            <input
                                type="text"
                                value={uploadTitle}
                                onChange={(e) => setUploadTitle(e.target.value)}
                                placeholder="VD: Cú pháp và cách tự học tiếng Việt..."
                                className="w-full glass-input px-3.5 py-2 rounded-lg text-sm"
                            />
                        </div>

                        <div className="flex items-start gap-3 bg-slate-950/20 border border-slate-800/40 p-3 rounded-lg">
                            <input
                                type="checkbox"
                                id="is-public"
                                checked={uploadIsPublic}
                                onChange={(e) => setUploadIsPublic(e.target.checked)}
                                className="mt-1 rounded border-slate-800 text-violet-600 focus:ring-violet-500 bg-slate-950"
                            />
                            <label htmlFor="is-public" className="cursor-pointer">
                                <span className="block text-xs font-semibold text-slate-200">
                                    Chia sẻ học liệu công khai (isPublic)
                                </span>
                                <span className="block text-[10px] text-slate-500">
                                    Cho phép các sinh viên khác trong trường có quyền đồng tra cứu dữ liệu.
                                </span>
                            </label>
                        </div>

                        <button
                            type="submit"
                            className="w-full py-2.5 rounded-lg glass-button-primary text-sm flex items-center justify-center gap-2"
                            disabled={isUploading}
                        >
                            {isUploading ? (
                                <>
                                    <RefreshCw className="w-4 h-4 animate-spin" />
                                    <span>Đang tải lên & lập chỉ mục...</span>
                                </>
                            ) : (
                                <>
                                    <CheckCircle className="w-4 h-4" />
                                    <span>Tải tệp & khởi tạo Vector</span>
                                </>
                            )}
                        </button>
                    </form>
                </div>

                {/* DB document table view */}
                <div className="lg:col-span-7 bg-slate-900/20 border border-slate-800/60 rounded-2xl p-6">
                    <div className="flex items-center justify-between mb-4">
                        <h3 className="text-md font-bold text-slate-200">Danh mục tệp hệ thống</h3>
                        <button
                            onClick={fetchDocuments}
                            className="p-1.5 rounded-md hover:bg-slate-800 text-slate-400"
                            title="Tải lại"
                            type="button"
                        >
                            <RefreshCw className="w-3.5 h-3.5" />
                        </button>
                    </div>

                    {documents.length === 0 ? (
                        <div className="text-center py-16 bg-slate-950/10 rounded-xl border border-dashed border-slate-800">
                            <FileText className="w-12 h-12 text-slate-600 mx-auto mb-3" />
                            <p className="text-xs text-slate-500">Chưa tìm thấy học liệu được cấu hình trong hệ thống.</p>
                        </div>
                    ) : (
                        <div className="space-y-3">
                            {documents.map((doc) => (
                                <div
                                    key={doc.documentId}
                                    className="bg-slate-950/40 rounded-xl border border-slate-800/60 p-4 flex flex-col md:flex-row justify-between gap-4"
                                >
                                    <div className="space-y-2 flex-1 min-w-0">
                                        <div className="flex items-center gap-2 flex-wrap">
                                            <span
                                                className="font-bold text-slate-200 truncate block text-sm"
                                                title={doc.title}
                                            >
                                                {doc.title}
                                            </span>
                                            <span className="flex items-center gap-1 text-[9px] px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
                                                <Globe className="w-2.5 h-2.5" />
                                                {doc.isPublic ? 'Public' : 'Private'}
                                            </span>
                                        </div>

                                        <p
                                            className="text-[11px] text-slate-500 truncate"
                                            title={doc.originalFilename}
                                        >
                                            Tên tệp gốc: {doc.originalFilename}
                                        </p>

                                        <div className="flex items-center gap-4 text-[10px] text-slate-400 font-mono flex-wrap">
                                            <span>Dung lượng: {formatBytes(doc.fileSizeBytes)}</span>
                                            <span>•</span>
                                            <span>Đã tách: {doc.chunkCount} đoạn Vector</span>
                                        </div>

                                        {doc.status === 'FAILED' && doc.errorMessage && (
                                            <div className="p-2 rounded bg-red-950/20 border border-red-500/20 text-red-400 text-[10px] leading-relaxed">
                                                <strong>Lỗi xử lý:</strong> {doc.errorMessage}
                                            </div>
                                        )}
                                    </div>

                                    <div className="flex items-center justify-between md:justify-end gap-3 flex-shrink-0">
                                        <div>
                                            {doc.status === 'DONE' && (
                                                <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-semibold bg-emerald-500/10 border border-emerald-500/20 text-emerald-400">
                                                    <CheckCircle className="w-3 h-3" />
                                                    Sẵn sàng
                                                </span>
                                            )}
                                            {doc.status === 'PROCESSING' && (
                                                <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-semibold bg-violet-500/10 border border-violet-500/20 text-violet-400 animate-pulse">
                                                    <RefreshCw className="w-3 h-3 animate-spin" />
                                                    Đang xử lý... ({doc.chunkCount} chunks)
                                                </span>
                                            )}
                                            {doc.status === 'PENDING' && (
                                                <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-semibold bg-amber-500/10 border border-amber-500/20 text-amber-400">
                                                    <RefreshCw className="w-3 h-3" />
                                                    Đang xếp hàng...
                                                </span>
                                            )}
                                            {doc.status === 'FAILED' && (
                                                <span className="flex items-center gap-1 px-2.5 py-1 rounded-full text-[10px] font-semibold bg-red-500/10 border border-red-500/20 text-red-400">
                                                    <XCircle className="w-3 h-3" />
                                                    Thất bại
                                                </span>
                                            )}
                                        </div>

                                        <div className="flex items-center gap-1.5">
                                            {doc.reindexable && (
                                                <button
                                                    onClick={() => handleReindexDocument(doc.documentId)}
                                                    className="p-1.5 rounded bg-slate-900 border border-slate-800 text-slate-400 hover:text-violet-400 hover:border-violet-500/40 transition-all"
                                                    title="Tái chỉ mục vector"
                                                    type="button"
                                                >
                                                    <RefreshCw className="w-3.5 h-3.5" />
                                                </button>
                                            )}
                                            <button
                                                onClick={() => handleDeleteDocument(doc.documentId)}
                                                className="p-1.5 rounded bg-slate-900 border border-slate-800 text-slate-400 hover:text-red-400 hover:border-red-500/40 transition-all"
                                                title="Xóa tài liệu"
                                                type="button"
                                            >
                                                <Trash2 className="w-3.5 h-3.5" />
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
