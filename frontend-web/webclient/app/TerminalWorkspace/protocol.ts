export const TERMINAL_PROTO_VERSION = 1;
export const TERMINAL_PROTO_FLAG_COMPRESSED = 0x80;
export const TERMINAL_PROTO_OPCODE_MASK = 0x7f;

export const TERMINAL_OP_HELLO = 0x01;
export const TERMINAL_OP_HELLO_ACK = 0x02;
export const TERMINAL_OP_PING = 0x03;
export const TERMINAL_OP_PONG = 0x04;
export const TERMINAL_OP_PANE_ATTACH = 0x10;
export const TERMINAL_OP_PANE_READY = 0x11;
export const TERMINAL_OP_PANE_DATA = 0x12;
export const TERMINAL_OP_PANE_RESIZE = 0x13;
export const TERMINAL_OP_PANE_DETACH = 0x14;
export const TERMINAL_OP_PANE_SHUTDOWN = 0x15;
export const TERMINAL_OP_PANE_STATUS = 0x16;
export const TERMINAL_OP_PANE_CREDIT = 0x17;
export const TERMINAL_OP_PANE_META = 0x18;

export const TERMINAL_ATTACH_FLAG_REPLAY = 1 << 0;

export const TERMINAL_READY_PERSISTENT = 1 << 0;
export const TERMINAL_READY_UCLOUD_PTY = 1 << 1;
export const TERMINAL_READY_VM_AGENT_PTY = 1 << 2;
export const TERMINAL_READY_FALLBACK_SHELL = 1 << 3;
export const TERMINAL_READY_RESET_REQUIRED = 1 << 4;

export const TERMINAL_META_BUSY = 1 << 0;
export const TERMINAL_META_COMMAND_DONE = 1 << 1;

export const TERMINAL_STATUS_EXITED = 1;
export const TERMINAL_STATUS_ERROR = 2;
export const TERMINAL_STATUS_JOB_ENDED = 3;
export const TERMINAL_STATUS_REPLACED = 4;

const CREDIT_REPLENISH_THRESHOLD = 64 * 1024;
const PING_INTERVAL_MS = 25000;

export interface TerminalPaneReadyEvent {
    channelId: number;
    persistent: boolean;
    ucloudPty: boolean;
    vmAgentPty: boolean;
    fallbackShell: boolean;
    resetRequired: boolean;
    baseOffset: bigint;
    replayBytes: number;
    warning: string;
}

export interface TerminalPaneMetaEvent {
    channelId: number;
    busy: boolean;
    commandDone: boolean;
    command: string;
    cwd: string;
}

export interface TerminalPaneStatusEvent {
    channelId: number;
    statusCode: number;
    exitCode: number;
    message: string;
}

export interface TerminalWsClientCallbacks {
    onReady(): void;
    onClose(reason: string): void;
    onReplaced(message: string): void;
    onPaneReady(event: TerminalPaneReadyEvent): void;
    onPaneData(channelId: number, data: Uint8Array): void;
    onPaneMeta(event: TerminalPaneMetaEvent): void;
    onPaneStatus(event: TerminalPaneStatusEvent): void;
}

export interface TerminalWsClient {
    readonly connected: boolean;
    attachPane(
        channelId: number,
        rank: number,
        cols: number,
        rows: number,
        handle: string,
        replay?: boolean,
        lastOffset?: bigint,
        sourceHandle?: string,
    ): void;
    detachPane(channelId: number): void;
    shutdownPane(channelId: number): void;
    sendData(channelId: number, data: string | Uint8Array): void;
    sendResize(channelId: number, cols: number, rows: number): void;
    close(): void;
}

const textEncoder = new TextEncoder();
const textDecoder = new TextDecoder();

async function inflateRawBytes(compressed: Uint8Array): Promise<Uint8Array> {
    const stream = new DecompressionStream("deflate-raw");
    const writer = stream.writable.getWriter();
    const copy = new Uint8Array(compressed.byteLength);
    copy.set(compressed);
    void writer.write(copy).then(() => writer.close());

    const reader = stream.readable.getReader();
    const chunks: Uint8Array[] = [];
    let total = 0;
    while (true) {
        const result = await reader.read();
        if (result.done) break;
        chunks.push(result.value);
        total += result.value.byteLength;
    }

    if (chunks.length === 1) return chunks[0];
    const merged = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
        merged.set(chunk, offset);
        offset += chunk.byteLength;
    }
    return merged;
}

function encodeHello(token: string): Uint8Array {
    const tokenBytes = textEncoder.encode(token);
    const buf = new Uint8Array(3 + 3 + tokenBytes.byteLength);
    const view = new DataView(buf.buffer);
    buf[0] = TERMINAL_OP_HELLO;
    view.setUint16(1, 0, true);
    buf[3] = TERMINAL_PROTO_VERSION;
    view.setUint16(4, tokenBytes.byteLength, true);
    buf.set(tokenBytes, 6);
    return buf;
}

