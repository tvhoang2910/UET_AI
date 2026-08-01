import React, { useState } from 'react';
import { Send } from 'lucide-react';

interface ChatInputProps {
    isGenerating: boolean;
    onSend: (val: string) => void;
}

export default function ChatInput({ isGenerating, onSend }: ChatInputProps) {
    const [localInput, setLocalInput] = useState('');

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!localInput.trim() || isGenerating) return;
        onSend(localInput);
        setLocalInput('');
    };

    return (
        <form onSubmit={handleSubmit} className="max-w-3xl mx-auto flex gap-2">
            <input
                type="text"
                value={localInput}
                onChange={(e) => setLocalInput(e.target.value)}
                placeholder="Gửi câu hỏi của bạn (VD: Trình bày bài học về tiếng Việt)..."
                className="flex-1 glass-input px-4 py-3 rounded-xl text-sm"
                disabled={isGenerating}
            />
            <button
                type="submit"
                className={`px-4 rounded-xl flex items-center justify-center transition-all ${
                    isGenerating || !localInput.trim()
                        ? 'bg-slate-800 text-slate-500 cursor-not-allowed border border-slate-700/40'
                        : 'glass-button-primary'
                }`}
                disabled={isGenerating || !localInput.trim()}
            >
                <Send className="w-4.5 h-4.5" />
            </button>
        </form>
    );
}
