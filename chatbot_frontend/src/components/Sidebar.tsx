import React from 'react';
import {
    Brain,
    X,
    Plus,
    MessageSquare,
    FileText,
    Activity,
    RefreshCw,
    Trash2,
    LogOut,
} from 'lucide-react';
import type { ChatSession } from '../types';

interface SidebarProps {
    user: { username: string; role: string };
    onLogout: () => void;
    activeTab: 'chat' | 'documents' | 'health';
    setActiveTab: (tab: 'chat' | 'documents' | 'health') => void;
    sidebarOpen: boolean;
    setSidebarOpen: (open: boolean) => void;
    sessions: ChatSession[];
    activeSessionId: string | null;
    setActiveSessionId: (id: string | null) => void;
    createNewSession: () => void;
    deleteSession: (sessionId: string, e: React.MouseEvent) => void;
    isLoadingSessions: boolean;
    documentCount: number;
}

export default function Sidebar({
    user,
    onLogout,
    activeTab,
    setActiveTab,
    sidebarOpen,
    setSidebarOpen,
    sessions,
    activeSessionId,
    setActiveSessionId,
    createNewSession,
    deleteSession,
    isLoadingSessions,
    documentCount,
}: SidebarProps) {
    return (
        <aside
            className={`fixed md:relative z-40 h-full w-80 glass-panel border-r border-slate-800/40 flex flex-col justify-between transition-transform duration-300 ease-in-out ${
                sidebarOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'
            }`}
        >
            <div className="flex flex-col flex-1 min-h-0">
                {/* App Brand Header */}
                <div className="p-4 flex items-center justify-between border-b border-slate-800/40">
                    <div className="flex items-center gap-2.5">
                        <div className="w-8.5 h-8.5 rounded-lg bg-violet-600 flex items-center justify-center text-white font-bold shadow-[0_0_12px_rgba(139,92,246,0.3)]">
                            <Brain className="w-4.5 h-4.5" />
                        </div>
                        <div>
                            <span className="text-md font-bold bg-gradient-to-r from-violet-300 to-indigo-300 bg-clip-text text-transparent">
                                UET CHATBOT RAG
                            </span>
                            <span className="block text-[9px] text-slate-500 uppercase tracking-widest">
                                Semantic Core Engine
                            </span>
                        </div>
                    </div>
                    <button
                        onClick={() => setSidebarOpen(false)}
                        className="md:hidden p-1.5 rounded-lg hover:bg-slate-800/60 text-slate-400"
                        type="button"
                    >
                        <X className="w-5 h-5" />
                    </button>
                </div>

                {/* New Chat Trigger Action */}
                <div className="p-4">
                    <button
                        onClick={createNewSession}
                        className="w-full py-2 px-3 rounded-lg flex items-center justify-center gap-2 text-xs font-semibold text-violet-300 border border-violet-500/30 bg-violet-500/5 hover:bg-violet-500/12 transition-all duration-200"
                        type="button"
                    >
                        <Plus className="w-4 h-4" />
                        Tạo phòng trò chuyện mới
                    </button>
                </div>

                {/* Interactive Navigation Modules */}
                <nav className="px-3 space-y-1">
                    <button
                        onClick={() => setActiveTab('chat')}
                        className={`w-full py-2 px-3.5 rounded-lg flex items-center gap-2.5 text-sm transition-all ${
                            activeTab === 'chat'
                                ? 'bg-violet-600/15 text-violet-300 font-medium'
                                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                        }`}
                        type="button"
                    >
                        <MessageSquare className="w-4.5 h-4.5" />
                        <span>Phòng trò chuyện</span>
                    </button>
                    <button
                        onClick={() => setActiveTab('documents')}
                        className={`w-full py-2 px-3.5 rounded-lg flex items-center gap-2.5 text-sm transition-all ${
                            activeTab === 'documents'
                                ? 'bg-violet-600/15 text-violet-300 font-medium'
                                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                        }`}
                        type="button"
                    >
                        <FileText className="w-4.5 h-4.5" />
                        <span>Kho học liệu ({documentCount})</span>
                    </button>
                    <button
                        onClick={() => setActiveTab('health')}
                        className={`w-full py-2 px-3.5 rounded-lg flex items-center gap-2.5 text-sm transition-all ${
                            activeTab === 'health'
                                ? 'bg-violet-600/15 text-violet-300 font-medium'
                                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/40'
                        }`}
                        type="button"
                    >
                        <Activity className="w-4.5 h-4.5" />
                        <span>Trạng thái hệ thống</span>
                    </button>
                </nav>

                {/* Chat Sessions History Sub-Panel */}
                <div className="flex-1 flex flex-col min-h-0 border-t border-slate-800/40 mt-4">
                    <div className="px-4 py-2 flex items-center justify-between">
                        <span className="text-[10px] font-semibold text-slate-500 uppercase tracking-widest">
                            Hội thoại gần đây
                        </span>
                        {isLoadingSessions && <RefreshCw className="w-3 h-3 text-slate-500 animate-spin" />}
                    </div>

                    <div className="flex-1 overflow-y-auto px-2 pb-4 space-y-0.5">
                        {sessions.length === 0 ? (
                            <div className="px-4 py-6 text-center text-xs text-slate-600">
                                Chưa có cuộc trò chuyện nào
                            </div>
                        ) : (
                            sessions.map((session) => (
                                <div
                                    key={session.id}
                                    onClick={() => {
                                        setActiveSessionId(session.id);
                                        setActiveTab('chat');
                                    }}
                                    className={`group w-full py-2 px-3 rounded-lg flex items-center justify-between gap-2 text-xs cursor-pointer transition-all ${
                                        activeSessionId === session.id
                                            ? 'bg-slate-800/80 text-violet-300 border-l-2 border-violet-500'
                                            : 'text-slate-400 hover:bg-slate-900/50 hover:text-slate-200'
                                    }`}
                                >
                                    <div className="flex items-center gap-2 truncate flex-1">
                                        <MessageSquare className="w-3.5 h-3.5 flex-shrink-0 text-slate-500" />
                                        <span className="truncate">{session.title}</span>
                                    </div>
                                    <button
                                        onClick={(e) => deleteSession(session.id, e)}
                                        className="opacity-0 group-hover:opacity-100 p-1 rounded hover:bg-slate-800 text-slate-500 hover:text-red-400 transition-all"
                                        title="Xóa phiên"
                                        type="button"
                                    >
                                        <Trash2 className="w-3.5 h-3.5" />
                                    </button>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </div>

            {/* LOGGED IN USER PROFILE FOOTER BLOCK */}
            <div className="p-4 border-t border-slate-800/40 bg-slate-950/60 flex items-center justify-between">
                <div className="flex items-center gap-2.5 truncate">
                    <div className="w-9 h-9 rounded-full bg-violet-600/10 border border-violet-500/20 flex items-center justify-center text-violet-400 font-semibold">
                        {user.username.charAt(0).toUpperCase()}
                    </div>
                    <div className="truncate">
                        <span className="block text-sm font-semibold text-slate-200 truncate">{user.username}</span>
                        <span className="block text-[10px] text-slate-500 font-mono truncate">{user.role}</span>
                    </div>
                </div>
                <button
                    onClick={onLogout}
                    className="p-2 rounded-lg hover:bg-red-500/10 text-slate-400 hover:text-red-400 transition-all"
                    title="Đăng xuất"
                    type="button"
                >
                    <LogOut className="w-4.5 h-4.5" />
                </button>
            </div>
        </aside>
    );
}
