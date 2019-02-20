import { Cloud } from "Authentication/SDUCloudObject";

class WebsocketWithToken<T = string> {
    private socket: WebSocket;
    private namespace: string;
    private streamIdOperationPair: Map<string, () => void> = new Map(); 

    constructor(namespace: string) {
        this.socket = new WebSocket(`ws://localhost:8080/${namespace}`);
        this.namespace = namespace
    }

    public addEventListener(event: WebSocketEvent, operation: (this: WebSocket, ev: Event | MessageEvent | CloseEvent) => void) {
        this.socket.addEventListener(event, operation);
    }

    public async send(operation: T, payload: object) {
        this.socket.send(JSON.stringify({
            streamId: "43", // FIXME
            call: `${this.namespace}.${operation}`,
            bearer: await Cloud.receiveAccessTokenOrRefreshIt,
            payload
        }));
    }

    public close() {
        this.socket.close();
    }
}

type WebSocketEvent = "open" | "message" | "error" | "close";

interface WebSocketMessage<T = any> {
    type: "message"
    streamId: string
    payload: T
}

interface WebSocketResponse<T = any> {
    type: "response"
    streamId: string
    payload: T
    status: number
}

interface WebSocketMessageCall {
    streamId: string
    call: string
    
}