import {
    contextSort, debugMessageOrCtxSort, nullIfEmpty, pushStateToHistory, toNumberOrUndefined
} from "../Utilities/Utilities";
import {
    activateServiceRequest, fetchPreviousMessagesRequest, fetchTextBlobRequest,
    replayMessagesRequest, resetMessagesRequest, setSessionStateRequest
} from "./Requests";
import {
    Log, DebugContext, getServiceName, DebugContextType, getGenerationName, readInt4,
    BinaryDebugMessageType, DebugMessage, ClientRequest, ClientResponse, DatabaseQuery,
    DatabaseResponse, DatabaseTransaction, ServerRequest, ServerResponse
} from "./Schema";

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

const FRAME_SIZE = 256;

function initializeSocket() {
    if (window.location.host === "localhost") {
        socket = new WebSocket("ws://localhost:5511");
    } else {
        socket = new WebSocket("wss://debugger-api.localhost.direct");
    }
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
                    debugMessageStore.addDebugContext(ctx);
                }
                debugMessageStore.emitChange();
                break;

            case 3: {
                const numberOfEntries = (message.length - 8) / FRAME_SIZE;
                for (let i = 0; i < numberOfEntries; i++) {
                    const offset = 8 + i * FRAME_SIZE;
                    const debugMessage = debugMessageFromType(view, offset)
                    debugMessageStore.addLog(debugMessage);
                }
                if (numberOfEntries > 0) {debugMessageStore.emitChange();}
                break;
            }
            case 4: {
                // Starts with Long for type (8)
                // Followed by size in int (4)
                // Followed by text of length size.
                const ID_SIZE = 4;
                const TYPE_SIZE = 8;
                const id = readInt4(view, TYPE_SIZE);
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

function debugMessageFromType(view: DataView, offset: number): DebugMessage {
    const type: BinaryDebugMessageType = Number(view.getInt8(offset))
    switch (type) {
        case BinaryDebugMessageType.CLIENT_REQUEST:
            return new ClientRequest(view, offset);
        case BinaryDebugMessageType.CLIENT_RESPONSE:
            return new ClientResponse(view, offset);
        case BinaryDebugMessageType.SERVER_REQUEST:
            return new ServerRequest(view, offset);
        case BinaryDebugMessageType.SERVER_RESPONSE:
            return new ServerResponse(view, offset);
        case BinaryDebugMessageType.DATABASE_TRANSACTION:
            return new DatabaseTransaction(view, offset);
        case BinaryDebugMessageType.DATABASE_QUERY:
            return new DatabaseQuery(view, offset);
        case BinaryDebugMessageType.DATABASE_RESPONSE:
            return new DatabaseResponse(view, offset);
        case BinaryDebugMessageType.LOG:
            return new Log(view, offset);
    }
}


type LogMessageExtraCache = Record<string, string>;
type LogMessageObject = Record<string, LogMessageExtraCache>

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

interface HistoryWatcherService {
    activeContext?: Pick<DebugContext, "id" | "timestamp">;
    shouldClearRoot: boolean;
}
export const historyWatcherService = new class {
    public info: HistoryWatcherService = {
        activeContext: undefined,
        shouldClearRoot: false
    }
    private subscriptions: (() => void)[] = [];

    public subscribe(subscription: () => void) {
        this.subscriptions = [...this.subscriptions, subscription];
        return () => {
            this.subscriptions = this.subscriptions.filter(s => s !== subscription);
        };
    }

    public getSnapshot(): HistoryWatcherService {
        return this.info;
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

    public setService(service: string, generation?: string): void {
        const oldService = this.activeService;
        this.activeService = service;
        this.activeGeneration = generation ?? serviceStore.getGeneration(service);
        if (service && oldService !== service && isSocketReady(socket)) {
            activateService(service, this.activeGeneration);
            pushStateToHistory(service, this.activeGeneration, historyWatcherService.info.activeContext);
        }
        this.emitChange();
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
    children: (DebugMessage | DebugContextAndChildren)[]
}

type ContextMap = Record<string, DebugContext[]>;
interface LogStoreContexts {
    content: ContextMap;
}
export const debugMessageStore = new class {
    private debugMessages: LogStoreContexts = {content: {}};
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
        this.emitChange();
        resetMessages();
    }

    public addDebugRoot(debugContext: DebugContext): void {
        const newRoot = {ctx: debugContext, children: []};
        this.activeContexts = newRoot;
        this.ctxMap[debugContext.id] = newRoot;
        this.entryCount++;
    }

    public addDebugContext(debugContext: DebugContext): void {
        this.isDirty = true;


        if (this.activeContexts) {
            const newEntry = {ctx: debugContext, children: []};
            this.ctxMap[debugContext.parent].children.push(newEntry);
            this.ctxMap[debugContext.parent].children.sort(debugMessageOrCtxSort);
            if (this.ctxMap[debugContext.id]) {
                console.log("Already filled! This shouldn't happen!", debugContext.id);
            }
            this.ctxMap[debugContext.id] = newEntry;
            this.entryCount++;
            return;
        }

        if (!this.debugMessages.content[activeService.service]) {
            this.debugMessages.content[activeService.service] = [debugContext];
        } else {
            const ctxTimestamp = debugContext.timestamp;
            const earliestTimestamp = this.earliestTimestamp();
            const latestTimestamp = this.latestTimestamp();

            if (ctxTimestamp < earliestTimestamp) {
                this.debugMessages.content[activeService.service].unshift(debugContext);
            } else if (ctxTimestamp > latestTimestamp) {
                this.debugMessages.content[activeService.service].push(debugContext);
            } else {
                this.debugMessages.content[activeService.service].push(debugContext);
                this.debugMessages.content[activeService.service].sort(contextSort);
            }
        }
    }

    public earliestContext(): DebugContext | undefined {
        return this.debugMessages.content[activeService.service]?.at(0);
    }

    public earliestTimestamp(): number {
        return this.earliestContext()?.timestamp ?? new Date().getTime();
    }

    public latestTimestamp(): number {
        return this.debugMessages.content[activeService.service]?.at(-1)?.timestamp ?? new Date().getTime();
    }

    public addLog(message: DebugMessage): void {
        if (this.activeContexts) {
            this.ctxMap[message.ctxId].children.push(message);
            this.ctxMap[message.ctxId].children.sort(debugMessageOrCtxSort);
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
            return this.debugMessages = {content: this.debugMessages.content};
        }
        return this.debugMessages;
    }

    public emitChange(): void {
        for (const subscriber of this.subscriptions) {
            subscriber();
        }
    }
}();

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

/*
 * CALLS
 **/

function activateService(service: Nullable<string>, generation: Nullable<string>) {
    if (!isSocketReady(socket)) return;
    socket.send(activateServiceRequest(service, generation));
}

function resetMessages(): void {
    if (!isSocketReady(socket)) return;
    socket.send(resetMessagesRequest());
}

export function replayMessages(generation: string, context: number, timestamp: number): void {
    if (!isSocketReady(socket)) return;
    socket.send(replayMessagesRequest(generation, context, timestamp));
}

export function fetchPreviousMessage(): void {
    const ctx = debugMessageStore.earliestContext();
    if (!isSocketReady(socket)) return;
    socket.send(fetchPreviousMessagesRequest(ctx != null ? ctx : {timestamp: new Date().getTime(), id: 2}, false));
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

export function fetchTextBlob(generation: string, blobId: string, fileIndex: string): void {
    if (!isSocketReady(socket)) return;
    const req = fetchTextBlobRequest(generation, blobId, fileIndex);
    socket.send(req);
}

/*
 * CALLS
 **/

function isSocketReady(socket: WebSocket | null): socket is WebSocket {
    return !!(socket && socket.readyState === socket.OPEN);
}

export function isSocketOpen(): boolean {
    return isSocketReady(socket);
}

window.onpopstate = e => {
    const {service, generation, context} = e.state as {
        service?: string;
        generation?: string;
        context?: Pick<DebugContext, "id" | "timestamp">
    };
    activeService.setService(service ?? "", generation ?? "");
    historyWatcherService.info.activeContext = context;
    if (context == null) {
        historyWatcherService.info.shouldClearRoot = true;
        debugMessageStore.clearActiveContext();
        activeService.emitChange();
    }
}

/**
 * WINDOW-EVENTS
 **/

window.onpageshow = () => {
    const {pathname} = window.location;
    const [generation, contextId, contextTimestamp] = pathname.split("/").filter(it => it);
    const service = window.location.hash.length > 0 ? window.location.hash.slice(1) : "";

    const parsedCtxId = toNumberOrUndefined(contextId);
    const parsedCtxTs = toNumberOrUndefined(contextTimestamp);

    const ctxInfo = parsedCtxId && parsedCtxTs ? {
        id: parsedCtxId,
        timestamp: parsedCtxTs,
    } : undefined;

    pushStateToHistory(service, generation, ctxInfo);
    historyWatcherService.info.activeContext = ctxInfo;
    updateWhenReady(() => {
        activeService.setService(service ?? "", generation);
        if (service && generation) {
            if (parsedCtxId && parsedCtxTs) {
                if (!isSocketReady(socket)) return;
                socket.send(fetchPreviousMessagesRequest({timestamp: parsedCtxTs, id: parsedCtxId}, true));
            }
        }
    });
}

/**
 * WINDOW-EVENTS
 **/

function updateWhenReady(func: () => void): void {
    if (isSocketReady(socket)) {
        func();
    } else {
        window.setTimeout(() => updateWhenReady(func), 400);
    }
}
