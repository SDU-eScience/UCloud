import { Log, DebugContext } from "./Schema";

let isConnected = false;
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
        isConnected = true;
        options.onConnect();
    };

    socket.onclose = () => {
        isConnected = false;
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
                console.log("Service!", message);
                break;

            case 2:
                const numberOfEntries = (message.length - 8) / 388;
                for (let i = 0; i < numberOfEntries; i++) {
                    const log = new DebugContext(view, 8 + i * 388);
                    console.log(log.importance, log.name, log.id, log.parent);
                }
                break;

            case 3: {
                const numberOfEntries = (message.length - 8) / 256;
                for (let i = 0; i < numberOfEntries; i++) {
                    const log = new Log(view, 8 + i * 256);
                    console.log(log.ctxGeneration, log.ctxId, log.ctxParent, log.importance, log.timestamp, log.type, log.message, log.extra);
                }
                break;
            }
        }
    };
}
