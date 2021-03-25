export interface Task {
    jobId: string;
    owner: string;
    processor: string;
    title: string | null;
    status: string | null;
    complete: boolean;
    startedAt: number;
}

export interface TaskUpdate {
    jobId: string;
    newTitle: string | null;
    speeds: Speed[];
    progress: Progress | null;
    complete: boolean;
    messageToAppend: string | null;
    newStatus: string | null;
}

export interface Speed {
    title: string;
    speed: number;
    unit: string;
    asText: string;
    clientTimestamp?: number;
}

export interface Progress {
    title: string;
    current: number;
    maximum: number;
}
