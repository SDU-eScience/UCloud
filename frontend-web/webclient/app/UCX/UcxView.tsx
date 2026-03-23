import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {Button, Checkbox, Flex, Icon, Input, Text} from "@/ui-components";
import {decodeFrame, Frame, Opcode, plainMapToValue, PlainValue, UiNode, Value, valueMapToPlain, ValueKind} from "@/UCX/protocol";
import {UcxSession} from "@/UCX/session";

type ValueProvider = string | (() => string | Promise<string>);
export type UcxRpcPayload = Record<string, PlainValue>;
export type UcxRpcHandler = (payload: UcxRpcPayload) => Promise<UcxRpcPayload | void> | UcxRpcPayload | void;

export interface UcxFunctionRegistry {
    sendBoundInput: (node: UiNode, value: Value, model: Record<string, Value>, scope?: Record<string, Value>) => void;
    sendUiEvent: (nodeId: string, event?: string, value?: Value) => void;
    invokeRpc: (name: string, payload?: UcxRpcPayload, timeoutMs?: number) => Promise<UcxRpcPayload>;
    modelValue: (model: Record<string, Value>, path: string, scope?: Record<string, Value>) => Value | undefined;
    sxStyle: (node: UiNode) => React.CSSProperties;
    [key: string]: unknown;
}

export interface UcxRenderContext {
    node: UiNode;
    model: Record<string, Value>;
    scope?: Record<string, Value>;
    fn: UcxFunctionRegistry;
    renderChildren: (scopeOverride?: Record<string, Value>) => React.ReactNode;
}

export type UcxComponentRenderer = (ctx: UcxRenderContext) => React.ReactNode;
export type UcxComponentRegistry = Record<string, UcxComponentRenderer>;

export interface UcxFrameRenderArgs {
    connected: boolean;
    transportError: string;
    reconnectingInSeconds?: number;
    content: React.ReactNode;
}

export interface UcxViewProps {
    url: string;
    authToken: ValueProvider;
    sysHello: ValueProvider;
    renderFrame?: (args: UcxFrameRenderArgs) => React.ReactNode;
    components?: Partial<UcxComponentRegistry>;
    functions?: Partial<UcxFunctionRegistry>;
    rpcHandlers?: Record<string, UcxRpcHandler>;
    onConnected?: () => void;
    onDisconnected?: (reason: string) => void;
    onTransportError?: (message: string) => void;
}