function encodePaneAttach(
    channelId: number,
    rank: number,
    cols: number,
    rows: number,
    handle: string,
    replay = false,
    lastOffset = 0n,
    sourceHandle = "",
): Uint8Array {
    const handleBytes = textEncoder.encode(handle);
    const sourceBytes = textEncoder.encode(sourceHandle);
    const buf = new Uint8Array(3 + 8 + handleBytes.byteLength + 9 + sourceBytes.byteLength);
    const view = new DataView(buf.buffer);
    buf[0] = TERMINAL_OP_PANE_ATTACH;
    view.setUint16(1, channelId, true);
    view.setUint16(3, rank, true);
    view.setUint16(5, Math.max(1, cols), true);
    view.setUint16(7, Math.max(1, rows), true);
    buf[9] = replay ? TERMINAL_ATTACH_FLAG_REPLAY : 0;
    buf[10] = handleBytes.byteLength;
    buf.set(handleBytes, 11);
    const extPos = 11 + handleBytes.byteLength;
    view.setBigUint64(extPos, lastOffset, true);
    buf[extPos + 8] = sourceBytes.byteLength;
    if (sourceBytes.byteLength > 0) {
        buf.set(sourceBytes, extPos + 9);
    }
    return buf;
}

function encodePaneResize(channelId: number, cols: number, rows: number): Uint8Array {
    const buf = new Uint8Array(3 + 4);
    const view = new DataView(buf.buffer);
    buf[0] = TERMINAL_OP_PANE_RESIZE;
    view.setUint16(1, channelId, true);
    view.setUint16(3, Math.max(1, cols), true);
    view.setUint16(5, Math.max(1, rows), true);
    return buf;
}

function encodePaneCredit(channelId: number, bytes: number): Uint8Array {
    const buf = new Uint8Array(3 + 4);
    const view = new DataView(buf.buffer);
    buf[0] = TERMINAL_OP_PANE_CREDIT;
    view.setUint16(1, channelId, true);
    view.setUint32(3, bytes, true);
    return buf;
}

function encodeEmptyControl(opcode: number, channelId: number): Uint8Array {
    const buf = new Uint8Array(3);
    const view = new DataView(buf.buffer);
    buf[0] = opcode;
    view.setUint16(1, channelId, true);
    return buf;
}

function encodePing(): Uint8Array {
    const buf = new Uint8Array(3 + 8);
    const view = new DataView(buf.buffer);
    buf[0] = TERMINAL_OP_PING;
    view.setUint16(1, 0, true);
    view.setBigUint64(3, BigInt(Date.now()), true);
    return buf;
}

