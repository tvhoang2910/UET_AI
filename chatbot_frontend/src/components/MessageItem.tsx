import { Fragment, type ReactNode } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Brain, BookOpen, FileSpreadsheet } from 'lucide-react';
import type { ChatMessage } from '../types';

const citationPattern = /(\[Nguồn\s+\d+:\s*Trang\s+\d+\]|\[Trang\s+\d+\])/g;

function renderCitationBadges(children: ReactNode): ReactNode {
    if (typeof children === 'string') {
        const parts = children.split(citationPattern);

        return parts.map((part, index) => {
            if (part && /^(\[Nguồn\s+\d+:\s*Trang\s+\d+\]|\[Trang\s+\d+\])$/.test(part)) {
                return (
                    <span
                        key={`${part}-${index}`}
                        className="inline-flex items-center px-2 py-0.5 mx-1 rounded text-[10px] font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/80 dark:text-blue-200 border border-blue-300/70 cursor-pointer hover:bg-blue-200 transition-colors"
                        title="Nhấn để xem chi tiết nguồn trích dẫn"
                    >
                        📌 {part.replace(/[\[\]]/g, '')}
                    </span>
                );
            }
            return <Fragment key={`${part}-${index}`}>{part}</Fragment>;
        });
    }

    if (Array.isArray(children)) {
        return children.map((child, index) => (
            <Fragment key={index}>{renderCitationBadges(child)}</Fragment>
        ));
    }

    return children;
}

interface MessageItemProps {
    msg: ChatMessage;
    username: string;
}

export default function MessageItem({ msg, username }: MessageItemProps) {
    return (
        <div className={`flex items-start gap-4 ${msg.role === 'user' ? 'justify-end' : ''}`}>
            {msg.role === 'assistant' && (
                <div className="w-9 h-9 rounded-lg bg-violet-600/10 border border-violet-500/20 flex items-center justify-center text-violet-400 flex-shrink-0">
                    <Brain className="w-5 h-5" />
                </div>
            )}

            <div className={`max-w-[85%] flex flex-col space-y-1.5 ${msg.role === 'user' ? 'items-end' : ''}`}>
                <div
                    className={`px-4 py-3 rounded-2xl text-sm leading-relaxed ${msg.role === 'user'
                            ? 'bg-violet-600 text-white rounded-tr-sm font-medium'
                            : 'bg-slate-900/75 border border-slate-800 rounded-tl-sm text-slate-200 shadow-md'
                        }`}
                >
                    <div className="markdown-content">
                        <ReactMarkdown
                            remarkPlugins={[remarkGfm]}
                            components={{
                                p: ({ children, ...props }) => (
                                    <p className="mb-2 last:mb-0 leading-relaxed" {...props}>
                                        {renderCitationBadges(children)}
                                    </p>
                                ),
                                ul: ({ ...props }) => <ul className="list-disc pl-5 mb-2 space-y-1" {...props} />,
                                ol: ({ ...props }) => <ol className="list-decimal pl-5 mb-2 space-y-1" {...props} />,
                                table: ({ ...props }) => (
                                    <div className="overflow-x-auto my-3">
                                        <table className="w-full border-collapse text-left text-xs" {...props} />
                                    </div>
                                ),
                                th: ({ ...props }) => <th className="border border-slate-700 bg-slate-800/80 px-2 py-2 font-semibold text-slate-100" {...props} />,
                                td: ({ ...props }) => <td className="border border-slate-700 px-2 py-2 text-slate-200 align-top" {...props} />,
                                code: ({ children, className }) => {
                                    const isInline = !className;
                                    return isInline ? (
                                        <code className="bg-slate-800/50 text-violet-300 px-1 py-0.5 rounded text-xs font-mono">
                                            {children}
                                        </code>
                                    ) : (
                                        <pre className="bg-slate-950 p-3 rounded-lg overflow-x-auto text-xs font-mono border border-slate-800 my-2">
                                            <code className="text-slate-200">{children}</code>
                                        </pre>
                                    );
                                },
                            }}
                        >
                            {msg.content}
                        </ReactMarkdown>
                    </div>
                </div>

                <div className="flex items-center gap-2 text-[10px] text-slate-500 px-1">
                    <span>{msg.timestamp}</span>
                    {msg.role === 'assistant' && msg.sources && msg.sources.length > 0 && (
                        <>
                            <span>•</span>
                            <span className="text-violet-400">Đã dùng RAG ( {msg.sources.length} trích dẫn )</span>
                        </>
                    )}
                </div>

                {msg.role === 'assistant' && msg.sources && msg.sources.length > 0 && (
                    <div className="w-full mt-2 border border-slate-800/60 rounded-xl bg-slate-950/40 p-3.5 space-y-3">
                        <div className="flex items-center gap-2 text-[11px] font-semibold text-slate-400 uppercase tracking-widest border-b border-slate-800/40 pb-2">
                            <BookOpen className="w-3.5 h-3.5 text-violet-400" />
                            <span>Văn bản trích xuất từ cơ sở tri thức:</span>
                        </div>
                        <div className="space-y-3">
                            {msg.sources.map((src, srcIdx) => (
                                <div
                                    key={srcIdx}
                                    className="text-xs bg-[#0b0e17] rounded-lg border border-slate-800/40 p-3 relative overflow-hidden"
                                >
                                    <div className="flex items-center justify-between mb-2">
                                        <div className="flex items-center gap-2">
                                            <FileSpreadsheet className="w-3.5 h-3.5 text-slate-400" />
                                            <span
                                                className="font-semibold text-slate-300 max-w-[200px] truncate"
                                                title={src.title}
                                            >
                                                {src.title}
                                            </span>
                                            {src.pageNumber && (
                                                <span className="bg-violet-500/10 border border-violet-500/20 text-violet-400 text-[9px] px-1.5 py-0.5 rounded">
                                                    Trang {src.pageNumber}
                                                </span>
                                            )}
                                        </div>

                                        <div className="flex items-center gap-2">
                                            <span className="text-[10px] text-slate-400 font-mono">Độ tương đồng:</span>
                                            <span className="text-xs font-bold text-emerald-400 font-mono">
                                                {(src.score * 100).toFixed(1)}%
                                            </span>
                                        </div>
                                    </div>

                                    <div className="w-full h-1 bg-slate-800 rounded-full mb-2 overflow-hidden">
                                        <div
                                            className="h-full bg-gradient-to-r from-violet-500 to-emerald-500"
                                            style={{ width: `${Math.min(src.score * 100, 100)}%` }}
                                        />
                                    </div>

                                    <div className="bg-slate-950/40 p-2.5 rounded border border-slate-800/20">
                                        <p className="text-slate-400 italic text-[11px] leading-relaxed select-all">
                                            "... {src.textSnippet} ..."
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>

            {msg.role === 'user' && (
                <div className="w-9 h-9 rounded-full bg-violet-600 flex items-center justify-center text-white font-semibold flex-shrink-0">
                    {username.charAt(0).toUpperCase()}
                </div>
            )}
        </div>
    );
}
