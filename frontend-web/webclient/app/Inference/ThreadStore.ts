import * as React from "react";
import {callAPI} from "@/Authentication/DataHook";
import {listPlaygroundThreads, PlaygroundThread} from "./api";

const POLL_INTERVAL = 60_000;

class InferenceThreadStore {
    private listeners = new Set<() => void>();
    private threads: PlaygroundThread[] = [];
    private liveSessionCount = 0;

    subscribe = (listener: () => void): (() => void) => {
        this.listeners.add(listener);
        return () => this.listeners.delete(listener);
    };

    getSnapshot = (): PlaygroundThread[] => this.threads;

    update(threads: PlaygroundThread[]): void {
        this.threads = [...threads].sort((a, b) => b.updatedAt - a.updatedAt);
        this.listeners.forEach(listener => listener());
    }

    async refresh(): Promise<void> {
        if (this.liveSessionCount > 0) return;
        const response = await callAPI(listPlaygroundThreads({providerId: null}));
        if (this.liveSessionCount === 0) this.update(response.threads);
    }

    beginLiveSession(): () => void {
        this.liveSessionCount++;
        return () => {
            this.liveSessionCount--;
        };
    }
}

export const inferenceThreadStore = new InferenceThreadStore();

export function useInferenceThreads(): PlaygroundThread[] {
    const threads = React.useSyncExternalStore(inferenceThreadStore.subscribe, inferenceThreadStore.getSnapshot);

    React.useEffect(() => {
        void inferenceThreadStore.refresh().catch(() => {});
        const interval = window.setInterval(() => void inferenceThreadStore.refresh().catch(() => {}), POLL_INTERVAL);
        return () => window.clearInterval(interval);
    }, []);

    return threads;
}
