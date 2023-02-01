import {Log, DebugContext, getServiceName, DebugContextType, getGenerationName} from "./Schema";

let socket: WebSocket | null = null;
let options: SocketOptions;

interface SocketOptions {
    onConnect: () => void;
    onDisconnect: () => void;
    onLogs: () => void;
    onContext: () => void;
    onService: () => void;
}

export function initializeConnection(opts: SocketOptions) {
    if (socket != null) throw Error("initializeConnection has already been called!");
    options = opts;
    initializeSocket();
}

function initializeSocket() {
    socket = new WebSocket("ws://localhost:5511");
    socket.binaryType = "arraybuffer";

    socket.onopen = () => {
        options.onConnect();
    };

    socket.onclose = () => {
        options.onDisconnect();

        setTimeout(() => {
            initializeSocket();
        }, 1000);
    };

    socket.onmessage = (e) => {
        if (!(e.data instanceof ArrayBuffer)) return;
        const message = new Uint8Array(e.data);
        if (message.length < 1) return;

        const view = new DataView(e.data);
        switch (Number(view.getBigInt64(0, false))) {
            case 1:
                const service = getServiceName(view);
                const generation = getGenerationName(view);
                serviceStore.add(service, generation);
                break;

            case 2:
                const numberOfEntries = (message.length - 8) / 388;
                for (let i = 0; i < numberOfEntries; i++) {
                    const ctx = new DebugContext(view, 8 + i * 388);
                    logStore.addDebugContext(ctx);
                }
                logStore.emitChange();
                break;

            case 3: {
                const numberOfEntries = (message.length - 8) / 256;
                const messages: string[] = []
                for (let i = 0; i < numberOfEntries; i++) {
                    const log = new Log(view, 8 + i * 256);
                    messages.push(log.message.previewOrContent);
                    logStore.addLog(log);
                }
                console.log(messages);
                logStore.emitChange();
                console.log(logStore.ctxMap);
                console.log(logStore.entryCount);
                break;
            }
            default: {
                console.log(Number(view.getBigInt64(0, false)))
            }
        }
    };
}

export const activeService = new class {
    private activeService: string = "";
    private activeGeneration: string = "";
    private subscriptions: (() => void)[] = [];

    public get service() {
        return this.activeService;
    }

    public get generation() {
        return this.activeGeneration;
    }

    public setService(service: string): void {
        this.activeService = service;
        this.activeGeneration = serviceStore.getGeneration(service);
        if (isSocketReady(socket)) {
            socket.send(activateServiceRequest(service));
            this.emitChange();
        }
    }

    public subscribe(subscription: () => void) {
        this.subscriptions = [...this.subscriptions, subscription];
        return () => {
            this.subscriptions = this.subscriptions.filter(s => s !== subscription);
        };
    }

    public getSnapshot(): string {
        return this.activeService;
    }

    public emitChange(): void {
        for (let subscriber of this.subscriptions) {
            subscriber();
        }
    }
}();

export interface DebugContextAndChildren {
    ctx: DebugContext;
    children: (Log | DebugContextAndChildren)[]
}

export function isLog(input: Log | DebugContext | DebugContextAndChildren): input is Log {
    return "ctxId" in input;
}

