import {Log, DebugContext, getServiceName, debugContextToString, messageImportanceToString, binaryDebugMessageTypeToString} from "./Schema";

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
                serviceStore.attemptAdd(service);
                break;

            case 2:
                const numberOfEntries = (message.length - 8) / 388;
                for (let i = 0; i < numberOfEntries; i++) {
                    const log = new DebugContext(view, 8 + i * 388);
                    console.log(log.id, messageImportanceToString(log.importance), log.name, log.parent, log.typeString);
                    logStore.addDebugContext(log);
                }
                break;

            case 3: {
                const numberOfEntries = (message.length - 8) / 256;
                for (let i = 0; i < numberOfEntries; i++) {
                    const log = new Log(view, 8 + i * 256);
                    console.log(log.ctxGeneration, log.ctxId, log.ctxParent, log.importance, log.timestamp, log.typeString, log.message, log.extra);
                }
                break;
            }
        }
    };
}

export type Logs = DebugContext | Log;

export const activeService = new class {
    private activeService: string = "";
    private subscriptions: (() => void)[] = [];

    public get service() {
        return this.activeService;
    }

    public setService(service: string): void {
        this.activeService = service;
        if (socket) {
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

export const logStore = new class {
    private logs: {content: Record<string, Logs[]>} = {content: {}};
    private subscriptions: (() => void)[] = [];
    private isDirty = false;

    public addLog(log: Log): void {
        this.isDirty = true;
        if (!this.logs.content[activeService.service]) {
            this.logs.content[activeService.service] = [log];
        } else {
            this.logs.content[activeService.service].push(log)
        }
        this.emitChange();
    }

    public addDebugContext(debugContext: DebugContext): void {
        this.isDirty = true;
        if (!this.logs.content[activeService.service]) {
            this.logs.content[activeService.service] = [debugContext];
        } else {
            this.logs.content[activeService.service].push(debugContext)
        }
        this.emitChange();
    }

    public subscribe(subscription: () => void) {
        this.subscriptions = [...this.subscriptions, subscription];
        return () => {
            this.subscriptions = this.subscriptions.filter(s => s !== subscription);
        };
    }

    public getSnapshot(): {content: Record<string, Logs[]>} {
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

export const serviceStore = new class {
    private services: string[] = [];
    private subscriptions: (() => void)[] = [];

    public attemptAdd(entry: string): void {
        this.services = [...this.services, entry];
        this.emitChange();
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

function activateServiceRequest(service: string): string {
    return JSON.stringify({
        type: "activate_service",
        service,
    })
}

function replayMessagesRequest(generation: string, context: number, timestamp: number): string {
    return JSON.stringify({
        type: "replay_messages",
        generation,
        context,
        timestamp,
    });
}