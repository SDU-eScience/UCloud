import {encodeFrame, Frame, Opcode, RpcStatus, Value, ValueKind} from "@/Playground/UcxCreateDemo/protocol";

export type RpcPayload = Record<string, Value>;
export type RpcHandler = (payload: RpcPayload) => Promise<RpcPayload | void> | RpcPayload | void;

interface PendingRpc {
    resolve: (value: RpcPayload) => void;
    reject: (reason: unknown) => void;
    timeoutHandle: number | null;
}

export class RpcCallError extends Error {
    status: number;
    payload: RpcPayload;

    constructor(status: number, payload: RpcPayload) {
        super(rpcErrorMessage(status, payload));
        this.status = status;
        this.payload = payload;
    }
}

export class UcxSession {
    private nextSeq = 1;
    private readonly handlers = new Map<string, RpcHandler>();
    private readonly pending = new Map<number, PendingRpc>();

    constructor(private readonly sendBytes: (bytes: Uint8Array) => void) {
    }

    setNextSeq(seq: number) {
        this.nextSeq = Math.max(this.nextSeq, seq);
    }

    send(frame: Omit<Frame, "seq">): number {
        const seq = this.nextSeq++;
        this.sendBytes(encodeFrame({...frame, seq}));
        return seq;
    }

    private sendWithSeq(seq: number, frame: Omit<Frame, "seq">) {
        this.sendBytes(encodeFrame({...frame, seq}));
    }

    registerRpcHandler(name: string, handler: RpcHandler) {
        this.handlers.set(name, handler);
    }

    unregisterRpcHandler(name: string) {
        this.handlers.delete(name);
    }

    invokeRpc(name: string, payload: RpcPayload = {}, timeoutMs = 30000): Promise<RpcPayload> {
        return new Promise<RpcPayload>((resolve, reject) => {
            const seq = this.nextSeq++;

            const pending: PendingRpc = {
                resolve,
                reject,
                timeoutHandle: null,
            };

            if (timeoutMs > 0) {
                pending.timeoutHandle = window.setTimeout(() => {
                    if (!this.pending.has(seq)) {
                        return;
                    }
                    this.pending.delete(seq);
                    reject(new Error(`UCX RPC timeout for '${name}'`));
                }, timeoutMs);
            }

            this.pending.set(seq, pending);

            try {
                this.sendWithSeq(seq, {
                    replyToSeq: 0,
                    opcode: Opcode.RpcRequest,
                    rpcRequestName: name,
                    rpcPayload: payload,
                });
            } catch (err) {
                this.pending.delete(seq);
                if (pending.timeoutHandle != null) {
                    window.clearTimeout(pending.timeoutHandle);
                }
                reject(err);
            }
        });
    }

    handleIncoming(frame: Frame): boolean {
        if (frame.opcode === Opcode.RpcRequest) {
            const requestName = frame.rpcRequestName ?? "";
            const handler = this.handlers.get(requestName);

            if (!handler) {
                this.send({
                    replyToSeq: frame.seq,
                    opcode: Opcode.RpcResponse,
                    rpcStatus: RpcStatus.NotFound,
                    rpcPayload: {error: {kind: ValueKind.String, string: `rpc handler not found: ${requestName}`}},
                });
                return true;
            }

            queueMicrotask(() => {
                Promise.resolve(handler(frame.rpcPayload ?? {}))
                    .then(result => {
                        this.send({
                            replyToSeq: frame.seq,
                            opcode: Opcode.RpcResponse,
                            rpcStatus: RpcStatus.Ok,
                            rpcPayload: result ?? {},
                        });
                    })
                    .catch(err => {
                        this.send({
                            replyToSeq: frame.seq,
                            opcode: Opcode.RpcResponse,
                            rpcStatus: RpcStatus.Internal,
                            rpcPayload: {error: {kind: ValueKind.String, string: String(err)}},
                        });
                    });
            });

            return true;
        }

        if (frame.opcode === Opcode.RpcResponse) {
            const pending = this.pending.get(frame.replyToSeq);
            if (!pending) {
                return true;
            }

            this.pending.delete(frame.replyToSeq);
            if (pending.timeoutHandle != null) {
                window.clearTimeout(pending.timeoutHandle);
            }

            if ((frame.rpcStatus ?? RpcStatus.Ok) !== RpcStatus.Ok) {
                pending.reject(new RpcCallError(frame.rpcStatus ?? RpcStatus.Internal, frame.rpcPayload ?? {}));
            } else {
                pending.resolve(frame.rpcPayload ?? {});
            }

            return true;
        }

        return false;
    }

    close(reason: string) {
        for (const [seq, pending] of this.pending.entries()) {
            this.pending.delete(seq);
            if (pending.timeoutHandle != null) {
                window.clearTimeout(pending.timeoutHandle);
            }
            pending.reject(new Error(reason));
        }
    }
}

function rpcErrorMessage(status: number, payload: RpcPayload): string {
    const errorValue = payload["error"];
    if (errorValue?.kind === ValueKind.String) {
        return `UCX RPC failed (${status}): ${errorValue.string}`;
    }
    return `UCX RPC failed (${status})`;
}