const UcxView: React.FunctionComponent<UcxViewProps> = ({
    url,
    authToken,
    sysHello,
    renderFrame,
    components,
    functions,
    rpcHandlers,
    onConnected,
    onDisconnected,
    onTransportError,
}) => {
    const [connected, setConnected] = useState(false);
    const [root, setRoot] = useState<UiNode | null>(null);
    const [model, setModel] = useState<Record<string, Value>>({});
    const [transportError, setTransportError] = useState("");
    const [reconnectingInSeconds, setReconnectingInSeconds] = useState<number | undefined>(undefined);

    const connRef = useRef<WebSocket | null>(null);
    const sessionRef = useRef<UcxSession | null>(null);
    const eventIdRef = useRef(1);
    const reconnectAttemptRef = useRef(0);
    const reconnectTimerRef = useRef<number | null>(null);
    const everConnectedRef = useRef(false);
    const pendingRehydrateModelRef = useRef<Record<string, Value> | null>(null);
    const authCompleteRef = useRef(false);
    const modelRef = useRef<Record<string, Value>>({});
    const authTokenRef = useRef<ValueProvider>(authToken);
    const sysHelloRef = useRef<ValueProvider>(sysHello);
    const rpcHandlersRef = useRef<Record<string, UcxRpcHandler> | undefined>(rpcHandlers);
    const onConnectedRef = useRef<typeof onConnected>(onConnected);
    const onDisconnectedRef = useRef<typeof onDisconnected>(onDisconnected);
    const onTransportErrorRef = useRef<typeof onTransportError>(onTransportError);

    useEffect(() => {
        modelRef.current = model;
    }, [model]);

    useEffect(() => {
        authTokenRef.current = authToken;
    }, [authToken]);

    useEffect(() => {
        sysHelloRef.current = sysHello;
    }, [sysHello]);

    useEffect(() => {
        rpcHandlersRef.current = rpcHandlers;
    }, [rpcHandlers]);

    useEffect(() => {
        onConnectedRef.current = onConnected;
    }, [onConnected]);

    useEffect(() => {
        onDisconnectedRef.current = onDisconnected;
    }, [onDisconnected]);

    useEffect(() => {
        onTransportErrorRef.current = onTransportError;
    }, [onTransportError]);

    const sendFrame = useCallback((frame: Omit<Frame, "seq">) => {
        sessionRef.current?.send(frame);
    }, []);

    const sendUiEvent = useCallback((nodeId: string, event = "click", value?: Value) => {
        sendFrame({
            replyToSeq: 0,
            opcode: Opcode.UiEvent,
            uiEvent: {
                nodeId,
                event,
                value: value ?? {kind: ValueKind.Null},
            },
        });
    }, [sendFrame]);

    const sendBoundInput = useCallback((node: UiNode, value: Value) => {
        if (!node.bindPath) return;

        if (node.optimistic) {
            setModel(prev => ({...prev, [node.bindPath]: value}));
        }

        const eventId = eventIdRef.current++;
        sendFrame({
            replyToSeq: 0,
            opcode: Opcode.ModelInput,
            modelInput: {
                eventId,
                nodeId: node.id,
                path: node.bindPath,
                value,
            },
        });
    }, [sendFrame]);

    const invokeRpc = useCallback((name: string, payload: UcxRpcPayload = {}, timeoutMs = 30000) => {
        if (!sessionRef.current) {
            return Promise.reject(new Error("UCX session is not connected"));
        }
        return sessionRef.current.invokeRpc(name, plainMapToValue(payload), timeoutMs).then(valueMapToPlain);
    }, []);

    const baseFunctions = useMemo<UcxFunctionRegistry>(() => ({
        sendBoundInput: (node, value) => sendBoundInput(node, value),
        sendUiEvent,
        invokeRpc,
        modelValue,
        sxStyle,
    }), [invokeRpc, sendBoundInput, sendUiEvent]);

    const mergedFunctions = useMemo<UcxFunctionRegistry>(() => ({
        ...baseFunctions,
        ...(functions ?? {}),
    }), [baseFunctions, functions]);

    const mergedComponents = useMemo<UcxComponentRegistry>(() => mergeComponentRegistry(baseComponents, components), [components]);

    const resendModelAfterReconnect = useCallback((snapshot: Record<string, Value>, mount: {root: UiNode}) => {
        const bindPaths = collectInputBindPaths(mount.root);
        if (bindPaths.size === 0) {
            return;
        }

        for (const path of bindPaths) {
            const value = modelValue(snapshot, path);
            if (!value) {
                continue;
            }

            const eventId = eventIdRef.current++;
            sendFrame({
                replyToSeq: 0,
                opcode: Opcode.ModelInput,
                modelInput: {
                    eventId,
                    nodeId: `rehydrate:${path}`,
                    path,
                    value,
                },
            });
        }
    }, [sendFrame]);

    useEffect(() => {
        let disposed = false;

        const clearReconnectTimer = () => {
            if (reconnectTimerRef.current != null) {
                window.clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
            }
        };

        const setError = (message: string) => {
            setTransportError(message);
            onTransportErrorRef.current?.(message);
        };

        const scheduleReconnect = () => {
            if (disposed) {
                return;
            }

            clearReconnectTimer();

            const baseDelayMs = 500;
            const capDelayMs = 30000;
            const attempt = reconnectAttemptRef.current;
            const maxDelay = Math.min(capDelayMs, baseDelayMs * Math.pow(2, attempt));
            const delay = Math.floor(Math.random() * maxDelay);
            reconnectAttemptRef.current = attempt + 1;

            const reconnectIn = Math.max(1, Math.ceil(delay / 1000));
            setReconnectingInSeconds(reconnectIn);
            setError(`Disconnected. Reconnecting in ${reconnectIn}s...`);
            reconnectTimerRef.current = window.setTimeout(connect, delay);
        };

        const connect = () => {
            if (disposed) {
                return;
            }

            const active = connRef.current;
            if (active && (active.readyState === WebSocket.OPEN || active.readyState === WebSocket.CONNECTING)) {
                return;
            }

            const socket = new WebSocket(url);
            socket.binaryType = "arraybuffer";
            connRef.current = socket;
            sessionRef.current = new UcxSession(bytes => {
                if (socket.readyState !== WebSocket.OPEN) {
                    return;
                }
                socket.send(bytes);
            });

            socket.onopen = () => {
                reconnectAttemptRef.current = 0;
                clearReconnectTimer();
                authCompleteRef.current = false;

                const handlers = rpcHandlersRef.current;
                if (handlers) {
                    for (const [name, handler] of Object.entries(handlers)) {
                        sessionRef.current?.registerRpcHandler(name, payload => {
                            return Promise.resolve(handler(valueMapToPlain(payload))).then(result => {
                                return plainMapToValue(result ?? {});
                            });
                        });
                    }
                }

                void (async () => {
                    try {
                        const token = await resolveProvider(authTokenRef.current);
                        const hello = await resolveProvider(sysHelloRef.current);

                        socket.send(token);
                        sendFrame({
                            replyToSeq: 0,
                            opcode: Opcode.SysHello,
                            sysHello: hello,
                        });
                    } catch (err) {
                        setError(err instanceof Error ? err.message : "Failed to initialize UCX handshake");
                        socket.close();
                    }
                })();
            };

            socket.onmessage = event => {
                try {
                    if (typeof event.data === "string") {
                        if (authCompleteRef.current) {
                            return;
                        }

                        if (event.data !== "OK") {
                            setError("Authentication failed");
                            socket.close();
                            return;
                        }

                        authCompleteRef.current = true;

                        setConnected(true);
                        setReconnectingInSeconds(undefined);
                        setTransportError("");
                        onConnectedRef.current?.();

                        if (everConnectedRef.current && Object.keys(modelRef.current).length > 0) {
                            pendingRehydrateModelRef.current = {...modelRef.current};
                        }
                        everConnectedRef.current = true;
                        return;
                    }

                    if (!(event.data instanceof ArrayBuffer)) {
                        setError("Received non-binary websocket frame");
                        return;
                    }

                    if (!authCompleteRef.current) {
                        setError("Received binary frame before authentication");
                        socket.close();
                        return;
                    }

                    const bytes = new Uint8Array(event.data);
                    const frame = decodeFrame(bytes);
                    sessionRef.current?.setNextSeq(frame.seq + 1);

                    if (sessionRef.current?.handleIncoming(frame)) {
                        return;
                    }

                    if (frame.opcode === Opcode.UiMount && frame.uiMount) {
                        const mount = frame.uiMount;
                        const rehydrateSnapshot = pendingRehydrateModelRef.current;

                        setRoot(mount.root);
                        setModel(mount.model);

                        if (rehydrateSnapshot) {
                            pendingRehydrateModelRef.current = null;
                            resendModelAfterReconnect(rehydrateSnapshot, {
                                root: mount.root,
                            });
                        }
                    } else if (frame.opcode === Opcode.ModelPatch && frame.modelPatch) {
                        setModel(prev => {
                            const next = {...prev};
                            for (const [path, value] of Object.entries(frame.modelPatch!.changes)) {
                                if (value.kind === ValueKind.Null) {
                                    delete next[path];
                                } else {
                                    next[path] = value;
                                }
                            }
                            return next;
                        });
                    }
                } catch (err) {
                    setError(err instanceof Error ? err.message : "Failed to decode frame");
                }
            };

            socket.onclose = () => {
                if (connRef.current === socket) {
                    connRef.current = null;
                }
                sessionRef.current?.close("WebSocket closed");
                sessionRef.current = null;
                setConnected(false);
                onDisconnectedRef.current?.("WebSocket closed");
                if (!disposed) {
                    scheduleReconnect();
                }
            };

            socket.onerror = () => {
                setError("WebSocket error");
                socket.close();
            };
        };

        connect();

        return () => {
            disposed = true;
            clearReconnectTimer();
            sessionRef.current?.close("UCX session disposed");
            sessionRef.current = null;
            const conn = connRef.current;
            connRef.current = null;
            if (conn) {
                conn.close();
            }
        };
    }, [resendModelAfterReconnect, sendFrame, url]);

    const content = root == null ? <Text>Waiting for UI mount...</Text> :
        <NodeRenderer
            node={root}
            model={model}
            fn={mergedFunctions}
            components={mergedComponents}
        />;

    if (renderFrame) {
        return <>{renderFrame({
            connected,
            transportError,
            reconnectingInSeconds,
            content,
        })}</>;
    }

    return <>{content}</>;
};

