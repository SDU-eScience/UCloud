import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {MainContainer, Button, Card, Checkbox, Flex, Icon, Input, Text} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {
    decodeFrame,
    encodeFrame,
    FormActionRes,
    Opcode,
    UiNode,
    Value,
    ValueKind,
} from "@/Playground/UcxCreateDemo/protocol";

const UcxCreateDemo: React.FunctionComponent = () => {
    const [connected, setConnected] = useState(false);
    const [root, setRoot] = useState<UiNode | null>(null);
    const [interfaceId, setInterfaceId] = useState("job-create-demo");
    const [model, setModel] = useState<Record<string, Value>>({});
    const [modelVersion, setModelVersion] = useState(0);
    const [transportError, setTransportError] = useState("");
    const [lastAction, setLastAction] = useState<FormActionRes | null>(null);

    const connRef = useRef<WebSocket | null>(null);
    const seqRef = useRef(1);
    const eventIdRef = useRef(1);
    const reconnectAttemptRef = useRef(0);
    const reconnectTimerRef = useRef<number | null>(null);
    const everConnectedRef = useRef(false);
    const pendingRehydrateModelRef = useRef<Record<string, Value> | null>(null);

    const modelRef = useRef<Record<string, Value>>({});
    const modelVersionRef = useRef(0);
    const interfaceIdRef = useRef("job-create-demo");

    useEffect(() => {
        modelRef.current = model;
    }, [model]);

    useEffect(() => {
        modelVersionRef.current = modelVersion;
    }, [modelVersion]);

    useEffect(() => {
        interfaceIdRef.current = interfaceId;
    }, [interfaceId]);

    const sendFrame = useCallback((payloadBuilder: (seq: number) => Uint8Array) => {
        const conn = connRef.current;
        if (!conn || conn.readyState !== WebSocket.OPEN) {
            return;
        }

        const seq = seqRef.current++;
        const bytes = payloadBuilder(seq);
        conn.send(bytes);
    }, []);

    const applyModelChangeLocally = useCallback((path: string, value: Value) => {
        setModel(prev => ({...prev, [path]: value}));
    }, []);

    const sendBoundInput = useCallback((node: UiNode, value: Value) => {
        if (!node.bindPath) return;

        if (node.optimistic) {
            applyModelChangeLocally(node.bindPath, value);
        }

        const eventId = eventIdRef.current++;
        sendFrame(seq => encodeFrame({
            seq,
            replyToSeq: 0,
            opcode: Opcode.ModelInput,
            modelInput: {
                eventId,
                nodeId: node.id,
                path: node.bindPath,
                value,
                baseVersion: modelVersionRef.current,
            },
        }));
    }, [applyModelChangeLocally, sendFrame]);

    const sendUiClick = useCallback((nodeId: string, value?: Value) => {
        sendFrame(seq => encodeFrame({
            seq,
            replyToSeq: 0,
            opcode: Opcode.UiEvent,
            uiEvent: {
                nodeId,
                event: "click",
                value: value ?? {kind: ValueKind.Null},
            },
        }));
    }, [sendFrame]);

    const submitFormAction = useCallback(() => {
        sendFrame(seq => encodeFrame({
            seq,
            replyToSeq: 0,
            opcode: Opcode.FormActionReq,
            formActionReq: {
                interfaceId: interfaceIdRef.current,
                action: "submit",
                fields: modelRef.current,
                context: {
                    username: {kind: ValueKind.String, string: Client.activeUsername ?? "anonymous"},
                },
            },
        }));
    }, [sendFrame]);

    const resendModelAfterReconnect = useCallback((snapshot: Record<string, Value>, mount: {version: number; root: UiNode}) => {
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
            sendFrame(seq => encodeFrame({
                seq,
                replyToSeq: 0,
                opcode: Opcode.ModelInput,
                modelInput: {
                    eventId,
                    nodeId: `rehydrate:${path}`,
                    path,
                    value,
                    baseVersion: mount.version,
                },
            }));
        }
    }, [sendFrame]);

    useEffect(() => {
        const url = Client.computeURL("/api", "/ucxCreateDemo")
            .replace("http://", "ws://")
            .replace("https://", "wss://");

        let disposed = false;

        const clearReconnectTimer = () => {
            if (reconnectTimerRef.current != null) {
                window.clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
            }
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

            setTransportError(`Disconnected. Reconnecting in ${Math.max(1, Math.ceil(delay / 1000))}s...`);
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

            socket.onopen = () => {
                reconnectAttemptRef.current = 0;
                clearReconnectTimer();
                setConnected(true);
                setTransportError("");

                if (everConnectedRef.current && Object.keys(modelRef.current).length > 0) {
                    pendingRehydrateModelRef.current = {...modelRef.current};
                }
                everConnectedRef.current = true;

                const hello = encodeFrame({
                    seq: seqRef.current++,
                    replyToSeq: 0,
                    opcode: Opcode.SysHello,
                    sysHello: {
                        host: "webclient",
                        features: ["binary", "ui-mount", "model-patch", "model-input", "optimistic", "reconnect"],
                    },
                });
                socket.send(hello);
            };

            socket.onmessage = event => {
                try {
                    if (!(event.data instanceof ArrayBuffer)) {
                        setTransportError("Received non-binary websocket frame");
                        return;
                    }
                    const bytes = new Uint8Array(event.data);

                    const frame = decodeFrame(bytes);
                    if (frame.opcode === Opcode.UiMount && frame.uiMount) {
                        const mount = frame.uiMount;
                        const rehydrateSnapshot = pendingRehydrateModelRef.current;

                        setInterfaceId(mount.interfaceId);
                        setRoot(mount.root);
                        setModel(mount.model);
                        setModelVersion(mount.version);

                        if (rehydrateSnapshot) {
                            pendingRehydrateModelRef.current = null;
                            resendModelAfterReconnect(rehydrateSnapshot, {
                                version: mount.version,
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
                        setModelVersion(frame.modelPatch.version);
                    } else if (frame.opcode === Opcode.FormActionRes && frame.formActionRes) {
                        setLastAction(frame.formActionRes);
                    }
                } catch (err) {
                    setTransportError(err instanceof Error ? err.message : "Failed to decode frame");
                }
            };

            socket.onclose = () => {
                if (connRef.current === socket) {
                    connRef.current = null;
                }
                setConnected(false);
                if (!disposed) {
                    scheduleReconnect();
                }
            };

            socket.onerror = () => {
                setTransportError("WebSocket error");
                socket.close();
            };
        };

        connect();

        return () => {
            disposed = true;
            clearReconnectTimer();
            const conn = connRef.current;
            connRef.current = null;
            if (conn) {
                conn.close();
            }
        };
    }, [resendModelAfterReconnect]);

    return <MainContainer
        main={<div className={WrapperClass}>
            <Card className={CardClass}>
                <h2>UCX Create Demo</h2>
                <Text color={connected ? "successMain" : "warningMain"}>
                    {connected ? "Connected" : "Connecting..."}
                </Text>
                <Text>Model version: {modelVersion}</Text>
                {transportError === "" ? null : <Text color="errorMain">Transport error: {transportError}</Text>}

                {root == null ? <Text>Waiting for UI mount...</Text> :
                    <NodeRenderer
                        node={root}
                        model={model}
                        sendBoundInput={sendBoundInput}
                        sendUiClick={sendUiClick}
                        submitFormAction={submitFormAction}
                    />
                }

                {lastAction == null ? null : <Text mt="12px">
                    {lastAction.ok ? "Form action accepted" : `Form action failed: ${lastAction.errorMessage}`}
                </Text>}
            </Card>
        </div>}
    />;
};

const NodeRenderer: React.FunctionComponent<{
    node: UiNode;
    model: Record<string, Value>;
    scope?: Record<string, Value>;
    sendBoundInput: (node: UiNode, value: Value) => void;
    sendUiClick: (nodeId: string, value?: Value) => void;
    submitFormAction: () => void;
}> = ({node, model, scope, sendBoundInput, sendUiClick, submitFormAction}) => {
    switch (node.component) {
        case "flex": {
            const direction = stringProp(node, "direction", "column");
            const gap = numberProp(node, "gap", 8);
            const stackStyle: React.CSSProperties = {
                display: "flex",
                flexDirection: direction as React.CSSProperties["flexDirection"],
                gap: `${gap}px`,
                width: "100%",
                ...sxStyle(node),
            };
            return <div style={stackStyle}>
                {node.children.map(child =>
                    <NodeRenderer
                        key={child.id}
                        node={child}
                        model={model}
                        scope={scope}
                        sendBoundInput={sendBoundInput}
                        sendUiClick={sendUiClick}
                        submitFormAction={submitFormAction}
                    />
                )}
            </div>;
        }

        case "heading": {
            const text = boundOrStaticText(node, model, scope);
            if (!text) return null;

            const level = Math.max(1, Math.min(6, Math.trunc(numberProp(node, "level", 3))));
            const tag = `h${level}` as keyof JSX.IntrinsicElements;
            const color = stringProp(node, "color", "");
            const headingStyle: React.CSSProperties = {
                margin: 0,
                ...sxStyle(node),
            };
            if (color) {
                headingStyle.color = toCssColor(color);
            }

            return React.createElement(tag, {style: headingStyle}, text);
        }

        case "text": {
            const text = boundOrStaticText(node, model, scope);
            if (!text) return null;
            const color = stringProp(node, "color", "");
            return <Text color={(color || undefined) as any} style={sxStyle(node)}>{text}</Text>;
        }

        case "icon": {
            const name = stringProp(node, "name", "bug");
            const color = stringProp(node, "color", "iconColor");
            const size = numberProp(node, "size", 18);
            return <Icon name={name as any} color={color as any} size={size} style={sxStyle(node)} />;
        }

        case "input_text": {
            const label = stringProp(node, "label", "");
            const placeholder = stringProp(node, "placeholder", "");
            const value = modelString(model, node.bindPath, scope);
            return <>
                {label === "" ? null : <FieldLabel>{label}</FieldLabel>}
                <Input
                    value={value}
                    placeholder={placeholder}
                    onChange={ev => sendBoundInput(node, {kind: ValueKind.String, string: ev.currentTarget.value})}
                />
            </>;
        }

        case "input_number": {
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
                        sendBoundInput(node, {kind: ValueKind.S64, s64: isNaN(parsed) ? 0 : parsed});
                    }}
                />
            </>;
        }

        case "checkbox": {
            const label = stringProp(node, "label", "");
            const checked = modelBool(model, node.bindPath, scope);
            return <Flex mt="8px" mb="12px" alignItems="center">
                <Checkbox
                    checked={checked}
                    onChange={ev => sendBoundInput(node, {kind: ValueKind.Bool, bool: ev.currentTarget.checked})}
                />
                {label}
            </Flex>;
        }

        case "button": {
            const label = stringProp(node, "label", "Button");
            const color = stringProp(node, "color", "primaryMain");
            const iconLeft = stringProp(node, "iconLeft", "");
            const iconRight = stringProp(node, "iconRight", "");
            const eventValuePath = stringProp(node, "eventValuePath", "");
            const eventValue = eventValuePath ? modelValue(model, eventValuePath, scope) : undefined;
            return <Button
                color={color as any}
                style={sxStyle(node)}
                onClick={() => {
                    sendUiClick(node.id, eventValue);
                    if (node.id === "submitForm") {
                        submitFormAction();
                    }
                }}
            >
                {iconLeft ? <Icon name={iconLeft as any} /> : null}
                {label}
                {iconRight ? <Icon name={iconRight as any} /> : null}
            </Button>;
        }

        case "list": {
            const items = modelList(model, node.bindPath, scope);
            const emptyText = stringProp(node, "emptyText", "No items yet.");
            if (items.length === 0) {
                return <Text color="textSecondary">{emptyText}</Text>;
            }

            return <ul className={TodoListClass} style={sxStyle(node)}>
                {items.map((entry, index) => {
                    const itemScope = entry.kind === ValueKind.Object ? entry.object : {value: entry};
                    const itemId = asString(itemScope["id"], String(index));

                    return <li key={itemId}>
                        {node.children.map((child, childIndex) =>
                            <NodeRenderer
                                key={`${itemId}:${child.id}:${childIndex}`}
                                node={child}
                                model={model}
                                scope={itemScope}
                                sendBoundInput={sendBoundInput}
                                sendUiClick={sendUiClick}
                                submitFormAction={submitFormAction}
                            />
                        )}
                    </li>;
                })}
            </ul>;
        }

        default:
            return null;
    }
};

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

const WrapperClass = injectStyle("ucx-create-wrapper", k => `
    ${k} {
        display: flex;
        justify-content: center;
        padding: 24px;
    }
`);

const CardClass = injectStyle("ucx-create-card", k => `
    ${k} {
        width: min(920px, 100%);
        padding: 16px;
        border-radius: 12px;
        display: flex;
        flex-direction: column;
        gap: 8px;
    }
`);

const TodoListClass = injectStyle("ucx-create-todos", k => `
    ${k} {
        list-style: none;
        padding: 0;
        margin: 0;
    }

    ${k} > li {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 8px;
        margin-bottom: 8px;
    }
`);

const FieldLabel = ({children}: React.PropsWithChildren): React.ReactNode => {
    return <label style={{fontWeight: 600, marginTop: "6px"}}>{children}</label>;
};

export default UcxCreateDemo;
