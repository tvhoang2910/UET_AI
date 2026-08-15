import { useState, useEffect, useRef } from 'react';
import { MessageSquare, Plus, ChevronRight, Brain } from 'lucide-react';
import type { ChatSession, ChatMessage, ChatSource } from '../types';
import ChatInput from '../components/ChatInput';
import MessageItem from '../components/MessageItem';

interface ChatWorkspaceProps {
    activeSessionId: string | null;
    sessions: ChatSession[];
    createNewSession: () => void;
    user: { username: string; role: string };
    triggerNotification: (message: string, type?: 'success' | 'error') => void;
}

export default function ChatWorkspace({
    activeSessionId,
    sessions,
    createNewSession,
    user,
    triggerNotification,
}: ChatWorkspaceProps) {
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [isGenerating, setIsGenerating] = useState(false);
    const chatBottomRef = useRef<HTMLDivElement>(null);

    // Load chat history from localStorage whenever active session changes
    useEffect(() => {
        if (activeSessionId) {
            const storedHistory = localStorage.getItem(`chat_history_${activeSessionId}`);
            if (storedHistory) {
                setMessages(JSON.parse(storedHistory));
            } else {
                setMessages([]);
            }
        } else {
            setMessages([]);
        }
    }, [activeSessionId]);

    // Scroll to bottom when message log updates
    useEffect(() => {
        chatBottomRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages, isGenerating]);

    const handleSendMessage = async (userMessageContent: string) => {
        if (!userMessageContent.trim() || isGenerating) return;

        if (!activeSessionId) {
            triggerNotification('Hãy chọn hoặc tạo một hội thoại mới.', 'error');
            return;
        }

        const newUserMessage: ChatMessage = {
            role: 'user',
            content: userMessageContent,
            timestamp: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
        };

        const updatedMessages = [...messages, newUserMessage];
        setMessages(updatedMessages);
        localStorage.setItem(`chat_history_${activeSessionId}`, JSON.stringify(updatedMessages));

        setIsGenerating(true);

        try {
            const res = await fetch(`http://localhost:8080/api/chat/sessions/${activeSessionId}/stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ message: userMessageContent }),
            });

            if (!res.body) {
                throw new Error('No response body from server');
            }

            const reader = res.body.getReader();
            const decoder = new TextDecoder();

            let assistantContent = '';
            let sources: ChatSource[] = [];
            let buffer = '';

            const botMsg: ChatMessage = {
                role: 'assistant',
                content: '',
                sources: [],
                timestamp: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
            };

            const withBot = [...updatedMessages, botMsg];
            setMessages(withBot);
            localStorage.setItem(`chat_history_${activeSessionId}`, JSON.stringify(withBot));

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const parts = buffer.split('\n\n');
                buffer = parts.pop() || '';

                for (const part of parts) {
                    let eventType = '';
                    const dataLines: string[] = [];

                    for (const line of part.split('\n')) {
                        if (line.startsWith('event:')) eventType = line.slice(6).trim();
                        if (line.startsWith('data:')) dataLines.push(line.slice(5));
                    }

                    const data = dataLines.join('\n');

                    if (eventType === 'token') {
                        assistantContent += data;
                        setMessages((prev) => {
                            const cp = [...prev];
                            const last = cp[cp.length - 1];
                            cp[cp.length - 1] = { ...last, content: assistantContent };
                            return cp;
                        });
                    }

                    if (eventType === 'sources') {
                        try {
                            sources = JSON.parse(data);
                            setMessages((prev) => {
                                const cp = [...prev];
                                const last = cp[cp.length - 1];
                                cp[cp.length - 1] = { ...last, sources };
                                return cp;
                            });
                        } catch {
                            // ignore parse errors
                        }
                    }
                }
            }

            const finalMessages = [
                ...updatedMessages,
                {
                    ...botMsg,
                    content: assistantContent,
                    sources,
                    timestamp: botMsg.timestamp,
                },
            ];

            setMessages(finalMessages);
            localStorage.setItem(`chat_history_${activeSessionId}`, JSON.stringify(finalMessages));
        } catch (err: any) {
            const errorMsg = err?.message || err.response?.data?.detail || 'Hệ thống AI tạm thời bận. Hãy thử lại.';
            const systemErrorMsg: ChatMessage = {
                role: 'assistant',
                content: `❌ **Lỗi máy chủ:** ${errorMsg}`,
                timestamp: new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }),
            };
            setMessages((prev) => [...prev, systemErrorMsg]);
        } finally {
            setIsGenerating(false);
        }
    };

    return (
        <div className="h-full flex flex-col justify-between">
            {/* Dynamic Header displaying chat parameters */}
            <div className="px-6 py-4 border-b border-slate-800/40 bg-slate-950/20 flex items-center justify-between">
                <div>
                    <h1 className="text-md font-bold text-slate-200">
                        {sessions.find((s) => s.id === activeSessionId)?.title || 'Phòng hội thoại'}
                    </h1>
                    <p className="text-[11px] text-slate-500">Mô hình hoạt động: Qwen-3 (8B) + RAG Knowledge Core</p>
                </div>
                {activeSessionId && (
                    <div className="flex items-center gap-1 text-[11px] text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2.5 py-1 rounded-full">
                        <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                        Đồng bộ thời gian thực
                    </div>
                )}
            </div>

            {/* Chat Thread container */}
            <div className="flex-1 overflow-y-auto p-6 space-y-6">
                {!activeSessionId ? (
                    <div className="h-full flex flex-col items-center justify-center max-w-xl mx-auto text-center space-y-6">
                        <div className="w-16 h-16 rounded-2xl bg-violet-600/10 border border-violet-500/20 flex items-center justify-center text-violet-400 animate-bounce">
                            <MessageSquare className="w-8 h-8" />
                        </div>
                        <div className="space-y-2">
                            <h2 className="text-xl font-bold text-slate-200">Bắt đầu trò chuyện</h2>
                            <p className="text-slate-400 text-sm">
                                Chào mừng bạn đến với UET Chatbot. Hãy nhấn nút để khởi tạo một cuộc hội thoại thông minh
                                mới hoặc chọn các phiên cũ từ danh sách lịch sử.
                            </p>
                        </div>
                        <button
                            onClick={createNewSession}
                            className="px-4 py-2 rounded-lg glass-button-primary text-xs flex items-center gap-2"
                            type="button"
                        >
                            <Plus className="w-4 h-4" />
                            Khởi tạo phòng chat mới
                        </button>
                    </div>
                ) : messages.length === 0 ? (
                    <div className="h-full flex flex-col justify-center max-w-2xl mx-auto space-y-8">
                        <div className="text-center space-y-2">
                            <h2 className="text-2xl font-black bg-gradient-to-r from-violet-400 to-indigo-300 bg-clip-text text-transparent">
                                UET RAG Smart AI
                            </h2>
                            <p className="text-slate-400 text-sm">
                                Học máy ngữ nghĩa dựa trên tài liệu PDF học liệu chuyên ngành
                            </p>
                        </div>

                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {[
                                {
                                    title: 'Tài nguyên tiếng Việt',
                                    question: 'Tóm tắt nội dung tài liệu Học Tiếng Việt Dễ Dàng giúp tôi.',
                                },
                                {
                                    title: 'Semantic Search',
                                    question: 'Phương pháp học tiếng Việt nhanh nhất được mô tả là gì?',
                                },
                                {
                                    title: 'Phân tích RAG',
                                    question: 'Cơ sở dữ liệu Vector Qdrant chứa các phân đoạn nào?',
                                },
                                {
                                    title: 'System Health',
                                    question: 'Trạng thái hoạt động của các service như thế nào?',
                                },
                            ].map((card, idx) => (
                                <div
                                    key={idx}
                                    onClick={() => handleSendMessage(card.question)}
                                    className="p-4 rounded-xl border border-slate-800/60 bg-slate-900/30 hover:bg-slate-800/40 cursor-pointer transition-all hover:border-violet-500/40 group"
                                >
                                    <h4 className="text-xs font-semibold text-slate-300 mb-1 group-hover:text-violet-300 flex items-center justify-between">
                                        {card.title}
                                        <ChevronRight className="w-3.5 h-3.5 opacity-0 group-hover:opacity-100 transition-all text-violet-400" />
                                    </h4>
                                    <p className="text-[11px] text-slate-500 line-clamp-2">{card.question}</p>
                                </div>
                            ))}
                        </div>
                    </div>
                ) : (
                    <div className="max-w-3xl mx-auto space-y-6">
                        {messages.map((msg, idx) => (
                            <MessageItem key={idx} msg={msg} username={user.username} />
                        ))}

                        {/* AI typing animation state */}
                        {isGenerating && (
                            <div className="flex items-start gap-4">
                                <div className="w-9 h-9 rounded-lg bg-violet-600/10 border border-violet-500/20 flex items-center justify-center text-violet-400 flex-shrink-0 animate-pulse-soft">
                                    <Brain className="w-5 h-5 animate-pulse" />
                                </div>
                                <div className="flex flex-col space-y-1.5 max-w-[80%]">
                                    <div className="px-4 py-3 rounded-2xl bg-slate-900/50 border border-slate-800 rounded-tl-sm flex items-center gap-1.5 text-xs text-slate-400">
                                        <span className="w-1.5 h-1.5 bg-violet-400 rounded-full animate-bounce" />
                                        <span
                                            className="w-1.5 h-1.5 bg-violet-400 rounded-full animate-bounce"
                                            style={{ animationDelay: '150ms' }}
                                        />
                                        <span
                                            className="w-1.5 h-1.5 bg-violet-400 rounded-full animate-bounce"
                                            style={{ animationDelay: '300ms' }}
                                        />
                                        <span>Đang truy xuất thông tin ngữ nghĩa và suy luận phản hồi...</span>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                )}
                <div ref={chatBottomRef} />
            </div>

            {/* Bot Input Controller footer block */}
            {activeSessionId && (
                <div className="p-4 border-t border-slate-800/40 bg-slate-950/20">
                    <ChatInput isGenerating={isGenerating} onSend={handleSendMessage} />
                    <p className="text-[10px] text-slate-500 text-center mt-2">
                        Học máy có thể đưa ra câu trả lời không chính xác. Hãy xác thực lại dữ liệu trích dẫn phía dưới
                        bong bóng hội thoại.
                    </p>
                </div>
            )}
        </div>
    );
}
