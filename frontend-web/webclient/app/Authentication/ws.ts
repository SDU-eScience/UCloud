import HttpClient from "./lib";

export interface WebSocketOpenSettings {
    /**
     * Will be called whenever a new socket is initialized
     */
    init?: (conn: WebSocketConnection) => void;
    reconnect?: boolean;
    onClose?: (conn: WebSocketConnection) => void;
}

export class WebSocketFactory {
    private readonly client: HttpClient;

    constructor(client: HttpClient) {
        this.client = client;
    }

    public open(path: string, settings?: WebSocketOpenSettings): WebSocketConnection {
        const settingsOrDefault = settings ?? {};
        return new WebSocketConnection(this.client, async () => {
            await this.client.waitForCloudReady();

            const url = this.client.computeURL("/api", path)
                .replace("http://", "ws://")
                .replace("https://", "wss://");

            return new WebSocket(url);
        }, settingsOrDefault);
    }
}

export interface WebsocketResponse<T = any> {
    type: "message" | "response";
    streamId: string;
    payload?: T;
    status?: number;
}

interface WebsocketRequest<T = any> {
    call: string;
    streamId: string;
    bearer: string;
    payload: T | null;
}

interface SubscribeParameters<T = any> {
    call: string;
    payload: T | null;
    handler: (message: WebsocketResponse) => void;
}

interface CallParameters<T = any> {
    call: string;
    payload: T | null;
}

export class WebSocketConnection {
    private client: HttpClient;
    private socket: WebSocket;
    private nextStreamId: number = 0;
    private handlers: Map<string, (message: WebsocketResponse) => void> = new Map();
    private internalClosed: boolean = false;
    private settings: WebSocketOpenSettings;

    constructor(client: HttpClient, socketFactory: () => Promise<WebSocket>, settings: WebSocketOpenSettings) {
        this.client = client;
        this.settings = settings;
        this.resetSocket(socketFactory);
    }

    get closed(): boolean {
        return this.internalClosed;
    }

    public close(): void {
        this.internalClosed = true;
        this.socket.close();
        const closeScript = this.settings.onClose ?? (() => {
            // Empty
        });
        closeScript(this);
    }

    public async subscribe<T>({call, payload, handler}: SubscribeParameters<T>): Promise<void> {
        const streamId = (this.nextStreamId++).toString();
        this.handlers.set(streamId, message => {
            handler(message);
            if (message.type === "response") {
                this.handlers.delete(streamId);
            }
        });

        this.sendMessage({
            call,
            streamId,
            payload,
            bearer: await this.client.receiveAccessTokenOrRefreshIt()
        });
    }

    public async call<T>({call, payload}: CallParameters<T>): Promise<WebsocketResponse> {
        return new Promise(async (resolve, reject) => {
            this.subscribe({
                call,
                payload,
                handler: async (message) => {
                    if (message.type === "response") {
                        const success = message.status !== undefined && message.status <= 399;

                        if (success) resolve(message);
                        else reject(message);
                    }
                }
            });
        });
    }

    private async resetSocket(socketFactory: () => Promise<WebSocket>): Promise<void> {
        const socket = await socketFactory();
        const initScript = this.settings.init ?? (() => {
            // Do nothing
        });

        socket.addEventListener("open", () => {
            initScript(this);
        });

        socket.addEventListener("close", () => {
            if (this.settings.reconnect !== false && !this.closed) {
                // We will reconnect by default.
                this.handlers.forEach(e => {
                    e({type: "response", status: 503, streamId: "unknown"});
                });
                this.handlers.clear();
                this.resetSocket(socketFactory);
            } else {
                this.close();
            }
        });

        socket.addEventListener("message", (ev) => {
            const message: WebsocketResponse = JSON.parse(ev.data);

            if (!!message.type && !!message.streamId) {
                const handler = this.handlers.get(message.streamId);
                if (!!handler) {
                    handler(message);
                }
            }
        });

        this.socket = socket;
    }

    private sendMessage(message: WebsocketRequest): void {
        this.socket.send(JSON.stringify(message));
    }
}