const NodeRenderer: React.FunctionComponent<{
    node: UiNode;
    model: Record<string, Value>;
    scope?: Record<string, Value>;
    fn: UcxFunctionRegistry;
    components: UcxComponentRegistry;
}> = ({node, model, scope, fn, components}) => {
    const renderer = components[node.component];
    if (!renderer) {
        return null;
    }

    return <>{renderer({
        node,
        model,
        scope,
        fn,
        renderChildren: (scopeOverride?: Record<string, Value>) => node.children.map(child =>
            <NodeRenderer
                key={child.id}
                node={child}
                model={model}
                scope={scopeOverride ?? scope}
                fn={fn}
                components={components}
            />
        ),
    })}</>;
};

const baseComponents: UcxComponentRegistry = {
    flex: ({node, fn, renderChildren}) => {
        const direction = stringProp(node, "direction", "column");
        const gap = numberProp(node, "gap", 8);
        const stackStyle: React.CSSProperties = {
            display: "flex",
            flexDirection: direction as React.CSSProperties["flexDirection"],
            gap: `${gap}px`,
            width: "100%",
            ...fn.sxStyle(node),
        };
        return <div style={stackStyle}>{renderChildren()}</div>;
    },
    box: ({node, fn, renderChildren}) => <div style={fn.sxStyle(node)}>{renderChildren()}</div>,
    heading: ({node, model, scope, fn}) => {
        const text = boundOrStaticText(node, model, scope);
        if (!text) return null;

        const level = Math.max(1, Math.min(6, Math.trunc(numberProp(node, "level", 3))));
        const tag = `h${level}`;
        const color = stringProp(node, "color", "");
        const headingStyle: React.CSSProperties = {
            margin: 0,
            ...fn.sxStyle(node),
        };
        if (color) {
            headingStyle.color = toCssColor(color);
        }

        return React.createElement(tag, {style: headingStyle}, text);
    },
    text: ({node, model, scope, fn}) => {
        const text = boundOrStaticText(node, model, scope);
        if (!text) return null;
        const color = stringProp(node, "color", "");
        return <Text color={(color || undefined) as any} style={fn.sxStyle(node)}>{text}</Text>;
    },
    icon: ({node, fn}) => {
        const name = stringProp(node, "name", "bug");
        const color = stringProp(node, "color", "iconColor");
        const size = numberProp(node, "size", 18);
        return <Icon name={name as any} color={color as any} size={size} style={fn.sxStyle(node)} />;
    },
    input_text: ({node, model, scope, fn}) => {
        const label = stringProp(node, "label", "");
        const placeholder = stringProp(node, "placeholder", "");
        const value = modelString(model, node.bindPath, scope);
        return <>
            {label === "" ? null : <FieldLabel>{label}</FieldLabel>}
            <Input
                value={value}
                placeholder={placeholder}
                onChange={ev => fn.sendBoundInput(node, {kind: ValueKind.String, string: ev.currentTarget.value}, model, scope)}
            />
        </>;
    },
    input_number: ({node, model, scope, fn}) => {
        const label = stringProp(node, "label", "");
        const value = modelNumber(model, node.bindPath, scope);
        const min = numberProp(node, "min", 0);
        const max = numberProp(node, "max", 0);
        return <>
            {label === "" ? null : <FieldLabel>{label}</FieldLabel>}
            <Input
                type="number"
                value={value}
                min={min}
                max={max}
                onChange={ev => {
                    const parsed = parseInt(ev.currentTarget.value, 10);
                    fn.sendBoundInput(node, {kind: ValueKind.S64, s64: isNaN(parsed) ? 0 : parsed}, model, scope);
                }}
            />
        </>;
    },
    checkbox: ({node, model, scope, fn}) => {
        const label = stringProp(node, "label", "");
        const checked = modelBool(model, node.bindPath, scope);
        return <Flex mt="8px" mb="12px" alignItems="center">
            <Checkbox
                checked={checked}
                onChange={ev => fn.sendBoundInput(node, {kind: ValueKind.Bool, bool: ev.currentTarget.checked}, model, scope)}
            />
            {label}
        </Flex>;
    },
    button: ({node, model, scope, fn}) => {
        const label = stringProp(node, "label", "Button");
        const color = stringProp(node, "color", "primaryMain");
        const iconLeft = stringProp(node, "iconLeft", "");
        const iconRight = stringProp(node, "iconRight", "");
        const eventValuePath = stringProp(node, "eventValuePath", "");
        const eventValue = eventValuePath ? modelValue(model, eventValuePath, scope) : undefined;
        return <Button
            color={color as any}
            onClick={() => fn.sendUiEvent(node.id, "click", eventValue)}
        >
            {iconLeft ? <Icon name={iconLeft as any} /> : null}
            {label}
            {iconRight ? <Icon name={iconRight as any} /> : null}
        </Button>;
    },
    list: ({node, model, scope, fn, renderChildren}) => {
        const items = modelList(model, node.bindPath, scope);
        const emptyText = stringProp(node, "emptyText", "No items yet.");
        if (items.length === 0) {
            return <Text color="textSecondary">{emptyText}</Text>;
        }

        return <ul style={{listStyle: "none", padding: 0, margin: 0, ...fn.sxStyle(node)}}>
            {items.map((entry, index) => {
                const itemScope = entry.kind === ValueKind.Object ? entry.object : {value: entry};
                const itemId = asString(itemScope["id"], String(index));

                return <li key={itemId} style={{display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, marginBottom: 8}}>
                    {renderChildren(itemScope)}
                </li>;
            })}
        </ul>;
    },
};

