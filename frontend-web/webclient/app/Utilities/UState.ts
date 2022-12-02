import { useForcedRender } from "@/Utilities/ReactUtilities";
import * as React from "react";

export abstract class UState<Self extends UState<Self>> {
    protected defaultDispatcher: QueuedAsyncDispatcher;
    private listeners: ((data: Self) => void)[] = [];

    constructor(defaultDispatcherSize: number = 1) {
        this.defaultDispatcher = new QueuedAsyncDispatcher(defaultDispatcherSize);
    }

    protected notifyChange() {
        const self = this as unknown as Self;
        for (const listener of this.listeners) {
            listener(self);
        }
    }

    public get loading(): boolean {
        return this.defaultDispatcher.loading;
    }

    subscribe(listener: (data: Self) => void) {
        this.listeners.push(listener);
    }

    unsubscribe(listener: (data: Self) => void) {
        const idx = this.listeners.indexOf(listener);
        if (idx !== -1) {
            this.listeners.splice(idx, 1);
        }
    }

    protected async run<T>(fn: () => Promise<T>): Promise<T> {
        const res = await this.defaultDispatcher.dispatch(() => {
            this.notifyChange();
            return fn();
        });

        this.notifyChange();
        return res;
    }
}

export function useUState<State extends UState<State>>(state: State): State {
    const forceRender = useForcedRender();
    React.useEffect(() => {
        const listener = () => {
            forceRender();
        };

        state.subscribe(listener);
        return () => { state.unsubscribe(listener); };
    }, []);
    return state;
}

class QueuedAsyncDispatcher {
    private maxQueueSize: number;
    private rejectOnQueueFull: boolean;
    private headOfQueue: Promise<unknown> | null = null;
    private queue: (() => Promise<unknown>)[] = [];

    constructor(maxQueueSize: number = 5, rejectOnQueueFull: boolean = false) {
        if (maxQueueSize <= 0) throw `maxQueueSize must be at least one but was ${maxQueueSize}`;
        this.maxQueueSize = maxQueueSize;
        this.rejectOnQueueFull = rejectOnQueueFull;
    }

    private activeProcessingSize() {
        const activeRightNow = this.headOfQueue !== null ? 1 : 0;
        return this.queue.length + activeRightNow;
    }

    public dispatch<T>(fn: () => Promise<T>): Promise<T> {
        if (this.activeProcessingSize() >= this.maxQueueSize) {
            if (this.rejectOnQueueFull) {
                return Promise.reject("Queue is full");
            } else {
                return this.headOfQueue as Promise<T>;
            }
        } else {
            return new Promise((resolve, reject) => {
                // Using a setTimeout here guarantees that we do not cause a setState while attempting to render a 
                // value. This would typicially happen if a UState caches a value and wants to check if the value is 
                // up-to-date and potentially re-fetch a new one.
                setTimeout(() => {
                    this.queue.push(() => {
                        return fn()
                            .finally(() => {
                                this.headOfQueue = null;
                                this.checkQueue();
                            })
                            .then(it => resolve(it))
                            .catch(it => reject(it));
                        }
                    );

                    this.checkQueue();
                }, 0);
            });
        }
    }

    public get loading() {
        return this.headOfQueue !== null;
    }

    private checkQueue() {
        if (this.headOfQueue === null && this.queue.length > 0) {
            const [headFn] = this.queue.splice(0, 1);
            this.headOfQueue = headFn();
        }
    }
}
