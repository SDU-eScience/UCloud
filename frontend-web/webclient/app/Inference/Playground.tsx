import * as React from "react";
import ReactModal from "react-modal";

import {callAPI} from "@/Authentication/DataHook";
import {dialogStore} from "@/Dialog/DialogStore";
import FileBrowse from "@/Files/FileBrowse";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Flex, Icon, Input, Text, TextArea} from "@/ui-components";
import CodeSnippet from "@/ui-components/CodeSnippet";
import {Toggle} from "@/ui-components/Toggle";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {Selection} from "@/ui-components/ResourceBrowser";
import {UcxComponentRegistry, UcxRenderContext} from "@/UCX/UcxView";
import UcxView from "@/UCX/UcxView";
import {Value, ValueKind} from "@/UCX/protocol";
import {usePrettyFilePath} from "@/Files/FilePath";
import {UFile} from "@/UCloud/UFile";
import {doNothing, removeTrailingSlash} from "@/UtilityFunctions";

import {openPlayground} from "./api";
import {getParentPath} from "@/Utilities/FileUtilities";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";

type PlaygroundSession = {
    connectTo: string;
    sessionToken: string;
};

const MAX_RECONNECT_ATTEMPTS = 5;

const playgroundComponents: UcxComponentRegistry = {
    inference_toggle: ({node, model, scope, fn}: UcxRenderContext) => {
        const label = stringProp(node, "label", "");
        const checked = boolValue(fn.modelValue(model, node.bindPath, scope));

        return <div style={{display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8, width: "100%", ...fn.sxStyle(node)}}>
            {label === "" ? <span /> : <span
                style={{fontWeight: 600, userSelect: "none", cursor: "pointer"}}
                onClick={() => fn.sendBoundInput(node, {kind: ValueKind.Bool, bool: !checked}, model, scope)}
            >
                {label}
            </span>}
            <Toggle
                height={18}
                checked={checked}
                onChange={() => fn.sendBoundInput(node, {kind: ValueKind.Bool, bool: !checked}, model, scope)}
            />
        </div>;
    },

    inference_image_preview: ({node, model, scope, fn}: UcxRenderContext) => {
        return <ImagePreviewNode node={node} model={model} scope={scope} fn={fn} />;
    },

    inference_transcription_composer: ({node, model, scope, fn}: UcxRenderContext) => {
        const filePathBindPath = stringProp(node, "filePathBindPath", "transcriptionFilePath");
        const promptBindPath = stringProp(node, "promptBindPath", "transcriptionPrompt");
        const filePathPlaceholder = stringProp(node, "filePathPlaceholder", "Audio file path");
        const promptPlaceholder = stringProp(node, "promptPlaceholder", "Optional prompt");
        const rows = numberProp(node, "rows", 4);
        const sendIcon = stringProp(node, "sendIcon", "heroPaperAirplane");
        const disabled = boolProp(node, "disabled", false);
        const filePath = stringValue(fn.modelValue(model, filePathBindPath, scope));
        const prettyPath = usePrettyFilePath(filePath);
        const prompt = stringValue(fn.modelValue(model, promptBindPath, scope));
        const canSend = !disabled && filePath.trim() !== "";

        const send = () => {
            if (!canSend) return;
            fn.sendUiEvent(node.id, "click", {kind: ValueKind.Null});
        };

        const openFileBrowser = () => {
            const selection: Selection<UFile> = {
                text: "Use",
                onClick: res => {
                    const target = removeTrailingSlash(res.id);
                    fn.sendBoundInput({...node, bindPath: filePathBindPath} as any, {kind: ValueKind.String, string: target}, model, scope);
                    dialogStore.success();
                },
                show: file => file.status.type === "FILE",
            };

            dialogStore.addDialog(
                <FileBrowse
                    opts={{
                        isModal: true,
                        managesLocalProject: true,
                        initialPath: getParentPath(filePath) ?? "",
                        selection,
                        additionalOperations: [],
                    }}
                />,
                doNothing,
                true,
                FilesApi.fileSelectorModalStyle,
            );
        };

        return <Box style={{position: "relative", width: "100%", display: "flex", flexDirection: "column", gap: 8}}>
            <div style={{display: "flex", gap: 8, alignItems: "center"}}>
                <Input
                    value={prettyPath}
                    placeholder={filePathPlaceholder}
                    readOnly={true}
                    onClick={openFileBrowser}
                    title={filePath || filePathPlaceholder}
                    style={{cursor: "pointer"}}
                />
                <Button type="button" onClick={openFileBrowser} m={0}>Browse</Button>
            </div>
            <TextArea
                rows={rows}
                placeholder={promptPlaceholder}
                value={prompt}
                onChange={ev => fn.sendBoundInput({...node, bindPath: promptBindPath} as any, {kind: ValueKind.String, string: ev.currentTarget.value}, model, scope)}
                onKeyDown={ev => {
                    if ((ev.ctrlKey || ev.metaKey) && ev.key === "Enter") {
                        ev.preventDefault();
                        ev.stopPropagation();
                        send();
                    }
                }}
                style={{paddingRight: 96, paddingBottom: 44, resize: "none"}}
            />
            <div style={{position: "absolute", right: 24, bottom: 12, padding: 0, width: 36, height: 36}}>
                <Button type="button" disabled={!canSend} onClick={send} m={0}>
                    <Icon name={sendIcon as any} size={14} />
                </Button>
            </div>
        </Box>;
    },

    inference_image_composer: ({node, model, scope, fn}: UcxRenderContext) => {
        const placeholder = stringProp(node, "placeholder", "Describe the image to generate");
        const rows = numberProp(node, "rows", 4);
        const sendIcon = stringProp(node, "sendIcon", "heroSparkles");
        const disabled = boolProp(node, "disabled", false);
        const value = stringValue(fn.modelValue(model, node.bindPath, scope));
        const canSend = !disabled && value.trim() !== "";

        const send = () => {
            if (!canSend) return;
            fn.sendUiEvent(node.id, "click", {kind: ValueKind.String, string: value});
        };

        return <Box style={{position: "relative", width: "100%"}}>
            <TextArea
                rows={rows}
                placeholder={placeholder}
                value={value}
                onChange={ev => fn.sendBoundInput(node, {kind: ValueKind.String, string: ev.currentTarget.value}, model, scope)}
                onKeyDown={ev => {
                    if ((ev.ctrlKey || ev.metaKey) && ev.key === "Enter") {
                        ev.preventDefault();
                        ev.stopPropagation();
                        send();
                    }
                }}
                style={{paddingRight: 96, paddingBottom: 44, resize: "none"}}
            />
            <div style={{position: "absolute", right: 24, bottom: 12, padding: 0, width: 36, height: 36}}>
                <Button type="button" disabled={!canSend} onClick={send} m={0}>
                    <Icon name={sendIcon as any} size={14} />
                </Button>
            </div>
        </Box>;
    },

    inference_chat_composer: ({node, model, scope, fn}: UcxRenderContext) => {
        const placeholder = stringProp(node, "placeholder", "Ask something");
        const rows = numberProp(node, "rows", 8);
        const sendIcon = stringProp(node, "sendIcon", "heroPaperAirplane");
        const disabled = boolProp(node, "disabled", false);
        const value = stringValue(fn.modelValue(model, node.bindPath, scope));
        const canSend = !disabled && value.trim() !== "";

        const send = () => {
            if (!canSend) return;
            fn.sendUiEvent(node.id, "click", {kind: ValueKind.String, string: value});
        };

        return <Box style={{position: "relative", width: "100%"}}>
            <TextArea
                rows={rows}
                placeholder={placeholder}
                value={value}
                onChange={ev => fn.sendBoundInput(node, {kind: ValueKind.String, string: ev.currentTarget.value}, model, scope)}
                onKeyDown={ev => {
                    if ((ev.ctrlKey || ev.metaKey) && ev.key === "Enter") {
                        ev.preventDefault();
                        ev.stopPropagation();
                        send();
                    }
                }}
                style={{paddingRight: 96, paddingBottom: 44, resize: "none"}}
            />
            <div style={{position: "absolute", right: 24, bottom: 12, padding: 0, width: 36, height: 36}}>
                <Button
                    type="button"
                    disabled={!canSend}
                    onClick={send}
                    m={0}
                >
                    <Icon name={sendIcon as any} size={14} />
                </Button>
            </div>
        </Box>;
    },

    inference_chat_box: ({node, model, scope, fn, renderChildren}: UcxRenderContext) => {
        const containerRef = React.useRef<HTMLDivElement | null>(null);

        React.useLayoutEffect(() => {
            const el = containerRef.current;
            if (!el) return;
            el.scrollTop = el.scrollHeight;
        }, [model, scope]);

        return <div ref={containerRef} style={{overflowY: "auto", ...fn.sxStyle(node)}}>{renderChildren()}</div>
    },
};

