import React, { useState } from 'react';
import { User, Lock, ArrowRight, Brain, Check, AlertTriangle, CheckCircle } from 'lucide-react';
import { api } from '../lib/api';

interface AuthFormProps {
    onAuthSuccess: (user: { username: string; role: string }) => void;
}

export default function AuthForm({ onAuthSuccess }: AuthFormProps) {
    const [authMode, setAuthMode] = useState<'login' | 'register'>('login');
    const [authUsername, setAuthUsername] = useState('');
    const [authPassword, setAuthPassword] = useState('');
    const [authError, setAuthError] = useState<string | null>(null);
    const [authSuccessMsg, setAuthSuccessMsg] = useState<string | null>(null);

    const handleAuthSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setAuthError(null);
        setAuthSuccessMsg(null);

        if (!authUsername.trim() || !authPassword.trim()) {
            setAuthError('Vui lòng điền đầy đủ thông tin đăng nhập.');
            return;
        }

        try {
            if (authMode === 'login') {
                const response = await api.post('/api/auth/login', {
                    username: authUsername,
                    password: authPassword,
                });
                const { token, username, role } = response.data;
                localStorage.setItem('token', token);
                localStorage.setItem('username', username);
                localStorage.setItem('role', role);
                onAuthSuccess({ username, role });
            } else {
                const response = await api.post('/api/auth/register', {
                    username: authUsername,
                    password: authPassword,
                });
                setAuthSuccessMsg(response.data.message || 'Đăng ký tài khoản thành công! Hãy đăng nhập.');
                setAuthMode('login');
            }
            setAuthUsername('');
            setAuthPassword('');
        } catch (err: any) {
            const errorMsg =
                err.response?.data?.detail || err.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.';
            setAuthError(errorMsg);
        }
    };

    return (
        <div className="min-h-screen flex items-center justify-center bg-[#070913] px-4 py-12 relative overflow-hidden">
            {/* Abstract futuristic grid elements */}
            <div className="absolute top-[-10%] left-[-20%] w-[60%] h-[60%] bg-violet-600/10 rounded-full blur-[120px] pointer-events-none" />
            <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] bg-indigo-600/10 rounded-full blur-[120px] pointer-events-none" />

            <div className="max-w-5xl w-full grid grid-cols-1 md:grid-cols-12 rounded-2xl glass-panel overflow-hidden shadow-2xl relative z-10">
                {/* Decorative Presentation Column */}
                <div className="md:col-span-5 bg-gradient-to-br from-slate-950/90 to-[#121626] p-8 md:p-12 flex flex-col justify-between border-r border-slate-800/40">
                    <div>
                        <div className="flex items-center gap-2.5 mb-8">
                            <div className="w-10 h-10 rounded-xl bg-violet-600 flex items-center justify-center text-white font-bold shadow-[0_0_15px_rgba(139,92,246,0.5)]">
                                <Brain className="w-5.5 h-5.5" />
                            </div>
                            <span className="text-xl font-bold bg-gradient-to-r from-violet-400 to-indigo-300 bg-clip-text text-transparent">
                                UET RAG AI
                            </span>
                        </div>

                        <h2 className="text-3xl font-extrabold text-slate-100 leading-tight mb-6">
                            Trợ lý Học tập thế hệ mới.
                        </h2>
                        <p className="text-slate-400 leading-relaxed mb-8">
                            Hệ thống truy vấn thông minh khai phá tri thức dựa trên tài nguyên học liệu nội bộ trường
                            Đại học Công nghệ.
                        </p>

                        <div className="space-y-5">
                            <div className="flex items-start gap-3">
                                <div className="p-1 rounded-md bg-emerald-500/15 text-emerald-400 mt-0.5">
                                    <Check className="w-4 h-4" />
                                </div>
                                <div>
                                    <h4 className="text-slate-200 font-semibold text-sm">Truy xuất ngữ cảnh RAG</h4>
                                    <p className="text-slate-400 text-xs">
                                        Phân tích chính xác, đối sánh vector semantic để tìm tài liệu.
                                    </p>
                                </div>
                            </div>
                            <div className="flex items-start gap-3">
                                <div className="p-1 rounded-md bg-violet-500/15 text-violet-400 mt-0.5">
                                    <Check className="w-4 h-4" />
                                </div>
                                <div>
                                    <h4 className="text-slate-200 font-semibold text-sm">Lưu trữ Qdrant an toàn</h4>
                                    <p className="text-slate-400 text-xs">
                                        Cơ sở dữ liệu Vector lưu trữ và truy cập nhanh tốc độ micro giây.
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div className="text-xs text-slate-500 mt-8 md:mt-0">
                        © 2026 UET Chatbot System. Phiên bản RAG v1.0
                    </div>
                </div>

                {/* Form Processing Column */}
                <div className="md:col-span-7 p-8 md:p-12 flex flex-col justify-center bg-slate-900/40">
                    <div className="max-w-md w-full mx-auto">
                        <div className="mb-8">
                            <span className="text-xs font-semibold uppercase tracking-wider text-violet-400">
                                Cổng đăng nhập hệ thống
                            </span>
                            <h3 className="text-2xl font-bold text-slate-100 mt-1">
                                {authMode === 'login' ? 'Chào mừng bạn trở lại' : 'Đăng ký tài khoản mới'}
                            </h3>
                            <p className="text-slate-400 text-sm mt-1">
                                {authMode === 'login'
                                    ? 'Hãy đăng nhập bằng tài khoản nội bộ'
                                    : 'Tạo mới một tài khoản sinh viên'}
                            </p>
                        </div>

                        {authError && (
                            <div className="p-3 mb-5 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-xs flex items-center gap-2">
                                <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                                <span>{authError}</span>
                            </div>
                        )}

                        {authSuccessMsg && (
                            <div className="p-3 mb-5 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 text-xs flex items-center gap-2">
                                <CheckCircle className="w-4 h-4 flex-shrink-0" />
                                <span>{authSuccessMsg}</span>
                            </div>
                        )}

                        <form onSubmit={handleAuthSubmit} className="space-y-4">
                            <div>
                                <label className="block text-slate-300 text-xs font-medium mb-1.5">
                                    Tên đăng nhập (Username)
                                </label>
                                <div className="relative">
                                    <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                                    <input
                                        type="text"
                                        value={authUsername}
                                        onChange={(e) => setAuthUsername(e.target.value)}
                                        placeholder="Nhập tên đăng nhập..."
                                        className="w-full glass-input pl-10 pr-4 py-2.5 rounded-lg text-sm"
                                    />
                                </div>
                            </div>

                            <div>
                                <label className="block text-slate-300 text-xs font-medium mb-1.5">
                                    Mật khẩu (Password)
                                </label>
                                <div className="relative">
                                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500" />
                                    <input
                                        type="password"
                                        value={authPassword}
                                        onChange={(e) => setAuthPassword(e.target.value)}
                                        placeholder="••••••••"
                                        className="w-full glass-input pl-10 pr-4 py-2.5 rounded-lg text-sm"
                                    />
                                </div>
                                {authMode === 'register' && (
                                    <span className="text-[10px] text-slate-500 mt-1 block">
                                        Yêu cầu tối thiểu từ 6 ký tự trở lên.
                                    </span>
                                )}
                            </div>

                            <button
                                type="submit"
                                className="w-full py-2.5 rounded-lg glass-button-primary mt-6 text-sm flex items-center justify-center gap-2"
                            >
                                <span>{authMode === 'login' ? 'Đăng nhập vào hệ thống' : 'Đăng ký tài khoản'}</span>
                                <ArrowRight className="w-4 h-4" />
                            </button>
                        </form>

                        <div className="mt-6 text-center text-sm text-slate-400 border-t border-slate-800/60 pt-6">
                            {authMode === 'login' ? (
                                <span>
                                    Bạn chưa có tài khoản?{' '}
                                    <button
                                        onClick={() => {
                                            setAuthMode('register');
                                            setAuthError(null);
                                        }}
                                        className="text-violet-400 hover:underline font-medium"
                                        type="button"
                                    >
                                        Đăng ký
                                    </button>
                                </span>
                            ) : (
                                <span>
                                    Đã có tài khoản?{' '}
                                    <button
                                        onClick={() => {
                                            setAuthMode('login');
                                            setAuthError(null);
                                        }}
                                        className="text-violet-400 hover:underline font-medium"
                                        type="button"
                                    >
                                        Đăng nhập ngay
                                    </button>
                                </span>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