export const logStore = new class {
    private logs: {content: Record<string, DebugContext[]>} = {content: {}};
    private activeContexts: DebugContextAndChildren | null = null;
    public ctxMap: Record<number, DebugContextAndChildren> = {};
    private subscriptions: (() => void)[] = [];
    private isDirty = false;
    public entryCount = 0;

    public contextRoot(): DebugContextAndChildren | null {
        return this.activeContexts;
    }

    public clearActiveContext(): void {
        this.ctxMap = {};
        this.activeContexts = null;
        this.entryCount = 0;

        activateServiceRequest(null);
        resetMessages();
    }

    public addDebugRoot(debugContext: DebugContext): void {
        const newRoot = {ctx: debugContext, children: []};
        this.activeContexts = newRoot;
        this.ctxMap[debugContext.id] = newRoot;
        this.entryCount++;
        this.emitChange();
    }

    public addDebugContext(debugContext: DebugContext): void {
        this.isDirty = true;
        if (this.activeContexts) {
            const newEntry = {ctx: debugContext, children: []};
            this.ctxMap[debugContext.parent].children.push(newEntry);
            this.ctxMap[debugContext.parent].children.sort(logOrCtxSort);
            if (this.ctxMap[debugContext.id]) {
                console.log("Already filled!", debugContext.id);
            }
            this.ctxMap[debugContext.id] = newEntry;
            this.entryCount++;
            return;
        }

        if (!this.logs.content[activeService.service]) {
            this.logs.content[activeService.service] = [debugContext];
        } else {
            this.logs.content[activeService.service].push(debugContext)
        }
    }

    public addLog(log: Log): void {
        if (this.activeContexts) {
            this.ctxMap[log.ctxId].children.push(log);
            this.ctxMap[log.ctxId].children.sort(logOrCtxSort);
            this.entryCount++;
        }
        this.isDirty = true;
    }

    public subscribe(subscription: () => void) {
        this.subscriptions = [...this.subscriptions, subscription];
        return () => {
            this.subscriptions = this.subscriptions.filter(s => s !== subscription);
        };
    }

    public getSnapshot(): {content: Record<string, DebugContext[]>} {
        if (this.isDirty) {
            this.isDirty = false;
            return this.logs = {content: this.logs.content};
        }
        return this.logs;
    }

    public emitChange(): void {
        for (const subscriber of this.subscriptions) {
            subscriber();
        }
    }
}();

function logOrCtxSort(a: (Log | DebugContextAndChildren), b: (Log | DebugContextAndChildren)): number {
    const timestampA = isLog(a) ? a.timestamp : a.ctx.timestamp;
    const timestampB = isLog(b) ? b.timestamp : b.ctx.timestamp;
    return timestampA - timestampB;
}

export const serviceStore = new class {
    private services: string[] = [];
    private generations: Record<string, string> = {};
    private subscriptions: (() => void)[] = [];

    public add(entry: string, generation: string): void {
        this.services = [...this.services, entry];
        this.generations[entry] = generation;
        this.emitChange();
    }

    public getGeneration(service: string): string {
        return this.generations[service] ?? "";
    }

    public subscribe(subscription: () => void) {
        this.subscriptions = [...this.subscriptions, subscription];
        return () => {
            this.subscriptions = this.subscriptions.filter(s => s !== subscription);
        };
    }

    public getSnapshot(): string[] {
        return this.services;
    }

    public emitChange(): void {
        for (let subscriber of this.subscriptions) {
            subscriber();
        }
    }
}();

function activateServiceRequest(service: string | null): string {
    return JSON.stringify({
        type: "activate_service",
        service,
    })
}

function resetMessages(): void {
    if (!isSocketReady(socket)) return;
    socket.send(JSON.stringify({type: "clear_active_context"}));
}

export function replayMessages(generation: string, context: number, timestamp: number): void {
    if (!isSocketReady(socket)) return;
    socket.send(replayMessagesRequest(generation, context, timestamp));
}

function replayMessagesRequest(generation: string, context: number, timestamp: number): string {
    return JSON.stringify({
        type: "replay_messages",
        generation,
        context,
        timestamp,
    });
}

export function setSessionState(query: string, filters: Set<DebugContextType>, level: string): void {
    if (!isSocketReady(socket)) return;
    const debugContextFilters: string[] = [];
    filters.forEach(entry => {
        debugContextFilters.push(DebugContextType[entry]);
    });
    const req = setSessionStateRequest(
        nullIfEmpty(query),
        nullIfEmpty(debugContextFilters),
        nullIfEmpty(level)
    );
    console.log(req);
    socket.send(req);
}

interface WithLength {
    length: number;
}

function nullIfEmpty<T extends WithLength>(f: T): T | null {
    return f.length === 0 ? null : f;
}

export function setSessionStateRequest(query: string | null, filters: string[] | null, level: string | null): string {
    return JSON.stringify({
        type: "set_session_state",
        query: query,
        filters: filters,
        level: level,
    });
}

function isSocketReady(socket: WebSocket | null): socket is WebSocket {
    return !!(socket && socket.readyState === socket.OPEN);
}

export function isSocketOpen(): boolean {
    return isSocketReady(socket);
}
