import { Cloud } from "Authentication/SDUCloudObject";

class WebsocketWithToken<T = string> {
    private socket: WebSocket;
    private nameSpace: string

    constructor(nameSpace: string) {
        this.socket = new WebSocket("ws://localhost:8080");
        this.nameSpace = nameSpace
    }

    public addEventListener(event: WebSocketEvent, operation: (this: WebSocket, ev: Event | MessageEvent | CloseEvent) => void) {
        this.socket.addEventListener(event, operation);
    }

    public async send(operation: T, payload: object) {
        this.socket.send(JSON.stringify({
            streamId: 43, // FIXME
            call: `${this.nameSpace}.${operation}`,
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