import {Log, DebugContext, getServiceName, DebugContextType, getGenerationName, readInt4} from "./Schema";

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
    socket = new WebSocket("wss://debugger-api.localhost.direct");
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
                for (let i = 0; i < numberOfEntries; i++) {
                    const log = new Log(view, 8 + i * 256);
                    logStore.addLog(log);
                }
                logStore.emitChange();
                break;
            }
            case 4: {
                // Starts with Long for type (4)
                // Followed by size (int)
                // Followed by text of length size.
                const ID_SIZE = 4;
                const TYPE_SIZE = 8;
                const id = readInt4(view, 8)
                const textDecoder = new TextDecoder();
                const u8a = new Uint8Array(view.buffer);
                let slice = u8a.slice(TYPE_SIZE + ID_SIZE);
                const text = textDecoder.decode(slice)

                logMessages.addMessage(text, id);
                break;
            }
            default: {
                console.log("preceding number we got", Number(view.getBigInt64(0, false)))
            }
        }
    };
}


type LogMessageExtraCache = Record<string, string>;
type LogMessageObject = Record<string, LogMessageExtraCache>
// TODO(Jonas): We probably need this as a service, and emit the change.
export const logMessages = new class {
    private messages: LogMessageObject = {};
    private subscriptions: (() => void)[] = [];
    private generation = 0;

    public addMessage(message: string, id: number) {
        if (!this.messages[activeService.service]) {
            this.messages[activeService.service] = {};
        }
        this.messages[activeService.service][id] = message;
        this.generation++;
        this.emitChange();
    }

    public get(id: string | undefined): string | undefined {
        if (id === undefined) return undefined;
        return this.messages[activeService.service]?.[id];
    }

    public has(id: string): boolean {
        return this.messages[activeService.service]?.[id] !== undefined;
    }

    public getSnapshot(): number {
        return this.generation;
    }

    public subscribe(subscription: () => void) {
        this.subscriptions = [...this.subscriptions, subscription];
        return () => {
            this.subscriptions = this.subscriptions.filter(s => s !== subscription);
        };
    }

    public emitChange(): void {
        for (const subscriber of this.subscriptions) {
            subscriber();
        }
    }
}();

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
        const oldService = this.activeService;
        this.activeService = service;
        this.activeGeneration = serviceStore.getGeneration(service);
        if (service && oldService !== service && isSocketReady(socket)) {
            socket.send(activateServiceRequest(service, this.activeGeneration));
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
        for (const subscriber of this.subscriptions) {
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

type ContextMap = Record<string, DebugContext[]>;
interface LogStoreContexts {
    content: ContextMap;
}
export const logStore = new class {
    private logs: LogStoreContexts = {content: {}};
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
            const ctxTimestamp = debugContext.timestamp;
            const earliestTimestamp = this.earliestTimestamp();
            const latestTimestamp = this.latestTimestamp();

            if (ctxTimestamp < earliestTimestamp) {
                this.logs.content[activeService.service].unshift(debugContext);
            } else if (ctxTimestamp > latestTimestamp) {
                this.logs.content[activeService.service].push(debugContext);
            } else {
                this.logs.content[activeService.service].push(debugContext);
                this.logs.content[activeService.service].sort(contextSort);
            }
        }
    }

    public earliestContext(): DebugContext | undefined {
        return this.logs.content[activeService.service]?.at(0);
    }

    public earliestTimestamp(): number {
        return this.earliestContext()?.timestamp ?? new Date().getTime();
    }

    public latestTimestamp(): number {
        return this.logs.content[activeService.service]?.at(-1)?.timestamp ?? new Date().getTime();
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

function contextSort(a: DebugContext, b: DebugContext): number {
    return a.timestamp - b.timestamp;
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

type Nullable<T> = T | null;

function activateServiceRequest(service: Nullable<string>, generation: Nullable<string>): string {
    return JSON.stringify({
        type: "activate_service",
        service,
        generation
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

export function fetchPreviousMessage(): void {
    const ctx = logStore.earliestContext();
    if (ctx == null) return;
    if (!isSocketReady(socket)) return;
    socket.send(fetchPreviousMessagesRequest(ctx));
}

function fetchPreviousMessagesRequest(ctx: DebugContext): string {
    return JSON.stringify({
        type: "fetch_previous_messages",
        timestamp: ctx.timestamp,
        id: ctx.id,
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

export function fetchTextBlob(generation: string, blobId: string, fileIndex: string): void {
    if (!isSocketReady(socket)) return;
    const req = fetchTextBlobRequest(generation, blobId, fileIndex);
    socket.send(req);
}

function fetchTextBlobRequest(generation: string, blobId: string, fileIndex: string) {
    return JSON.stringify({
        type: "fetch_text_blob",
        generation,
        id: blobId,
        fileIndex,
    });
}

function isSocketReady(socket: WebSocket | null): socket is WebSocket {
    return !!(socket && socket.readyState === socket.OPEN);
}

export function isSocketOpen(): boolean {
    return isSocketReady(socket);
}