export default function Playground(): React.ReactNode {
    const [session, setSession] = React.useState<PlaygroundSession | null>(null);
    const [loading, setLoading] = React.useState(true);
    const [terminalError, setTerminalError] = React.useState("");
    const [refreshNonce, setRefreshNonce] = React.useState(0);
    const retryCountRef = React.useRef(0);
    const mountedRef = React.useRef(true);
    const projectId = useProjectId();

    React.useEffect(() => {
        return () => {
            mountedRef.current = false;
        };
    }, []);

    React.useEffect(() => {
        setRefreshNonce(x => x + 1);
    }, [projectId])

    React.useEffect(() => {
        let cancelled = false;

        setLoading(true);
        void callAPI(openPlayground({providerId: null}))
            .then(result => {
                if (cancelled) return;
                setSession(result);
                setLoading(false);
                setTerminalError("");
            })
            .catch(err => {
                if (cancelled) return;
                setSession(null);
                setLoading(false);
                setTerminalError(err instanceof Error ? err.message : "Failed to open the inference playground");
            });

        return () => {
            cancelled = true;
        };
    }, [refreshNonce]);

    const handleConnected = React.useCallback(() => {
        if (!mountedRef.current) return;
        retryCountRef.current = 0;
        setTerminalError("");
    }, []);

    const handleDisconnected = React.useCallback(() => {
        if (!mountedRef.current) return;

        const nextRetry = retryCountRef.current + 1;
        if (nextRetry > MAX_RECONNECT_ATTEMPTS) {
            setTerminalError("Disconnected. Reconnect limit reached.");
            return;
        }

        retryCountRef.current = nextRetry;
        setRefreshNonce(v => v + 1);
    }, []);

    if (!session) {
        return <MainContainer main={<div style={{padding: 24}}>
            <Text>{loading ? "Opening inference playground..." : "Unable to open inference playground."}</Text>
            {terminalError === "" ? null : <Text color="errorMain">{terminalError}</Text>}
        </div>} />;
    }

    return <MainContainer main={<div style={{display: "flex", flexDirection: "column", gap: 8}}>
        <Flex mb={24}>
            <h3 className="title" style={{marginTop: "auto", marginBottom: "auto"}}>AI Inference: Playground</h3>
            <Box flexGrow={1} />
            <ProjectSwitcher />
        </Flex>
        {terminalError === "" ? null : <Text color="errorMain">{terminalError}</Text>}
        <UcxView
            url={session.connectTo}
            authToken={session.sessionToken}
            sysHello={JSON.stringify({})}
            maxReconnectAttempts={MAX_RECONNECT_ATTEMPTS}
            onConnected={handleConnected}
            onDisconnected={handleDisconnected}
            components={playgroundComponents}
        />
    </div>} />;
}