function mergeComponentRegistry(
    base: UcxComponentRegistry,
    overrides?: Partial<UcxComponentRegistry>
): UcxComponentRegistry {
    if (!overrides) {
        return base;
    }

    const result: UcxComponentRegistry = {...base};
    for (const [key, renderer] of Object.entries(overrides)) {
        if (renderer) {
            result[key] = renderer;
        }
    }
    return result;
}

async function resolveProvider(provider: ValueProvider): Promise<string> {
    if (typeof provider === "string") {
        return provider;
    }
    return provider();
}

function modelValue(model: Record<string, Value>, path: string, scope?: Record<string, Value>): Value | undefined {
    if (!path) return undefined;

    if (path.startsWith("./")) {
        if (!scope) {
            return undefined;
        }

        const localPath = path.substring(2);
        if (localPath === "") {
            return {kind: ValueKind.Object, object: scope};
        }
        return traverseObjectPath(scope, localPath);
    }

    const direct = model[path];
    if (direct != null) {
        return direct;
    }

    return traverseObjectPath(model, path);
}

function collectInputBindPaths(root: UiNode): Set<string> {
    const result = new Set<string>();
    const rehydratable = new Set(["input_text", "input_number", "checkbox", "textarea", "select", "toggle", "list"]);

    const walk = (node: UiNode) => {
        if (node.bindPath && rehydratable.has(node.component) && !node.bindPath.startsWith("./")) {
            result.add(node.bindPath);
        }
        for (const child of node.children) {
            walk(child);
        }
    };

    walk(root);
    return result;
}

