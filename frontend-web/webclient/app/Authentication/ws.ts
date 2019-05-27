import Cloud from "./lib";

export interface WebSocketOpenSettings {
    /**
     * Will be called whenever a new socket is initialized
     */
    init?: (conn: WebSocketConnection) => void

    reconnect?: boolean

    onClose?: (conn: WebSocketConnection) => void
}

export class WebSocketFactory {
    private readonly cloud: Cloud;
    constructor(cloud: Cloud) {
        this.cloud = cloud;
    }

    open(path: string, settings?: WebSocketOpenSettings): WebSocketConnection {
        const settingsOrDefault = settings || {};
        return new WebSocketConnection(this.cloud, () => {
            const url = this.cloud.computeURL("/api", path)
                .replace("http://", "ws://")
                .replace("https://", "wss://");
            return new WebSocket(url);
        }, settingsOrDefault);
    }
}

export interface WebsocketResponse {
    type: "message" | "response"
    streamId: string
    payload?: any
    status?: number
}

interface WebsocketRequest {
    call: string
    streamId: string
    bearer: string
    payload: any | null
}

export class WebSocketConnection {
    private cloud: Cloud;
    private socket: WebSocket;
    private nextStreamId: number = 0;
    private handlers: Map<string, (message: WebsocketResponse) => void> = new Map();
    private _closed: boolean = false;
    private settings: WebSocketOpenSettings;

    constructor(cloud: Cloud, socketFactory: () => WebSocket, settings: WebSocketOpenSettings) {
        this.cloud = cloud;
        this.settings = settings;
        this.resetSocket(socketFactory);
    }

    get closed() {
        return this._closed;
    }

    close() {
        this._closed = true;
        this.socket.close();
        const closeScript = this.settings.onClose || (() => { });
        closeScript(this);
    }

    private resetSocket(socketFactory: () => WebSocket) {
        const socket = socketFactory();
        const initScript = this.settings.init || (() => { });

        socket.addEventListener("open", () => {
            console.log("Connecting to WS is open");
            initScript(this);
        });

        socket.addEventListener("close", () => {
            if (this.settings.reconnect !== false && !this.closed) {
                // We will reconnect by default.
                console.log("Lost connection to WS. Reconnecting...");
                this.handlers.forEach(e => {
                    e({ type: "response", status: 503, streamId: "unknown" })
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

    private sendMessage(message: WebsocketRequest) {
        this.socket.send(JSON.stringify(message));
    }

    async subscribe(call: string, payload: any | null, handler: (message: WebsocketResponse) => void) {
        const streamId = (this.nextStreamId++).toString();
        this.handlers.set(streamId, (message) => {
            handler(message);
            if (message.type === "response") {
                this.handlers.delete(streamId);
            }
        });

        this.sendMessage({
            call,
            streamId,
            payload,
            bearer: await this.cloud.receiveAccessTokenOrRefreshIt()
        });
    }

    async call(call: string, payload: any | null): Promise<WebsocketResponse> {
        return new Promise(async (resolve, reject) => {
            this.subscribe(call, payload, async (message) => {
                if (message.type === "response") {
                    const success = message.status !== undefined && message.status <= 399;

                    if (success) resolve(message);
                    else reject(message);
                }
            });
        });
    }
}