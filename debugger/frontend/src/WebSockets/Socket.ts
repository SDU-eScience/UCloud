import {
    contextSort, debugMessageOrCtxSort, nullIfEmpty, pushStateToHistory, toNumberOrUndefined
} from "../Utilities/Utilities";
import {
    Log, DebugContext, getServiceName, DebugContextType, getGenerationName, readInt32,
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
            case 1: {
                const numberOfEntries = (message.length - 8) / 256;
                for (let i = 0; i < numberOfEntries; i++) {
                    const service = getServiceName(view, 8 + (i * 256));
                    const generation = getGenerationName(view, 8 + (i * 256));
                    serviceStore.add(service, generation);
                }
                serviceStore.emitChange();
                break;
            }

            case 2: {
                const numberOfEntries = (message.length - 8) / 388;
                for (let i = 0; i < numberOfEntries; i++) {
                    const ctx = new DebugContext(view, 8 + i * 388);
                    debugMessageStore.addDebugContext(ctx);
                }
                debugMessageStore.emitChange();
                break;
            }

            case 3: {
                const numberOfEntries = (message.length - 8) / FRAME_SIZE;
                for (let i = 0; i < numberOfEntries; i++) {
                    const offset = 8 + i * FRAME_SIZE;
                    const debugMessage = debugMessageFromType(view, offset)
                    debugMessageStore.addLog(debugMessage);
                }

                if (numberOfEntries > 0) debugMessageStore.emitChange();
                break;
            }
            case 4: {
                // Starts with Long for type (8)
                // Followed by size in int (4)
                // Followed by text of length size.
                const ID_SIZE = 4;
                const TYPE_SIZE = 8;
                const id = readInt32(view, TYPE_SIZE);
                const textDecoder = new TextDecoder();
                const u8a = new Uint8Array(view.buffer);
                let slice = u8a.slice(TYPE_SIZE + ID_SIZE);
                const text = textDecoder.decode(slice)

                blobMessages.addMessage(text, id);
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


type BlobMessageExtraCache = Record<string, string>;
type BlobMessageObject = Record<string, BlobMessageExtraCache>;

export const blobMessages = new class {
    private blobs: BlobMessageObject = {};
    private subscriptions: (() => void)[] = [];
    private generation = 0;

    public addMessage(message: string, id: number) {
        if (!this.blobs[activeService.service]) {
            this.blobs[activeService.service] = {};
        }
        this.blobs[activeService.service][id] = message;
        this.generation++;
        this.emitChange();
    }

    public get(id: string | undefined): string | undefined {
        if (id === undefined) return undefined;
        return this.blobs[activeService.service]?.[id];
    }

    public has(id: string): boolean {
        return this.blobs[activeService.service]?.[id] !== undefined;
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
    service?: string;
    generation?: string;
    contextId?: number;
    contextTimestamp?: number;
}
export const historyWatcherService = new class {
    public info: HistoryWatcherService = {};
    private subscriptions: (() => void)[] = [];

    constructor() {
        window.onpopstate = () => {
            this.onUrlChanged();
        };

        window.onpageshow = () => {
            this.onUrlChanged();
        };
    }

    public onUrlChanged() {
        const {pathname} = window.location;
        const [generation, contextId, contextTimestamp] = pathname.split("/").filter(it => it);
        const service = window.location.hash.length > 0 ? window.location.hash.slice(1) : "";

        const parsedCtxId = toNumberOrUndefined(contextId);
        const parsedCtxTs = toNumberOrUndefined(contextTimestamp);

        this.info = {
            contextId: parsedCtxId,
            contextTimestamp: parsedCtxTs,
            service: service,
            generation,
        };

        this.emitChange();
    }

    public subscribe(subscription: () => void) {
        this.subscriptions = [...this.subscriptions, subscription];
        let running = true;
        updateWhenReady(() => {
            if (running) subscription();
        });
        return () => {
            running = false;
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

    constructor() {
        historyWatcherService.subscribe(() => {
            const info = historyWatcherService.info;
            if (info.service) {
                this.setService(info.service, info.generation);
            } else {
                this.clearService();
            }
        });
    }

    public get service() {
        return this.activeService;
    }

    public get generation() {
        return this.activeGeneration;
    }

    private clearService() {
        this.activeService = "";
        this.activeGeneration = "";
        this.emitChange();
    }

    private setService(service: string, generation?: string, attempts: number = 0): void {
        if (attempts > 6) return;

        if (!isSocketReady(socket)) {
            window.setTimeout(() => this.setService(service, generation, attempts + 1), 500);
            return;
        }

        const oldService = this.activeService;
        this.activeService = service;
        this.activeGeneration = generation ?? serviceStore.getGeneration(service);
        if (service && oldService !== service && isSocketReady(socket)) {
            sendActivateService(service, this.activeGeneration);
            this.emitChange();
        } else {
            window.setTimeout(() => this.setService(service, generation, attempts + 1), 500);
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
    private activeAttempts: number = -1;

    public contextRoot(): DebugContextAndChildren | null {
        return this.activeContexts;
    }

    constructor() {
        historyWatcherService.subscribe(() => {
            if (this.activeAttempts !== -1) {
                window.clearTimeout(this.activeAttempts);
                this.activeAttempts = -1;
            }

            const info = historyWatcherService.info;
            this.attemptToFindContext(info);
        });
    }

    private attemptToFindContext(info: HistoryWatcherService, attempts: number = 0) {
        if (attempts > 3) {
            // If we don't the context then push to the page without the context. We could
            // potentially show a message stating that we couldn't find the context.
            pushStateToHistory(info.service, info.generation);
            return;
        }

        if (info.service) {
            const contexts = this.debugMessages.content[info.service];

            if (info.contextId) {
                if (contexts) {
                    const theContext = contexts.find(it => it.id === info.contextId)
                    if (theContext) {
                        this.setActiveContext(theContext);
                        return
                    }
                }

                this.activeAttempts = window.setTimeout(() => this.attemptToFindContext(info, attempts + 1), 1000);
            } else {
                this.clearActiveContext();
            }
        }
    }

    clearActiveContext(): void {
        console.log("clearing context");
        this.ctxMap = {};
        this.activeContexts = null;
        this.entryCount = 0;
        this.debugMessages.content = {};
        this.emitChange();
        sendClearActiveContext();
    }

    clearChildren() {
        const currentRoot = this.activeContexts?.ctx;
        if (!currentRoot) return;
        const newRoot = {ctx: currentRoot, children: []};
        this.activeContexts = newRoot;
        this.ctxMap[currentRoot.id] = newRoot;
        this.entryCount = 0;
        this.isDirty = true;
        this.emitChange();
    }

    private setActiveContext(debugContext: DebugContext): void {
        const newRoot = {ctx: debugContext, children: []};
        this.activeContexts = newRoot;
        this.ctxMap[debugContext.id] = newRoot;
        this.entryCount++;

        this.emitChange();
        sendReplayMessages(activeService.generation, debugContext.id, debugContext.timestamp);
    }

    public addDebugContext(debugContext: DebugContext): void {
        console.log("adding context")
        this.isDirty = true;

        if (this.activeContexts) {
            const newEntry = {ctx: debugContext, children: []};
            this.ctxMap[debugContext.parent].children.push(newEntry);
            this.ctxMap[debugContext.parent].children.sort(debugMessageOrCtxSort);
            this.ctxMap[debugContext.id] = newEntry;
            this.entryCount++;
            return;
        }

        if (!this.debugMessages.content[activeService.service]) {
            this.debugMessages.content[activeService.service] = [debugContext];
        } else {
            // NOTE(Dan): Push the context while preserving correct order in the array
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
        console.log("emit change");
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

function sendActivateService(service: Nullable<string>, generation: Nullable<string>) {
    if (!isSocketReady(socket)) return;
    socket.send(JSON.stringify({
        type: "activate_service",
        service,
        generation
    }));
}

export function sendClearActiveContext(): void {
    if (!isSocketReady(socket)) return;
    socket.send(JSON.stringify({type: "clear_active_context"}));
}

export function sendReplayMessages(generation: string, context: number, timestamp: number): void {
    if (!isSocketReady(socket)) return;
    socket.send(
        JSON.stringify({
            type: "replay_messages",
            generation,
            context,
            timestamp,
        })
    );
}

export function sendFetchPreviousMessages(): void {
    const ctx = debugMessageStore.earliestContext();
    if (!isSocketReady(socket)) return;

    socket.send(
        JSON.stringify({
            type: "fetch_previous_messages",
            timestamp: ctx?.timestamp ?? new Date().getTime(),
            id: ctx?.id ?? 2,
            onlyFindSelf: false
        })
    );
}

export function sendSetSessionState(query: string, filters: Set<DebugContextType>, level: string): void {
    if (!isSocketReady(socket)) return;
    const debugContextFilters: string[] = [];
    filters.forEach(entry => {
        debugContextFilters.push(DebugContextType[entry]);
    });

    socket.send(JSON.stringify({
        type: "set_session_state",
        query: nullIfEmpty(query),
        filters: nullIfEmpty(debugContextFilters),
        level: nullIfEmpty(level),
    }));
}

export function sendFetchTextBlob(generation: string, blobId: string, fileIndex: string): void {
    if (!isSocketReady(socket)) return;
    socket.send(JSON.stringify({
        type: "fetch_text_blob",
        generation,
        id: blobId,
        fileIndex,
    }));
}

function isSocketReady(socket: WebSocket | null): socket is WebSocket {
    return !!(socket && socket.readyState === socket.OPEN);
}

function updateWhenReady(func: () => void): void {
    if (isSocketReady(socket)) {
        func();
    } else {
        window.setTimeout(() => updateWhenReady(func), 400);
    }
}