function sxStyle(node: UiNode): React.CSSProperties {
    const sx = prop(node, "sx");
    if (!sx || sx.kind !== ValueKind.Object) {
        return {};
    }

    const style: React.CSSProperties = {};

    for (const [key, value] of Object.entries(sx.object)) {
        const primitive = valueToPrimitive(value);
        if (primitive === undefined) {
            continue;
        }

        switch (key) {
            case "m":
                style.margin = px(primitive);
                break;
            case "mt":
                style.marginTop = px(primitive);
                break;
            case "mr":
                style.marginRight = px(primitive);
                break;
            case "mb":
                style.marginBottom = px(primitive);
                break;
            case "ml":
                style.marginLeft = px(primitive);
                break;
            case "p":
                style.padding = px(primitive);
                break;
            case "pt":
                style.paddingTop = px(primitive);
                break;
            case "pr":
                style.paddingRight = px(primitive);
                break;
            case "pb":
                style.paddingBottom = px(primitive);
                break;
            case "pl":
                style.paddingLeft = px(primitive);
                break;
            case "gap":
                style.gap = px(primitive);
                break;
            case "width":
                style.width = px(primitive);
                break;
            case "height":
                style.height = px(primitive);
                break;
            case "minWidth":
                style.minWidth = px(primitive);
                break;
            case "maxWidth":
                style.maxWidth = px(primitive);
                break;
            case "display":
                style.display = String(primitive) as React.CSSProperties["display"];
                break;
            case "alignItems":
                style.alignItems = String(primitive) as React.CSSProperties["alignItems"];
                break;
            case "justifyContent":
                style.justifyContent = String(primitive) as React.CSSProperties["justifyContent"];
                break;
            case "flexWrap":
                style.flexWrap = String(primitive) as React.CSSProperties["flexWrap"];
                break;
            case "borderRadius":
                style.borderRadius = px(primitive);
                break;
            case "borderWidth":
                style.borderWidth = px(primitive);
                break;
            case "borderStyle":
                style.borderStyle = String(primitive) as React.CSSProperties["borderStyle"];
                break;
            case "borderColor":
                style.borderColor = toCssColor(String(primitive));
                break;
            case "backgroundColor":
                style.backgroundColor = toCssColor(String(primitive));
                break;
            case "color":
                style.color = toCssColor(String(primitive));
                break;
            case "fontSize":
                style.fontSize = px(primitive);
                break;
            case "fontWeight":
                style.fontWeight = String(primitive) as React.CSSProperties["fontWeight"];
                break;
            case "lineHeight":
                style.lineHeight = String(primitive) as React.CSSProperties["lineHeight"];
                break;
            case "textAlign":
                style.textAlign = String(primitive) as React.CSSProperties["textAlign"];
                break;
        }
    }

    return style;
}