function stringProp(node: {props?: Record<string, Value>}, key: string, fallback: string): string {
    const value = node.props?.[key];
    if (value && value.kind === ValueKind.String && value.string !== "") {
        return value.string;
    }
    return fallback;
}

function numberProp(node: {props?: Record<string, Value>}, key: string, fallback: number): number {
    const value = node.props?.[key];
    if (!value) return fallback;
    if (value.kind === ValueKind.S64) return value.s64;
    if (value.kind === ValueKind.F64) return value.f64;
    return fallback;
}

function boolProp(node: {props?: Record<string, Value>}, key: string, fallback: boolean): boolean {
    const value = node.props?.[key];
    if (value && value.kind === ValueKind.Bool) {
        return value.bool;
    }
    return fallback;
}

function stringValue(value: any): string {
    if (!value || value.kind !== ValueKind.String) return "";
    return value.string ?? "";
}

function boolValue(value: any): boolean {
    if (!value || value.kind !== ValueKind.Bool) return false;
    return value.bool;
}

type ImagePreviewNodeProps = Pick<UcxRenderContext, "node" | "model" | "scope" | "fn">;

const ImagePreviewNode: React.FC<ImagePreviewNodeProps> = ({node, model, scope, fn}) => {
    const output = stringValue(fn.modelValue(model, node.bindPath, scope));
    const images = React.useMemo(() => parseImagePreviewItems(output), [output]);
    const [selectedImage, setSelectedImage] = React.useState<string | null>(null);
    const [selectedIndex, setSelectedIndex] = React.useState(0);

    React.useEffect(() => {
        if (selectedImage !== null && !images.includes(selectedImage)) {
            setSelectedImage(null);
            setSelectedIndex(0);
        }
    }, [images, selectedImage]);

    const openImage = (src: string, idx: number) => {
        setSelectedImage(src);
        setSelectedIndex(idx);
    };

    const closeImage = () => {
        setSelectedImage(null);
        setSelectedIndex(0);
    };

    return <div style={{display: "flex", flexDirection: "column", gap: 12, ...fn.sxStyle(node)}}>
        {output.trim() === "" ? <Text color="textSecondary">You have not requested anything yet.</Text> : null}
        {output.trim() !== "" && images.length === 0 ? <Text color="textSecondary">Image preview is not possible for this response format.</Text> : null}
        {images.length === 0 ? null : <div style={{display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12}}>
            {images.map((src, idx) => <button
                key={`${idx}-${src.slice(0, 12)}`}
                type="button"
                onClick={() => openImage(src, idx)}
                style={{border: "1px solid var(--borderColor)", borderRadius: 8, padding: 8, background: "var(--backgroundDefault)", minWidth: 0, display: "block", textDecoration: "none", color: "inherit", cursor: "pointer", textAlign: "left"}}
            >
                <img
                    src={src}
                    alt={`Generated image ${idx + 1}`}
                    style={{display: "block", width: "100%", maxHeight: 280, objectFit: "contain", borderRadius: 6}}
                />
            </button>)}
        </div>}
        <ReactModal
            isOpen={selectedImage !== null}
            onRequestClose={closeImage}
            style={{
                ...largeModalStyle,
                content: {
                    ...largeModalStyle.content,
                    width: "92vw",
                    height: "92vh",
                    maxHeight: "92vh",
                    top: "4vh",
                    left: "4vw",
                    overflow: "hidden",
                    display: "flex",
                    flexDirection: "column",
                    gap: 12,
                },
            }}
            ariaHideApp={false}
        >
            <div style={{display: "flex", justifyContent: "space-between", alignItems: "center", gap: 16}}>
                <Text style={{fontWeight: 600}}>Image {selectedIndex + 1}</Text>
                <Button type="button" onClick={closeImage} m={0}>Close</Button>
            </div>
            {selectedImage === null ? null : <div style={{flex: 1, minHeight: 0, display: "flex", alignItems: "center", justifyContent: "center", padding: 16, background: "var(--backgroundDefault)", borderRadius: 8}}>
                <img
                    src={selectedImage}
                    alt={`Generated image ${selectedIndex + 1}`}
                    style={{maxWidth: "100%", maxHeight: "100%", objectFit: "contain", borderRadius: 8}}
                />
            </div>}
        </ReactModal>
        {output.trim() === "" ? null :
            <div style={{display: "flex", flexDirection: "column", gap: 8}}>
                <Text style={{fontWeight: 600}}>JSON response:</Text>
                <CodeSnippet maxHeight="320px">{output}</CodeSnippet>
            </div>
        }
    </div>;
};

function parseImagePreviewItems(output: string): string[] {
    if (!output.trim()) return [];

    let parsed: any;
    try {
        parsed = JSON.parse(output);
    } catch {
        return [];
    }

    const items: string[] = [];
    if (parsed && Array.isArray(parsed.data)) {
        for (const item of parsed.data) {
            if (!item || typeof item !== "object") continue;
            const url = typeof item.url === "string" ? item.url : "";
            const b64 = typeof item.b64_json === "string" ? item.b64_json : "";
            if (url.startsWith("data:")) {
                items.push(url);
            } else if (b64 !== "") {
                items.push(`data:image/png;base64,${b64}`);
            }
        }
        return items;
    }

    const url = typeof parsed?.url === "string" ? parsed.url : "";
    const b64 = typeof parsed?.b64_json === "string" ? parsed.b64_json : "";
    if (url.startsWith("data:")) {
        items.push(url);
    } else if (b64 !== "") {
        items.push(`data:image/png;base64,${b64}`);
    }
    return items;
}
