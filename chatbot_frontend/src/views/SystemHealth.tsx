import { useState, useEffect, useCallback } from 'react';
import { Activity, RefreshCw, CheckCircle, AlertTriangle, Brain, FileSpreadsheet } from 'lucide-react';
import type { SystemHealth as SystemHealthType } from '../types';
import { api } from '../lib/api';

export default function SystemHealth() {
    const [health, setHealth] = useState<SystemHealthType | null>(null);
    const [isCheckingHealth, setIsCheckingHealth] = useState(false);

    const checkSystemHealth = useCallback(async () => {
        setIsCheckingHealth(true);
        try {
            const response = await api.get('/api/system/health');
            setHealth(response.data);
        } catch (err) {
            console.error('Failed to parse system health status', err);
        } finally {
            setIsCheckingHealth(false);
        }
    }, []);

    // Initial check
    useEffect(() => {
        checkSystemHealth();
    }, [checkSystemHealth]);

    return (
        <div className="p-6 md:p-8 max-w-4xl mx-auto space-y-8 animate-fade-in">
            {/* Header Panel */}
            <div className="border-b border-slate-800/40 pb-5 flex items-center justify-between">
                <div>
                    <h1 className="text-2xl font-bold text-slate-100 flex items-center gap-2">
                        <Activity className="w-7 h-7 text-violet-400" />
                        Báo cáo hiệu năng hệ thống
                    </h1>
                    <p className="text-slate-400 text-sm mt-1">
                        Theo dõi trực quan thời gian thực các kết nối hạ tầng AI (Ollama, Qdrant DB, và Model).
                    </p>
                </div>
                <button
                    onClick={checkSystemHealth}
                    className="px-3.5 py-1.5 rounded-lg border border-slate-800 bg-slate-900 text-xs text-slate-300 flex items-center gap-2 hover:bg-slate-800 transition-all"
                    disabled={isCheckingHealth}
                    type="button"
                >
                    <RefreshCw className={`w-3.5 h-3.5 ${isCheckingHealth ? 'animate-spin text-violet-400' : ''}`} />
                    Tải lại dữ liệu
                </button>
            </div>

            {health && (
                <div
                    className={`p-4 rounded-2xl border flex items-center gap-4 ${
                        health.status === 'UP'
                            ? 'bg-emerald-950/20 border-emerald-500/20 text-emerald-300'
                            : 'bg-red-950/20 border-red-500/20 text-red-300'
                    }`}
                >
                    <div
                        className={`w-10 h-10 rounded-xl flex items-center justify-center ${
                            health.status === 'UP' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'
                        }`}
                    >
                        {health.status === 'UP' ? (
                            <CheckCircle className="w-6 h-6 animate-pulse-soft" />
                        ) : (
                            <AlertTriangle className="w-6 h-6" />
                        )}
                    </div>
                    <div>
                        <h3 className="text-sm font-bold">
                            {health.status === 'UP' ? 'TOÀN BỘ HỆ THỐNG ĐÃ SẴN SÀNG' : 'HỆ THỐNG CÓ SỰ CỐ GIÁN ĐOẠN'}
                        </h3>
                        <p className="text-slate-400 text-xs mt-0.5">
                            Thời điểm kiểm thử gần nhất: {new Date(health.checkedAt).toLocaleTimeString('vi-VN')}
                        </p>
                    </div>
                </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Local AI Hosting */}
                <div className="bg-slate-900/20 border border-slate-800/60 rounded-2xl p-5 space-y-4">
                    <div className="flex items-center justify-between">
                        <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-widest">
                            Local AI Hosting
                        </span>
                        <span
                            className={`w-2 h-2 rounded-full ${
                                health?.ollama.status === 'UP' ? 'bg-emerald-400 animate-pulse' : 'bg-red-500'
                            }`}
                        />
                    </div>
                    <div className="flex items-start gap-3">
                        <div className="p-2 rounded-lg bg-violet-500/10 text-violet-400">
                            <Brain className="w-5 h-5" />
                        </div>
                        <div>
                            <h4 className="text-sm font-bold text-slate-200">Ollama API Service</h4>
                            <p className="text-slate-400 text-xs mt-0.5">Runtime hosting model cục bộ.</p>
                        </div>
                    </div>
                    <div className="bg-slate-950/40 p-3 rounded-lg text-[11px] space-y-1 text-slate-400">
                        <div>
                            Trạng thái:{' '}
                            <span className="font-mono text-slate-200 font-bold">
                                {health?.ollama.status || 'Chờ kiểm tra'}
                            </span>
                        </div>
                        {health?.ollama.details.version && (
                            <div>
                                Phiên bản Ollama:{' '}
                                <span className="font-mono text-violet-400">{health.ollama.details.version}</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Vector Database */}
                <div className="bg-slate-900/20 border border-slate-800/60 rounded-2xl p-5 space-y-4">
                    <div className="flex items-center justify-between">
                        <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-widest">
                            Vector Database
                        </span>
                        <span
                            className={`w-2 h-2 rounded-full ${
                                health?.qdrant.status === 'UP' ? 'bg-emerald-400 animate-pulse' : 'bg-red-500'
                            }`}
                        />
                    </div>
                    <div className="flex items-start gap-3">
                        <div className="p-2 rounded-lg bg-violet-500/10 text-violet-400">
                            <CheckCircle className="w-5 h-5" />
                        </div>
                        <div>
                            <h4 className="text-sm font-bold text-slate-200">Qdrant Vector Database</h4>
                            <p className="text-slate-400 text-xs mt-0.5">Không gian biểu diễn vector ngữ nghĩa.</p>
                        </div>
                    </div>
                    <div className="bg-slate-950/40 p-3 rounded-lg text-[11px] space-y-1 text-slate-400">
                        <div>
                            Trạng thái kết nối:{' '}
                            <span className="font-mono text-slate-200 font-bold">
                                {health?.qdrant.status || 'Chờ kiểm tra'}
                            </span>
                        </div>
                        {health?.qdrant.details.collection && (
                            <div>
                                Không gian Collection:{' '}
                                <span className="font-mono text-violet-400">{health.qdrant.details.collection}</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Reasoning Model */}
                <div className="bg-slate-900/20 border border-slate-800/60 rounded-2xl p-5 space-y-4">
                    <div className="flex items-center justify-between">
                        <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-widest">
                            Reasoning Model
                        </span>
                        <span
                            className={`w-2 h-2 rounded-full ${
                                health?.chatModel.status === 'READY' ? 'bg-emerald-400 animate-pulse' : 'bg-red-500'
                            }`}
                        />
                    </div>
                    <div className="flex items-start gap-3">
                        <div className="p-2 rounded-lg bg-violet-500/10 text-violet-400">
                            <Brain className="w-5 h-5" />
                        </div>
                        <div>
                            <h4 className="text-sm font-bold text-slate-200">Chat Generation Model</h4>
                            <p className="text-slate-400 text-xs mt-0.5">Suy luận logic và định dạng câu trả lời.</p>
                        </div>
                    </div>
                    <div className="bg-slate-950/40 p-3 rounded-lg text-[11px] space-y-1 text-slate-400">
                        <div>
                            Trạng thái:{' '}
                            <span className="font-mono text-slate-200 font-bold">
                                {health?.chatModel.status || 'Chờ kiểm tra'}
                            </span>
                        </div>
                        {health?.chatModel.details.model && (
                            <div>
                                Tên Model:{' '}
                                <span className="font-mono text-violet-400">{health.chatModel.details.model}</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Vector Encoder */}
                <div className="bg-slate-900/20 border border-slate-800/60 rounded-2xl p-5 space-y-4">
                    <div className="flex items-center justify-between">
                        <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-widest">
                            Vector Encoder
                        </span>
                        <span
                            className={`w-2 h-2 rounded-full ${
                                health?.embeddingModel.status === 'READY'
                                    ? 'bg-emerald-400 animate-pulse'
                                    : 'bg-red-500'
                            }`}
                        />
                    </div>
                    <div className="flex items-start gap-3">
                        <div className="p-2 rounded-lg bg-violet-500/10 text-violet-400">
                            <FileSpreadsheet className="w-5 h-5" />
                        </div>
                        <div>
                            <h4 className="text-sm font-bold text-slate-200">Embedding Model Encoder</h4>
                            <p className="text-slate-400 text-xs mt-0.5">Tính toán vector hóa văn bản PDF đầu vào.</p>
                        </div>
                    </div>
                    <div className="bg-slate-950/40 p-3 rounded-lg text-[11px] space-y-1 text-slate-400">
                        <div>
                            Trạng thái:{' '}
                            <span className="font-mono text-slate-200 font-bold">
                                {health?.embeddingModel.status || 'Chờ kiểm tra'}
                            </span>
                        </div>
                        {health?.embeddingModel.details.model && (
                            <div>
                                Tên Model:{' '}
                                <span className="font-mono text-violet-400">
                                    {health.embeddingModel.details.model}
                                </span>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
}