function valueToPrimitive(value: Value): string | number | boolean | undefined {
    switch (value.kind) {
        case ValueKind.String:
            return value.string;
        case ValueKind.S64:
            return value.s64;
        case ValueKind.F64:
            return value.f64;
        case ValueKind.Bool:
            return value.bool;
        default:
            return undefined;
    }
}

function px(value: string | number | boolean): string {
    if (typeof value === "number") {
        return `${value}px`;
    }
    if (typeof value === "boolean") {
        return value ? "1px" : "0";
    }
    return value;
}

function toCssColor(value: string): string {
    if (value.startsWith("#") || value.startsWith("rgb") || value.startsWith("hsl") || value.startsWith("var(")) {
        return value;
    }
    return `var(--${value})`;
}

function traverseObjectPath(root: Record<string, Value>, path: string): Value | undefined {
    const parts = path.split(".");
    if (parts.length === 0) {
        return undefined;
    }

    let current: Value | undefined = root[parts[0]];
    for (let i = 1; i < parts.length; i++) {
        if (!current || current.kind !== ValueKind.Object) {
            return undefined;
        }
        current = current.object[parts[i]];
    }

    return current;
}

function modelString(model: Record<string, Value>, path: string, scope?: Record<string, Value>): string {
    return asString(modelValue(model, path, scope), "");
}

function modelNumber(model: Record<string, Value>, path: string, scope?: Record<string, Value>): number {
    const value = modelValue(model, path, scope);
    if (!value) return 0;
    if (value.kind === ValueKind.S64) return value.s64;
    if (value.kind === ValueKind.F64) return value.f64;
    return 0;
}

function modelBool(model: Record<string, Value>, path: string, scope?: Record<string, Value>): boolean {
    const value = modelValue(model, path, scope);
    if (!value || value.kind !== ValueKind.Bool) return false;
    return value.bool;
}

function modelList(model: Record<string, Value>, path: string, scope?: Record<string, Value>): Value[] {
    const value = modelValue(model, path, scope);
    if (!value || value.kind !== ValueKind.List) return [];
    return value.list;
}

function boundOrStaticText(node: UiNode, model: Record<string, Value>, scope?: Record<string, Value>): string {
    if (node.bindPath) return modelString(model, node.bindPath, scope);
    return stringProp(node, "text", "");
}

function prop(node: UiNode, key: string): Value | undefined {
    return node.props[key];
}

function stringProp(node: UiNode, key: string, fallback: string): string {
    return asString(prop(node, key), fallback);
}

function numberProp(node: UiNode, key: string, fallback: number): number {
    const value = prop(node, key);
    if (!value) return fallback;
    if (value.kind === ValueKind.S64) return value.s64;
    if (value.kind === ValueKind.F64) return value.f64;
    return fallback;
}

function asString(value: Value | undefined, fallback: string): string {
    if (value && value.kind === ValueKind.String) return value.string;
    return fallback;
}

const FieldLabel = ({children}: React.PropsWithChildren): React.ReactNode => {
    return <label style={{fontWeight: 600, marginTop: "6px"}}>{children}</label>;
};

export default UcxView;