export function createTerminalWsClient(
    url: string,
    token: string,
    callbacks: TerminalWsClientCallbacks,
): TerminalWsClient {
    const ws = new WebSocket(url);
    ws.binaryType = "arraybuffer";

    let handshakeComplete = false;
    let closedByClient = false;
    let replaced = false;
    let pingTimer: number | null = null;

    const channelQueues = new Map<number, Promise<void>>();
    const pendingCredit = new Map<number, number>();

    const sendRaw = (frame: Uint8Array) => {
        if (ws.readyState === WebSocket.OPEN) {
            ws.send(frame as Uint8Array<ArrayBuffer>);
        }
    };

    const recordConsumedBytes = (channelId: number, byteLength: number) => {
        const next = (pendingCredit.get(channelId) ?? 0) + byteLength;
        if (next >= CREDIT_REPLENISH_THRESHOLD) {
            pendingCredit.set(channelId, 0);
            sendRaw(encodePaneCredit(channelId, next));
        } else {
            pendingCredit.set(channelId, next);
        }
    };

    ws.onopen = () => {
        sendRaw(encodeHello(token));
    };

    ws.onmessage = event => {
        if (!(event.data instanceof ArrayBuffer)) return;
        const raw = new Uint8Array(event.data);
        if (raw.byteLength < 3) return;

        const view = new DataView(event.data);
        const firstByte = raw[0];
        const opcode = firstByte & TERMINAL_PROTO_OPCODE_MASK;
        const compressed = (firstByte & TERMINAL_PROTO_FLAG_COMPRESSED) !== 0;
        const channelId = view.getUint16(1, true);

        if (opcode === TERMINAL_OP_HELLO_ACK) {
            handshakeComplete = true;
            if (pingTimer === null) {
                pingTimer = window.setInterval(() => {
                    sendRaw(encodePing());
                }, PING_INTERVAL_MS);
            }
            callbacks.onReady();
            return;
        }

        if (opcode === TERMINAL_OP_PONG) {
            return;
        }

        if (opcode === TERMINAL_OP_PANE_DATA) {
            const payload = new Uint8Array(event.data, 3);
            const activeQueue = channelQueues.get(channelId);
            if (!compressed && !activeQueue) {
                callbacks.onPaneData(channelId, payload);
                recordConsumedBytes(channelId, payload.byteLength);
                return;
            }

            const prev = activeQueue ?? Promise.resolve();
            const next = prev.then(async () => {
                if (closedByClient) return;
                const decoded = compressed ? await inflateRawBytes(payload) : payload;
                if (closedByClient) return;
                callbacks.onPaneData(channelId, decoded);
                recordConsumedBytes(channelId, decoded.byteLength);
            }).catch(() => undefined);

            channelQueues.set(channelId, next);
            void next.finally(() => {
                if (channelQueues.get(channelId) === next) {
                    channelQueues.delete(channelId);
                }
            });
            return;
        }

        if (opcode === TERMINAL_OP_PANE_READY) {
            if (raw.byteLength < 18) return;
            const flags = raw[3];
            const baseOffset = view.getBigUint64(4, true);
            const replayBytes = view.getUint32(12, true);
            const warnLen = view.getUint16(16, true);
            const warning = warnLen > 0 && raw.byteLength >= 18 + warnLen
                ? textDecoder.decode(new Uint8Array(event.data, 18, warnLen))
                : "";
            callbacks.onPaneReady({
                channelId,
                persistent: (flags & TERMINAL_READY_PERSISTENT) !== 0,
                ucloudPty: (flags & TERMINAL_READY_UCLOUD_PTY) !== 0,
                vmAgentPty: (flags & TERMINAL_READY_VM_AGENT_PTY) !== 0,
                fallbackShell: (flags & TERMINAL_READY_FALLBACK_SHELL) !== 0,
                resetRequired: (flags & TERMINAL_READY_RESET_REQUIRED) !== 0,
                baseOffset,
                replayBytes,
                warning,
            });
            return;
        }

        if (opcode === TERMINAL_OP_PANE_META) {
            if (raw.byteLength < 8) return;
            const flags = raw[3];
            const cmdLen = view.getUint16(4, true);
            if (raw.byteLength < 8 + cmdLen) return;
            const command = cmdLen > 0
                ? textDecoder.decode(new Uint8Array(event.data, 6, cmdLen))
                : "";
            const cwdPos = 6 + cmdLen;
            const cwdLen = view.getUint16(cwdPos, true);
            if (raw.byteLength < cwdPos + 2 + cwdLen) return;
            const cwd = cwdLen > 0
                ? textDecoder.decode(new Uint8Array(event.data, cwdPos + 2, cwdLen))
                : "";
            callbacks.onPaneMeta({
                channelId,
                busy: (flags & TERMINAL_META_BUSY) !== 0,
                commandDone: (flags & TERMINAL_META_COMMAND_DONE) !== 0,
                command,
                cwd,
            });
            return;
        }

        if (opcode === TERMINAL_OP_PANE_STATUS) {
            if (raw.byteLength < 10) return;
            const statusCode = raw[3];
            const exitCode = view.getInt32(4, true);
            const msgLen = view.getUint16(8, true);
            const message = msgLen > 0 && raw.byteLength >= 10 + msgLen
                ? textDecoder.decode(new Uint8Array(event.data, 10, msgLen))
                : "";
            if (channelId === 0 && statusCode === TERMINAL_STATUS_REPLACED) {
                replaced = true;
                callbacks.onReplaced(message || "Opened in another tab");
                return;
            }
            callbacks.onPaneStatus({
                channelId,
                statusCode,
                exitCode,
                message,
            });
        }
    };

    ws.onclose = () => {
        handshakeComplete = false;
        if (pingTimer !== null) {
            window.clearInterval(pingTimer);
            pingTimer = null;
        }
        if (!closedByClient && !replaced) {
            callbacks.onClose("Connection closed");
        }
    };

    ws.onerror = () => {
        if (!closedByClient && !replaced) {
            callbacks.onClose("Connection error");
        }
    };

    return {
        get connected(): boolean {
            return handshakeComplete && ws.readyState === WebSocket.OPEN;
        },
        attachPane(
            channelId: number,
            rank: number,
            cols: number,
            rows: number,
            handle: string,
            replay = false,
            lastOffset = 0n,
            sourceHandle = "",
        ): void {
            pendingCredit.set(channelId, 0);
            sendRaw(encodePaneAttach(channelId, rank, cols, rows, handle, replay, lastOffset, sourceHandle));
        },
        detachPane(channelId: number): void {
            pendingCredit.delete(channelId);
            channelQueues.delete(channelId);
            sendRaw(encodeEmptyControl(TERMINAL_OP_PANE_DETACH, channelId));
        },
        shutdownPane(channelId: number): void {
            pendingCredit.delete(channelId);
            channelQueues.delete(channelId);
            sendRaw(encodeEmptyControl(TERMINAL_OP_PANE_SHUTDOWN, channelId));
        },
        sendData(channelId: number, data: string | Uint8Array): void {
            const bytes = typeof data === "string" ? textEncoder.encode(data) : data;
            const buf = new Uint8Array(3 + bytes.byteLength);
            const view = new DataView(buf.buffer);
            buf[0] = TERMINAL_OP_PANE_DATA;
            view.setUint16(1, channelId, true);
            buf.set(bytes, 3);
            sendRaw(buf);
        },
        sendResize(channelId: number, cols: number, rows: number): void {
            sendRaw(encodePaneResize(channelId, cols, rows));
        },
        close(): void {
            closedByClient = true;
            if (pingTimer !== null) {
                window.clearInterval(pingTimer);
                pingTimer = null;
            }
            if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
                ws.close();
            }
        },
    };
}
