export interface ChatSession {
    id: string;
    username: string;
    title: string;
    createdAt: string;
}

export interface ChatSource {
    title: string;
    chunkIndex: number;
    pageNumber: number | null;
    score: number;
    textSnippet: string;
}

export interface ChatMessage {
    role: 'user' | 'assistant';
    content: string;
    sources?: ChatSource[];
    timestamp: string;
}

export interface DocumentStatus {
    documentId: string;
    title: string;
    originalFilename: string;
    storedFilePath: string | null;
    fileSizeBytes: number;
    status: 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED';
    createdAt: string;
    updatedAt: string;
    finishedAt: string | null;
    sourceFileExists: boolean;
    reindexable: boolean;
    errorMessage: string | null;
    chunkCount: number;
    owner?: string;
    isPublic?: boolean;
}

export interface SystemComponent {
    status: 'UP' | 'DOWN' | 'READY' | 'NOT_READY';
    details: Record<string, any>;
}

export interface SystemHealth {
    status: 'UP' | 'DOWN';
    checkedAt: string;
    ollama: SystemComponent;
    qdrant: SystemComponent;
    chatModel: SystemComponent;
    embeddingModel: SystemComponent;
}
