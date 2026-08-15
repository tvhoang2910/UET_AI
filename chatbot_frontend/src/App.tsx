import React, { useEffect, useState } from 'react';
import { Brain, Menu, AlertTriangle, CheckCircle } from 'lucide-react';
import { api } from './lib/api';
import type { ChatSession } from './types';

// Components
import Sidebar from './components/Sidebar';
import ChatWorkspace from './views/ChatWorkspace';
import DocumentCatalog from './views/DocumentCatalog';
import SystemHealth from './views/SystemHealth';

const DEFAULT_USER = { username: 'default_user', role: 'USER' };

export default function App() {
    // Global User & Routing State
    const [user, setUser] = useState<{ username: string; role: string }>(() => {
        const username = localStorage.getItem('username') ?? DEFAULT_USER.username;
        const role = localStorage.getItem('role') ?? DEFAULT_USER.role;
        return { username, role };
    });

    const [activeTab, setActiveTab] = useState<'chat' | 'documents' | 'health'>('chat');
    const [sidebarOpen, setSidebarOpen] = useState(true);

    // Global Notification State
    const [notification, setNotification] = useState<{ message: string; type: 'success' | 'error' } | null>(null);

    const triggerNotification = (message: string, type: 'success' | 'error' = 'success') => {
        setNotification({ message, type });
        setTimeout(() => setNotification(null), 4000);
    };

    // Shared Sessions State (Needed by Sidebar & ChatWorkspace)
    const [sessions, setSessions] = useState<ChatSession[]>([]);
    const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
    const [isLoadingSessions, setIsLoadingSessions] = useState(false);

    // Document Count for Sidebar
    const [documentCount, setDocumentCount] = useState(0);

    // Fetch initial shared data on mount
    useEffect(() => {
        fetchSessions();
        fetchDocumentCount();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const fetchSessions = async () => {
        setIsLoadingSessions(true);
        try {
            const response = await api.get('/api/chat/sessions');
            setSessions(response.data);
            if (response.data.length > 0 && !activeSessionId) {
                setActiveSessionId(response.data[0].id);
            }
        } catch (err) {
            console.error('Failed to load chat sessions', err);
        } finally {
            setIsLoadingSessions(false);
        }
    };

    const fetchDocumentCount = async () => {
        try {
            const response = await api.get('/api/documents');
            setDocumentCount(response.data.length);
        } catch (err) {
            console.error('Failed to retrieve document count', err);
        }
    };

    const createNewSession = async () => {
        try {
            const title = `Hội thoại mới ngày ${new Date().toLocaleDateString('vi-VN')}`;
            const response = await api.post(`/api/chat/sessions?title=${encodeURIComponent(title)}`);
            const newSession: ChatSession = response.data;
            setSessions((prev) => [newSession, ...prev]);
            setActiveSessionId(newSession.id);
            setActiveTab('chat');
            triggerNotification('Tạo hội thoại mới thành công!', 'success');
        } catch (err) {
            triggerNotification('Không thể tạo phòng chat mới.', 'error');
        }
    };

    const deleteSession = async (sessionId: string, e: React.MouseEvent) => {
        e.stopPropagation();
        if (!window.confirm('Bạn có chắc muốn xóa phiên hội thoại này?')) return;

        try {
            await api.delete(`/api/chat/sessions/${sessionId}`);
            setSessions((prev) => prev.filter((s) => s.id !== sessionId));
            localStorage.removeItem(`chat_history_${sessionId}`);
            if (activeSessionId === sessionId) {
                setActiveSessionId(null);
            }
            triggerNotification('Đã xóa phiên hội thoại.', 'success');
        } catch (err) {
            triggerNotification('Không thể xóa hội thoại này.', 'error');
        }
    };

    const handleLogout = () => {
        localStorage.setItem('username', DEFAULT_USER.username);
        localStorage.setItem('role', DEFAULT_USER.role);
        setUser(DEFAULT_USER);
        setSessions([]);
        setActiveSessionId(null);
        triggerNotification('Đang ở chế độ single-user.', 'success');
    };

    return (
        <div className="min-h-screen bg-[#070912] flex text-slate-100 overflow-hidden relative">
            {/* Floating System Notifications */}
            {notification && (
                <div
                    className={`fixed top-4 right-4 z-50 flex items-center gap-2 px-4 py-3 rounded-xl border shadow-xl transition-all duration-300 transform translate-y-0 ${notification.type === 'success'
                            ? 'bg-emerald-950/80 border-emerald-500/30 text-emerald-200'
                            : 'bg-red-950/80 border-red-500/30 text-red-200'
                        }`}
                >
                    {notification.type === 'success' ? (
                        <CheckCircle className="w-5 h-5 text-emerald-400" />
                    ) : (
                        <AlertTriangle className="w-5 h-5 text-red-400" />
                    )}
                    <span className="text-sm font-medium">{notification.message}</span>
                </div>
            )}

            {/* LEFT SIDEBAR PANEL */}
            <Sidebar
                user={user}
                onLogout={handleLogout}
                activeTab={activeTab}
                setActiveTab={setActiveTab}
                sidebarOpen={sidebarOpen}
                setSidebarOpen={setSidebarOpen}
                sessions={sessions}
                activeSessionId={activeSessionId}
                setActiveSessionId={setActiveSessionId}
                createNewSession={createNewSession}
                deleteSession={deleteSession}
                isLoadingSessions={isLoadingSessions}
                documentCount={documentCount}
            />

            {/* MAIN CONTENT REGION */}
            <main className="flex-1 flex flex-col min-w-0 h-screen relative bg-[#090c14]">
                {/* Mobile Header bar */}
                <header className="p-4 flex items-center gap-3 border-b border-slate-800/40 bg-slate-950/20 md:hidden">
                    <button
                        onClick={() => setSidebarOpen(true)}
                        className="p-2 rounded-lg bg-slate-900 border border-slate-800 text-slate-400"
                        type="button"
                    >
                        <Menu className="w-5 h-5" />
                    </button>
                    <div className="flex items-center gap-2">
                        <div className="w-7 h-7 rounded-lg bg-violet-600 flex items-center justify-center text-white text-xs">
                            <Brain className="w-4 h-4" />
                        </div>
                        <span className="text-sm font-bold text-slate-200">UET Chatbot RAG</span>
                    </div>
                </header>

                {/* ACTIVE WORKSPACE PANEL RENDERER */}
                <div className="flex-1 overflow-y-auto relative min-h-0">
                    {activeTab === 'chat' && (
                        <ChatWorkspace
                            activeSessionId={activeSessionId}
                            sessions={sessions}
                            createNewSession={createNewSession}
                            user={user}
                            triggerNotification={triggerNotification}
                        />
                    )}
                    {activeTab === 'documents' && <DocumentCatalog triggerNotification={triggerNotification} />}
                    {activeTab === 'health' && <SystemHealth />}
                </div>
            </main>
        </div>
    );
}
