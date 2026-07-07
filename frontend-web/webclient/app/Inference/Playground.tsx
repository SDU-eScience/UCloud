import * as React from "react";

import {callAPI} from "@/Authentication/DataHook";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Flex, Icon, Text, TextArea,} from "@/ui-components";
import {Toggle} from "@/ui-components/Toggle";
import UcxView, {UcxComponentRegistry, UcxFunctionRegistry, UcxRenderContext, UcxSpinner} from "@/UCX/UcxView";
import {UiNode, Value, ValueKind} from "@/UCX/protocol";
import {copyToClipboard, doNothing, extensionFromPath, extensionType, typeFromMime} from "@/UtilityFunctions";
import {addStandardInputDialog} from "@/UtilityComponents";
import {sendFailureNotification} from "@/Notifications";
import {Operation, Operations, ShortcutKey} from "@/ui-components/Operation";
import {openPlayground} from "./api";
import {sizeToString} from "@/Utilities/FileUtilities";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import Tooltip from "@/ui-components/Tooltip";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {RichSelect} from "@/ui-components/RichSelect";
import {format, isToday} from "date-fns";
import ModelInferenceLogo from "./ModelLogo";
import {MarkdownDocument, MarkdownTable} from "@/ui-components/Markdown";
import {CopyButton} from "@/ui-components/CopyButton";
import {IconButton} from "@/ui-components/IconButton";
import {ChunkedFileReader} from "@/Files/ChunkedFileReader";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";

type PlaygroundSession = {
    connectTo: string;
    sessionToken: string;
};

type PlaygroundOption = { key: string; value: string };
type PlaygroundAttachmentKind = "image" | "video" | "audio" | "text" | "convertible" | "unsupported";
type PlaygroundUploadAttachment = {
    localId: string;
    kind: Exclude<PlaygroundAttachmentKind, "unsupported">;
    attachmentId: string | null;
    markdownAttachmentId: string | null;
    fileName: string;
    fileSize: number;
    uploadedBytes: number;
    status: "uploading" | "converting" | "uploaded" | "error";
    error: string;
    text: string;
};

const ComposerActionButtonClass = injectStyleSimple("inference-composer-action", `
    width: 32px;
    height: 32px;
    border: 0;
    border-radius: 999px;
    background: transparent;
    color: var(--textPrimary);
    display: inline-flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: background-color 120ms ease, opacity 120ms ease;
`);

const ComposerActionButtonHoverClass = injectStyle("inference-composer-action-hover", k => `
    ${k} .${ComposerActionButtonClass}:not(:disabled):hover {
        background: var(--playground-hover, var(--dialogToolbar));
    }

    ${k} .${ComposerActionButtonClass}:disabled {
        cursor: not-allowed;
        opacity: 0.45;
    }
`);

const PlaygroundThemeClass = injectStyle("inference-playground-theme", k => `
    ${k} {
        --playground-panel: #ffffff;
        --playground-surface: var(--backgroundDefault);
        --playground-surface-raised: var(--dialogToolbar);
        --playground-hover: var(--dialogToolbar);
        --playground-active: var(--secondaryMain);
        --playground-user-bg: var(--secondaryMain);
        --playground-user-text: var(--textPrimary);
        --playground-logo-bg: var(--secondaryMain);
        --playground-border: var(--borderColor);
        --playground-border-hover: var(--borderColorHover);
    }

    html.dark ${k} {
        --backgroundDefault: #161719;
        --dialogToolbar: #2a2d32;
        --borderColor: #3d424a;
        --borderColorHover: #5a616c;
        --textSecondary: #b2b7bf;
        --playground-panel: #22252a;
        --playground-surface: #121314;
        --playground-surface-raised: #2a2d32;
        --playground-hover: #30343a;
        --playground-active: #3d4148;
        --playground-user-bg: #383c43;
        --playground-user-text: #f3f5f7;
        --playground-logo-bg: #292c31;
        --playground-border: #3f444c;
        --playground-border-hover: #626975;
    }
`);

const ThreadListClass = injectStyle("inference-thread-list", k => `
    ${k} .inference-thread-row {
        display: flex;
        align-items: center;
        gap: 4px;
        border-radius: 8px;
        background: transparent;
    }

    ${k} .inference-thread-row[data-active="true"],
    ${k} .inference-thread-row:hover[data-active="true"] {
        background: var(--playground-active, var(--secondaryMain));
    }

    ${k} .inference-thread-row:hover {
        background: var(--playground-hover, var(--dialogToolbar));
    }
`);

const MAX_RECONNECT_ATTEMPTS = 5;
const MAX_TEXT_ATTACHMENT_BYTES = 128 * 1024;
const PLAYGROUND_REHYDRATE_PATHS = [
    "developer",
    "chat.modelId",
    "chat.streaming",
    "chat.maxCompletionTokens",
    "chat.temperature",
    "chat.topP",
    "chat.systemPrompt",
    "chat.presencePenalty",
    "chat.frequencyPenalty",
    "chat.logprobs",
    "chat.topLogprobs",
];
const PlaygroundProviderDomainContext = React.createContext("");
const PlaygroundComposerDraftContext = React.createContext<{draft: string; setDraft: (draft: string) => void} | null>(null);

const playgroundComponents: UcxComponentRegistry = {
    inference_toggle: ({node, model, scope, fn}: UcxRenderContext) => {
        const label = stringProp(node, "label", "");
        const checked = boolValue(fn.modelValue(model, node.bindPath, scope));

        return (
            <div
                style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: 8,
                    width: "100%",
                    ...fn.sxStyle(node),
                }}
            >
                {label === "" ? (
                    <span/>
                ) : (
                    <span
                        style={{fontWeight: 600, userSelect: "none", cursor: "pointer"}}
                        onClick={() =>
                            fn.sendBoundInput(
                                node,
                                {kind: ValueKind.Bool, bool: !checked},
                                model,
                                scope
                            )
                        }
                    >
            {label}
          </span>
                )}
                <Toggle
                    height={18}
                    checked={checked}
                    onChange={() =>
                        fn.sendBoundInput(
                            node,
                            {kind: ValueKind.Bool, bool: !checked},
                            model,
                            scope
                        )
                    }
                />
            </div>
        );
    },

    inference_chat_composer: ({node, model, scope, fn}: UcxRenderContext) => {
        const placeholder = stringProp(node, "placeholder", "Ask something");
        const rows = numberProp(node, "rows", 8);
        const sendIcon = stringProp(node, "sendIcon", "heroPaperAirplane");
        const disabled = boolProp(node, "disabled", false);
        const propModelOptions = optionsProp(node, "modelOptions");
        const modelOptions = propModelOptions.length > 0 ? propModelOptions : textGenerationModelOptions(fn.modelValue(model, "models"));
        const selectedModel = stringValue(fn.modelValue(model, "chat.modelId", scope));
        const selectedModelOption = modelOptions.find(option => option.key === selectedModel);
        const selectedCapabilities = modelCapabilities(model, selectedModel);
        const draftContext = React.useContext(PlaygroundComposerDraftContext);
        const [localDraft, setLocalDraft] = React.useState(() => stringValue(fn.modelValue(model, node.bindPath, scope)));
        const value = draftContext?.draft ?? localDraft;
        const setValue = React.useCallback((next: string) => {
            if (draftContext) {
                draftContext.setDraft(next);
            } else {
                setLocalDraft(next);
            }
        }, [draftContext]);
        const providerDomain = React.useContext(PlaygroundProviderDomainContext);
        const fileInputRef = React.useRef<HTMLInputElement | null>(null);
        const uploadCancelRef = React.useRef<Record<string, {cancelled: boolean; attachmentId: string | null}>>({});
        const [attachments, setAttachments] = React.useState<PlaygroundUploadAttachment[]>([]);
        const canSend = !disabled && value.trim() !== "" && attachments.every(attachment => attachment.status === "uploaded");

        const send = () => {
            if (!canSend) return;
            const sentAttachments = attachments.filter(attachment => attachment.kind === "text" || attachment.kind === "convertible" || attachment.attachmentId !== null);
            fn.sendUiEvent(node.id, "click", {
                kind: ValueKind.Object,
                object: {
                    prompt: {kind: ValueKind.String, string: value},
                    attachments: {
                        kind: ValueKind.List,
                        list: sentAttachments.map(attachment => ({
                            kind: ValueKind.Object,
                            object: {
                                kind: {kind: ValueKind.String, string: attachment.kind === "convertible" ? "text" : attachment.kind},
                                attachmentId: {kind: ValueKind.String, string: attachment.attachmentId ?? ""},
                                fileName: {kind: ValueKind.String, string: attachment.fileName},
                                url: {kind: ValueKind.String, string: attachment.attachmentId === null || attachment.kind === "convertible" ? "" : playgroundAttachmentUrl(providerDomain, attachment.attachmentId)},
                                text: {kind: ValueKind.String, string: attachment.text},
                            },
                        })),
                    },
                },
            });
            setValue("");
            setAttachments([]);
            uploadCancelRef.current = {};
        };

        const removeAttachment = (localId: string) => {
            const cancelState = uploadCancelRef.current[localId];
            if (cancelState) {
                cancelState.cancelled = true;
                if (cancelState.attachmentId) {
                    void fn.invokeRpc("inferenceAttachmentDelete", {id: cancelState.attachmentId}, 30000).catch(doNothing);
                }
                delete uploadCancelRef.current[localId];
            }
            setAttachments(current => current.filter(item => item.localId !== localId));
        };

        const uploadFile = async (file: File) => {
            const kind = await detectPlaygroundAttachmentKind(file);
            const rejection = playgroundAttachmentRejection(kind, selectedCapabilities);
            if (rejection) {
                sendFailureNotification(rejection);
                return;
            }
            if (kind === "unsupported") return;

            if (kind === "text") {
                if (file.size > MAX_TEXT_ATTACHMENT_BYTES) {
                    sendFailureNotification("Text attachments must be 128 KiB or smaller.");
                    return;
                }
                const text = await file.text();
                const localId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
                setAttachments(current => [...current, {
                    localId,
                    kind,
                    attachmentId: null,
                    markdownAttachmentId: null,
                    fileName: file.name,
                    fileSize: file.size,
                    uploadedBytes: file.size,
                    status: "uploaded",
                    error: "",
                    text,
                }]);
                return;
            }

            const localId = `${Date.now()}-${Math.random().toString(16).slice(2)}`;
            const cancelState = {cancelled: false, attachmentId: null as string | null};
            uploadCancelRef.current[localId] = cancelState;
            setAttachments(current => [...current, {
                localId,
                kind,
                attachmentId: null,
                markdownAttachmentId: null,
                fileName: file.name,
                fileSize: file.size,
                uploadedBytes: 0,
                status: "uploading",
                error: "",
                text: "",
            }]);

            try {
                const created = await fn.invokeRpc("inferenceAttachmentCreate", {filename: file.name}, 30000) as Record<string, unknown>;
                const attachmentId = typeof created.id === "string" ? created.id : "";
                if (attachmentId === "") throw new Error("Attachment upload failed: missing id");
                cancelState.attachmentId = attachmentId;
                setAttachments(current => current.map(item => item.localId === localId ? {...item, attachmentId} : item));

                if (cancelState.cancelled) {
                    await fn.invokeRpc("inferenceAttachmentDelete", {id: attachmentId}, 30000);
                    return;
                }

                const reader = new ChunkedFileReader(file);
                while (!reader.isEof()) {
                    if (cancelState.cancelled) {
                        await fn.invokeRpc("inferenceAttachmentDelete", {id: attachmentId}, 30000);
                        return;
                    }
                    const chunk = new Uint8Array(await reader.readChunk(512 * 1024));
                    await fn.invokeRpc("inferenceAttachmentAppend", {id: attachmentId, data: chunk}, 120000);
                    setAttachments(current => current.map(item => item.localId === localId ? {...item, uploadedBytes: reader.offset} : item));
                }

                if (cancelState.cancelled) {
                    await fn.invokeRpc("inferenceAttachmentDelete", {id: attachmentId}, 30000);
                    return;
                }
                if (kind === "convertible") {
                    setAttachments(current => current.map(item => item.localId === localId ? {...item, status: "converting", uploadedBytes: file.size} : item));
                    const converted = await fn.invokeRpc("inferenceAttachmentConvertToMarkdown", {id: attachmentId}, 150000) as Record<string, unknown>;
                    const markdownAttachmentId = typeof converted.id === "string" ? converted.id : "";
                    if (markdownAttachmentId === "") throw new Error("Attachment conversion failed: missing markdown id");
                    if (cancelState.cancelled) {
                        await fn.invokeRpc("inferenceAttachmentDelete", {id: attachmentId}, 30000);
                        return;
                    }
                    const markdownUrl = playgroundAttachmentUrl(providerDomain, markdownAttachmentId);
                    const markdownResp = await fetch(markdownUrl);
                    if (!markdownResp.ok) throw new Error("Attachment conversion failed: could not download markdown");
                    const text = await markdownResp.text();
                    setAttachments(current => current.map(item => item.localId === localId ? {...item, markdownAttachmentId, status: "uploaded", uploadedBytes: file.size, text} : item));
                    return;
                }
                setAttachments(current => current.map(item => item.localId === localId ? {...item, status: "uploaded", uploadedBytes: file.size} : item));
            } catch (err) {
                const message = err instanceof Error ? err.message : String(err);
                setAttachments(current => current.map(item => item.localId === localId ? {...item, status: "error", error: message} : item));
                sendFailureNotification(message);
            }
        };

        const onFilesSelected = (ev: React.ChangeEvent<HTMLInputElement>) => {
            const files = Array.from(ev.currentTarget.files ?? []);
            ev.currentTarget.value = "";
            for (const file of files) void uploadFile(file);
        };

        return (
            <Box
                className={ComposerActionButtonHoverClass}
                style={{
                    width: "100%",
                    flexShrink: 0,
                    display: "flex",
                    flexDirection: "column",
                    minHeight: 104,
                    border: "1px solid var(--playground-border, var(--borderColor))",
                    borderRadius: 16,
                    background: "var(--playground-surface, var(--backgroundDefault))",
                    overflow: "hidden",
                }}
            >
                <input ref={fileInputRef} type="file" multiple style={{display: "none"}} onChange={onFilesSelected}/>
                <TextArea
                    resize={"none"}
                    rows={rows}
                    placeholder={placeholder}
                    value={value}
                    onChange={(ev) => {
                        const next = ev.currentTarget.value;
                        setValue(next);
                    }}
                    onKeyDown={(ev) => {
                        if ((ev.ctrlKey || ev.metaKey) && ev.key === "Enter") {
                            ev.preventDefault();
                            ev.stopPropagation();
                            send();
                        }
                    }}
                    style={{
                        resize: "none",
                        border: 0,
                        boxShadow: "none",
                        background: "transparent",
                        width: "100%",
                        minHeight: 0,
                        maxHeight: "30vh",
                        overflowY: "auto",
                        padding: "14px 16px 8px 16px",
                    }}
                />
                {attachments.length > 0 && (
                    <div style={{display: "flex", flexWrap: "wrap", gap: 8, padding: "0 10px 8px 10px"}}>
                        {attachments.map(attachment => (
                            <AttachmentUploadCard key={attachment.localId} attachment={attachment} onRemove={() => removeAttachment(attachment.localId)}/>
                        ))}
                    </div>
                )}
                <div
                    style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 6,
                        flexShrink: 0,
                        padding: "0 10px 10px 10px",
                    }}
                >
                    <Tooltip tooltipContentWidth={160} trigger={
                        <span style={{display: "inline-flex"}}>
                            <button type="button" disabled={disabled} onClick={() => fileInputRef.current?.click()} className={ComposerActionButtonClass}>
                                <Icon name="heroPlus" size={18}/>
                            </button>
                        </span>
                    }>
                        Attach file
                    </Tooltip>
                    <RichSelect<PlaygroundOption, keyof PlaygroundOption>
                        items={modelOptions}
                        keys={["key", "value"]}
                        selected={selectedModelOption}
                        onSelect={(option) => fn.sendModelInput("chat.modelId", {kind: ValueKind.String, string: option.key}, "chat.modelId")}
                        dropdownWidth="340px"
                        dropdownVerticalGap={8}
                        elementHeight={42}
                        matchTriggerWidth={false}
                        showSearchField={modelOptions.length > 8}
                        trigger={
                            <ModelSelectorTrigger option={selectedModelOption} modelName={selectedModel}/>
                        }
                        RenderRow={(props) => (
                            <ModelSelectorOption
                                option={props.element}
                                selected={props.element?.key === selectedModel}
                                onSelect={props.onSelect}
                                dataProps={props.dataProps}
                            />
                        )}
                    />
                    <div style={{flex: 1}}/>
                    <Tooltip tooltipContentWidth={80} trigger={
                        <span style={{display: "inline-flex"}}>
                            <button type="button" disabled={!canSend} onClick={send}
                                    className={ComposerActionButtonClass}>
                                <Icon name={sendIcon as any} size={18}/>
                            </button>
                        </span>
                    }>
                        Send
                    </Tooltip>
                </div>
            </Box>
        );
    },

    inference_chat_message: ({model, scope, fn}: UcxRenderContext) => {
        return <ChatMessageNode model={model} scope={scope} fn={fn}/>;
    },

    inference_chat_box: ({
                             node,
                             model,
                             scope,
                             fn,
                             renderChildren,
                         }: UcxRenderContext) => {
        const containerRef = React.useRef<HTMLDivElement | null>(null);
        const pinnedToBottomRef = React.useRef(true);
        const currentThreadId = stringValue(fn.modelValue(model, "currentThreadId"));
        const previousThreadIdRef = React.useRef<string | null>(null);

        React.useLayoutEffect(() => {
            const el = containerRef.current;
            if (!el) return;
            if (previousThreadIdRef.current !== currentThreadId) {
                previousThreadIdRef.current = currentThreadId;
                pinnedToBottomRef.current = true;
            }
            if (pinnedToBottomRef.current) {
                el.scrollTop = el.scrollHeight;
            }
        }, [currentThreadId, model, scope]);

        return (
            <div
                ref={containerRef}
                onScroll={(ev) => {
                    const el = ev.currentTarget;
                    pinnedToBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight <= 8;
                }}
                style={{overflowY: "auto", ...fn.sxStyle(node)}}
            >
                {renderChildren()}
            </div>
        );
    },

    inference_thread_list: ({node, model, fn}: UcxRenderContext) => {
        return <ThreadListNode node={node} model={model} fn={fn}/>;
    },
};

type ThreadListItem = { id: string; title: string };

type ChatMessagePart = {
    kind: "text" | "thinking" | "image" | "video" | "audio" | "attachment";
    text: string;
    summary: string;
    body: string;
    open: boolean;
    fileName: string;
    url: string;
};

type ChatMessageListItem = {
    role: string;
    content: string;
    parts: ChatMessagePart[];
};

type PlaygroundFrameProps = {
    model: Record<string, Value>;
    fn?: UcxFunctionRegistry;
    ucxContent?: React.ReactNode;
    connected: boolean;
    mounted: boolean;
    loadingSession?: boolean;
    error?: string;
    connectionStatus?: string;
};

const PlaygroundWorkspaceClass = injectStyle("inference-playground-workspace", k => `
    ${k} .playground-body {
        display: flex;
        flex-direction: row;
        gap: 32px;
        align-self: stretch;
        width: 100%;
        min-height: 0;
        height: calc(100vh - 115px);
    }

    ${k} .playground-main {
        flex: 1;
        min-width: 0;
        min-height: 0;
        overflow: hidden;
        display: flex;
        flex-direction: column;
        gap: 16px;
        padding: 16px;
        border-radius: 18px;
        border: 1px solid var(--borderColor);
        background: var(--playground-panel, transparent);
    }

    ${k} .playground-sidebar {
        width: 320px;
        flex-shrink: 0;
        min-height: 0;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 16px;
        border-radius: 16px;
        padding: 16px;
        border: 1px solid var(--borderColor);
        background: var(--playground-panel, transparent);
    }

    ${k} .playground-body {
        margin-top: 16px;
    }

    @media (max-width: 900px) {
        ${k} .playground-body {
            height: auto;
            min-height: calc(100vh - 174px);
            flex-direction: column;
        }

        ${k} .playground-main {
            min-height: 62vh;
        }

        ${k} .playground-sidebar {
            width: 100%;
        }
    }
`);

function AttachmentUploadCard({attachment, onRemove}: {attachment: PlaygroundUploadAttachment; onRemove?: () => void}): React.ReactNode {
    const progress = attachment.fileSize <= 0 ? 100 : Math.min(100, Math.round((attachment.uploadedBytes / attachment.fileSize) * 100));
    const uploading = attachment.status === "uploading";
    return <div style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        maxWidth: 280,
        minWidth: 180,
        padding: "8px 10px",
        border: "1px solid var(--playground-border, var(--borderColor))",
        borderRadius: 10,
        background: "var(--playground-surface-raised, var(--dialogToolbar))",
    }}>
        <Icon name="heroDocument" size={18}/>
        <div style={{minWidth: 0, flex: 1}}>
            <div style={{fontSize: 13, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis"}} title={attachment.fileName}>
                {attachment.fileName}
            </div>
            <div style={{fontSize: 11, color: "var(--textSecondary)"}}>
                {attachment.status === "error" ? attachment.error : attachment.status === "converting" ? "Extracting text" : uploading ? `${progress}% uploaded` : attachment.uploadedBytes === 0 ? null : `${sizeToString(attachment.uploadedBytes)}`}
            </div>
            {uploading && <div style={{height: 3, marginTop: 4, borderRadius: 999, background: "var(--borderColor)", overflow: "hidden"}}>
                <div style={{height: "100%", width: `${progress}%`, background: "var(--primaryMain)", transition: "width 120ms ease"}}/>
            </div>}
        </div>
        {onRemove ? <IconButton tooltip={"Remove attachment"} onClick={onRemove} icon={"heroTrash"} /> : null}
    </div>;
}

function MessageAttachmentCard({part}: {part: ChatMessagePart}): React.ReactNode {
    const kind = part.kind === "image" || part.kind === "video" || part.kind === "audio" ? part.kind : "text";
    return <AttachmentUploadCard attachment={{
        localId: `${part.kind}-${part.url}-${part.fileName}`,
        kind,
        attachmentId: null,
        markdownAttachmentId: null,
        fileName: part.fileName || "Unknown",
        fileSize: 0,
        uploadedBytes: 0,
        status: "uploaded",
        error: "",
        text: part.text,
    }}/>;
}

type ChatMessageNodeProps = Pick<UcxRenderContext, "model" | "scope" | "fn">;

function ModelSelectorTrigger({option, modelName}: {option?: PlaygroundOption; modelName: string}): React.ReactNode {
    const label = option?.value ?? (modelName || "Select model");
    const logoModelName = option?.key ?? modelName;

    return (
        <button
            type="button"
            style={{
                width: 300,
                maxWidth: "34vw",
                height: 34,
                border: 0,
                borderRadius: 999,
                background: "transparent",
                color: "inherit",
                display: "inline-flex",
                alignItems: "center",
                gap: 8,
                padding: "0 10px",
                cursor: "pointer",
                textAlign: "left",
            }}
        >
            <ModelInferenceLogo modelName={logoModelName}/>
            <span
                style={{
                    minWidth: 0,
                    flex: 1,
                    overflow: "hidden",
                    whiteSpace: "nowrap",
                    textOverflow: "ellipsis",
                    fontWeight: 600,
                }}
            >
                {label}
            </span>
            <Icon name="heroChevronDown" size={14}/>
        </button>
    );
}

function ModelSelectorOption({
    option,
    selected,
    onSelect,
    dataProps,
}: {
    option?: PlaygroundOption;
    selected: boolean;
    onSelect: () => void;
    dataProps?: Record<string, string>;
}): React.ReactNode {
    if (!option) return null;

    return (
        <div
            {...dataProps}
            data-active={selected.toString()}
            onClick={onSelect}
            style={{
                minHeight: 42,
                display: "flex",
                alignItems: "center",
                gap: 10,
                padding: "7px 10px",
                color: "inherit",
                background: selected ? "var(--playground-hover, var(--rowHover))" : undefined,
            }}
        >
            <ModelInferenceLogo modelName={option.key}/>
            <span
                style={{
                    minWidth: 0,
                    flex: 1,
                    overflow: "hidden",
                    whiteSpace: "nowrap",
                    textOverflow: "ellipsis",
                    fontWeight: 500,
                }}
            >
                {option.value}
            </span>
            {selected ? <Icon name="heroCheck" size={16} color="primaryMain"/> : <span style={{width: 16}}/>}
        </div>
    );
}

function ChatMessageNode(
    {
        model,
        scope,
        fn,
    }: ChatMessageNodeProps
): React.ReactNode {
    const role = stringValue(fn.modelValue(model, "./role", scope));
    const content = stringValue(fn.modelValue(model, "./content", scope));
    const parts = chatMessagePartsValue(fn.modelValue(model, "./parts", scope));
    const generatedAt = numberValue(fn.modelValue(model, "./generatedAt", scope));
    const modelName = stringValue(fn.modelValue(model, "./modelName", scope));
    const startedAt = numberValue(fn.modelValue(model, "./startedAt", scope));
    const firstTokenAt = numberValue(fn.modelValue(model, "./firstTokenAt", scope));
    const finishedAt = numberValue(fn.modelValue(model, "./finishedAt", scope));
    const outputTokens = numberValue(fn.modelValue(model, "./outputTokens", scope));
    const messageIndex = numberValue(fn.modelValue(model, "./messageIndex", scope));
    const responseFinished = finishedAt > 0;
    const modelOptions = textGenerationModelOptions(fn.modelValue(model, "models"));
    const selectedModelOption = modelOptions.find(option => option.key === modelName) ?? modelOptions.find(option => option.key === stringValue(fn.modelValue(model, "chat.modelId")));
    const regenerateModelLabel = selectedModelOption?.value ?? modelName;
    const allMessages = chatMessagesValue(fn.modelValue(model, "chat.messages"));
    const messageParts = parts.length === 0
        ? [
            {
                kind: "text",
                text: content,
                summary: "",
                body: "",
                open: false,
            } as ChatMessagePart,
        ]
        : parts;

    if (shouldHideCollapsedAttachmentMessage(allMessages, messageIndex, role, content, messageParts)) return null;

    if (role === "user") {
        const displayParts = orderUserMessageParts([
            ...messageParts,
            ...collapsedAttachmentPartsForMessage(allMessages, messageIndex),
        ]);
        return (
            <Flex width="100%" justifyContent="flex-end" my={16} flexDirection="column" alignItems="flex-end" gap="6px">
                <div style={{maxWidth: "78%", borderRadius: 16, padding: "10px 14px", background: "var(--playground-user-bg, var(--secondaryMain))", color: "var(--playground-user-text, var(--textPrimary))", overflowWrap: "anywhere", display: "flex", flexDirection: "column", gap: 8}}>
                    {displayParts.map((part, idx) => {
                        if (part.kind === "image" && part.url !== "") {
                            return <img key={idx} src={part.url} alt={part.fileName || "Attachment"} style={{maxWidth: 360, maxHeight: 260, objectFit: "contain", borderRadius: 8}}/>;
                        }
                        if (part.kind === "video" || part.kind === "audio" || part.kind === "attachment") {
                            return <MessageAttachmentCard key={idx} part={part}/>;
                        }
                        return <span key={idx} style={{whiteSpace: "pre-wrap"}}>{part.text}</span>;
                    })}
                </div>
                <Flex className={ComposerActionButtonHoverClass} alignItems="center" gap="8px" color="textSecondary" fontSize="12px">
                    <span>{formatTimeOfDay(generatedAt)}</span>
                    <CopyButton onClick={() => copyToClipboard(content)}/>
                </Flex>
            </Flex>
        );
    }

    return (
        <Flex flexDirection="column" gap="4px" width="100%" my={16}>
            {messageParts.map((part, idx) => {
                if (part.kind === "thinking") {
                    return <ThinkingPart key={idx} part={part}/>;
                }
                return <StreamingMarkdownPart key={idx} text={part.text} streaming={!responseFinished}/>;
            })}
            {!responseFinished ? null : <Flex className={ComposerActionButtonHoverClass} alignItems="center" gap="8px" color="textSecondary" fontSize="12px" flexWrap="wrap">
                <CopyButton onClick={() => copyToClipboard(content)}/>
                <RichSelect<PlaygroundOption, keyof PlaygroundOption>
                    items={modelOptions}
                    keys={["key", "value"]}
                    selected={selectedModelOption}
                    onSelect={(option) => fn.sendUiEvent("regenerateChat", "click", {
                        kind: ValueKind.Object,
                        object: {
                            modelId: {kind: ValueKind.String, string: option.key},
                            messageIndex: {kind: ValueKind.S64, s64: messageIndex},
                        },
                    })}
                    dropdownWidth="340px"
                    dropdownVerticalGap={8}
                    elementHeight={42}
                    matchTriggerWidth={false}
                    showSearchField={modelOptions.length > 8}
                    trigger={<IconButton tooltip={`Regenerate (used: ${regenerateModelLabel})`} icon="heroArrowPath" onClick={doNothing}/>}
                    RenderRow={(props) => (
                        <ModelSelectorOption
                            option={props.element}
                            selected={props.element?.key === selectedModelOption?.key}
                            onSelect={props.onSelect}
                            dataProps={props.dataProps}
                        />
                    )}
                />
                <Tooltip tooltipContentWidth={240} trigger={<span>{formatResponseDuration(startedAt, finishedAt)}</span>}>
                    <div style={{display: "flex", flexDirection: "column", gap: 4}}>
                        <span>Time to first token: {formatDuration(firstTokenAt > 0 && startedAt > 0 ? firstTokenAt - startedAt : 0)}</span>
                        <span>Output tokens: {outputTokens || "Unknown"}</span>
                        <span>Tokens per second: {formatTokensPerSecond(outputTokens, firstTokenAt, finishedAt)}</span>
                        <span>Finished: {formatTimeOfDay(finishedAt)}</span>
                    </div>
                </Tooltip>
            </Flex>}
        </Flex>
    );
}

function formatTimeOfDay(ms: number): string {
    if (ms <= 0) return "Unknown time";
    const date = new Date(ms);
    return isToday(date) ? format(date, "HH:mm") : format(date, "yyyy-MM-dd HH:mm");
}

function formatResponseDuration(startedAt: number, finishedAt: number): string {
    if (startedAt <= 0 || finishedAt <= 0) return "Response time unknown";
    return formatDuration(finishedAt - startedAt);
}

function formatDuration(ms: number): string {
    if (ms <= 0) return "Unknown";
    if (ms < 1000) return `${Math.round(ms)} ms`;
    return `${(ms / 1000).toFixed(ms < 10000 ? 1 : 0)} s`;
}

function formatTokensPerSecond(outputTokens: number, firstTokenAt: number, finishedAt: number): string {
    if (outputTokens <= 0 || firstTokenAt <= 0 || finishedAt <= firstTokenAt) return "Unknown";
    return `${(outputTokens / ((finishedAt - firstTokenAt) / 1000)).toFixed(1)}/s`;
}

function StreamingMarkdownPart({text, streaming}: {text: string; streaming: boolean}): React.ReactNode {
    if (!streaming) return <MarkdownDocument text={text}/>;

    const stableText = stableStreamingMarkdownPrefix(text);
    return <MarkdownDocument text={stableText}/>;
}

type StreamingMarkdownStackItem = {
    kind: "fence" | "inlineCode" | "emphasis" | "linkText" | "linkUrl" | "heading";
    marker?: string;
};

function stableStreamingMarkdownPrefix(text: string): string {
    const stack: StreamingMarkdownStackItem[] = [];
    let stableIndex = 0;
    let lineStart = 0;

    const top = () => stack[stack.length - 1];
    const updateStable = (idx: number) => {
        if (stack.length === 0) stableIndex = idx;
    };

    for (let i = 0; i < text.length; i++) {
        const current = top();

        if (current?.kind === "fence") {
            if (i === lineStart && isFenceAt(text, i)) {
                i = consumeLine(text, i);
                stack.pop();
                updateStable(i);
                lineStart = i;
                i--;
                continue;
            }
            if (text[i] === "\n") lineStart = i + 1;
            continue;
        }

        if (current?.kind === "heading") {
            if (text[i] === "\n") {
                stack.pop();
                lineStart = i + 1;
                updateStable(i + 1);
            }
            continue;
        }

        if (current?.kind === "inlineCode") {
            if (text.startsWith(current.marker ?? "`", i)) {
                i += (current.marker?.length ?? 1) - 1;
                stack.pop();
                updateStable(i + 1);
            }
            if (text[i] === "\n") lineStart = i + 1;
            continue;
        }

        if (current?.kind === "emphasis") {
            if (current.marker && text.startsWith(current.marker, i) && !isEscaped(text, i)) {
                i += current.marker.length - 1;
                stack.pop();
                updateStable(i + 1);
            }
            if (text[i] === "\n") lineStart = i + 1;
            continue;
        }

        if (current?.kind === "linkText") {
            if (text[i] === "]" && !isEscaped(text, i)) {
                stack.pop();
                if (text[i + 1] === "(") {
                    stack.push({kind: "linkUrl"});
                    i++;
                } else {
                    updateStable(i + 1);
                }
            }
            if (text[i] === "\n") lineStart = i + 1;
            continue;
        }

        if (current?.kind === "linkUrl") {
            if (text[i] === ")" && !isEscaped(text, i)) {
                stack.pop();
                updateStable(i + 1);
            }
            if (text[i] === "\n") lineStart = i + 1;
            continue;
        }

        if (i === lineStart && isFenceAt(text, i)) {
            stack.push({kind: "fence", marker: markdownFenceMarkerAt(text, i)});
            i = consumeLine(text, i);
            lineStart = i;
            i--;
            continue;
        }

        if (i === lineStart && isAtxHeadingAt(text, i)) {
            stack.push({kind: "heading"});
            continue;
        }

        if (text[i] === "`" && !isEscaped(text, i)) {
            const marker = text.startsWith("``", i) ? "``" : "`";
            stack.push({kind: "inlineCode", marker});
            i += marker.length - 1;
            continue;
        }

        const emphasisMarker = markdownEmphasisMarkerAt(text, i);
        if (emphasisMarker) {
            stack.push({kind: "emphasis", marker: emphasisMarker});
            i += emphasisMarker.length - 1;
            continue;
        }

        if (text[i] === "[" && !isEscaped(text, i)) {
            if (text[i - 1] === "!") stableIndex = Math.max(0, i - 1);
            stack.push({kind: "linkText"});
            continue;
        }

        if (text[i] === "\n") lineStart = i + 1;
        updateStable(i + 1);
    }

    const current = top();
    if (current?.kind === "fence") {
        return text.trimEnd();
    }

    return text.slice(0, stableIndex).trimEnd();
}

function isFenceAt(text: string, idx: number): boolean {
    return /^\s*(```|~~~)/.test(text.slice(idx, Math.min(text.length, idx + 16)));
}

function markdownFenceMarkerAt(text: string, idx: number): string {
    const match = text.slice(idx, Math.min(text.length, idx + 16)).match(/^\s*(```|~~~)/);
    return match?.[1] ?? "```";
}

function isAtxHeadingAt(text: string, idx: number): boolean {
    return /^ {0,3}#{1,6}(\s|$)/.test(text.slice(idx, Math.min(text.length, idx + 10)));
}

function consumeLine(text: string, idx: number): number {
    const newline = text.indexOf("\n", idx);
    return newline === -1 ? text.length : newline + 1;
}

function markdownEmphasisMarkerAt(text: string, idx: number): string {
    if (isEscaped(text, idx)) return "";
    const ch = text[idx];
    if (ch !== "*" && ch !== "_") return "";
    if (isListMarkerAt(text, idx)) return "";
    const previous = text[idx - 1] ?? "";
    const next = text[idx + 1];
    if (next === undefined || /\s/.test(next)) return "";
    if (ch === "_" && /[\p{L}\p{N}]/u.test(previous)) return "";
    if (ch === "*" && /\s/.test(previous) && /\s/.test(text[idx + 2] ?? "")) return "";
    if (next === ch && text[idx + 2] === ch) return ch.repeat(3);
    if (next === ch) return ch.repeat(2);
    return ch;
}

function isListMarkerAt(text: string, idx: number): boolean {
    const before = text.slice(Math.max(0, idx - 4), idx);
    const atLineStart = idx === 0 || before.endsWith("\n") || /^\n? {0,3}$/.test(before);
    return atLineStart && (text[idx] === "*" || text[idx] === "_") && /\s/.test(text[idx + 1] ?? "");
}

function isEscaped(text: string, idx: number): boolean {
    let slashCount = 0;
    for (let i = idx - 1; i >= 0 && text[i] === "\\"; i--) slashCount++;
    return slashCount % 2 === 1;
}

function ThinkingPart({part}: { part: ChatMessagePart }): React.ReactNode {
    const [expanded, setExpanded] = React.useState(false);
    const summary = part.summary.trim();

    React.useEffect(() => {
        setExpanded(part.open);
    }, [part.open]);

    return (
        <div
            style={{
                border: "1px solid var(--playground-border, var(--borderColor))",
                borderRadius: 8,
                overflow: "hidden",
                background: "var(--playground-surface-raised, var(--dialogToolbar))",
                marginBottom: "16px"
            }}
        >
            <button
                type="button"
                onClick={() => setExpanded((v) => !v)}
                style={{
                    width: "100%",
                    display: "flex",
                    alignItems: "center",
                    gap: 8,
                    padding: "8px 10px",
                    border: 0,
                    background: "transparent",
                    color: "inherit",
                    cursor: "pointer",
                    textAlign: "left",
                }}
            >
                <Icon name="heroSparkles" size={16}/>
                <span style={{fontWeight: 600, flexShrink: 0}}>Thinking</span>
                {summary === "" ? null : (
                    <span
                        style={{
                            whiteSpace: "nowrap",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                        }}
                    >
            {summary}
          </span>
                )}
            </button>
            {expanded ? (
                <div
                    style={{
                        padding: "0 10px 10px 10px",
                        color: "var(--textSecondary)",
                        whiteSpace: "normal",
                    }}
                >
                    {part.body.trim() === "" ? (
                        <UcxSpinner />
                    ) : (
                        <MarkdownDocument text={part.body}/>
                    )}
                </div>
            ) : null}
        </div>
    );
}

function ThreadListNode({
                            node,
                            model,
                            fn,
}: Pick<UcxRenderContext, "node" | "model" | "fn">): React.ReactNode {
    const [operations, setOperations] = React.useState<
        Operation<ThreadListItem>[]
    >([]);
    const openOperationsRef =
        React.useRef<(left: number, top: number) => void>(doNothing);
    const threads = threadListValue(fn.modelValue(model, node.bindPath));
    const currentThreadId = stringValue(fn.modelValue(model, "currentThreadId"));

    const threadOperations = React.useCallback(
        (thread: ThreadListItem): Operation<ThreadListItem>[] => [
            {
                text: "Rename",
                icon: "heroPencil",
                shortcut: ShortcutKey.R,
                enabled: () => true,
                onClick: async () => {
                    try {
                        const result = await addStandardInputDialog({
                            title: "Rename thread",
                            placeholder: thread.title,
                            confirmText: "Rename",
                            validator: (value) => value.trim() !== "",
                            validationFailureMessage: "Thread title cannot be empty",
                        });
                        fn.sendUiEvent("renameThreadFromMenu", "click", {
                            kind: ValueKind.Object,
                            object: {
                                id: {kind: ValueKind.String, string: thread.id},
                                title: {kind: ValueKind.String, string: result.result},
                            },
                        });
                    } catch {
                        // Dialog cancelled.
                    }
                },
            },
            {
                text: "Delete",
                icon: "heroTrash",
                color: "errorMain",
                confirm: true,
                shortcut: ShortcutKey.Backspace,
                enabled: () => true,
                onClick: () =>
                    fn.sendUiEvent("deleteThread", "click", {
                        kind: ValueKind.String,
                        string: thread.id,
                    }),
            },
        ],
        [fn]
    );

    if (threads.length === 0) {
        return <Text color="textSecondary">No threads yet.</Text>;
    }

    return (
        <Box
            className={ThreadListClass}
            style={{
                position: "relative",
                display: "flex",
                flexDirection: "column",
                gap: 6,
            }}
        >
            {threads.map((thread) => {
                const active = thread.id === currentThreadId;
                const openMenu = (left: number, top: number) => {
                    setOperations(threadOperations(thread));
                    openOperationsRef.current(left, top);
                };

                return (
                    <div
                        key={thread.id}
                        className="inference-thread-row"
                        data-active={active}
                        onContextMenu={(ev) => {
                            ev.preventDefault();
                            ev.stopPropagation();
                            openMenu(ev.clientX, ev.clientY);
                        }}
                    >
                        <button
                            type="button"
                            onClick={() =>
                                fn.sendUiEvent("openThread", "click", {
                                    kind: ValueKind.String,
                                    string: thread.id,
                                })
                            }
                            style={{
                                flex: 1,
                                minWidth: 0,
                                border: 0,
                                padding: "8px 10px",
                                textAlign: "left",
                                cursor: "pointer",
                                background: "transparent",
                                color: "inherit",
                                whiteSpace: "nowrap",
                                overflow: "hidden",
                                textOverflow: "ellipsis",
                            }}
                        >
                            {thread.title}
                        </button>
                        {active ? (
                            <button
                                type="button"
                                aria-label="Thread operations"
                                onClick={(ev) => {
                                    ev.preventDefault();
                                    ev.stopPropagation();
                                    const rect = ev.currentTarget.getBoundingClientRect();
                                    openMenu(rect.left, rect.bottom);
                                }}
                                style={{
                                    width: 28,
                                    height: 28,
                                    border: 0,
                                    borderRadius: 999,
                                    background: "transparent",
                                    color: "inherit",
                                    cursor: "pointer",
                                    display: "inline-flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    marginRight: 4,
                                }}
                            >
                                <Icon name={"ellipsis"} size={12}/>
                            </button>
                        ) : null}
                    </div>
                );
            })}
            <Operations
                entityNameSingular="thread"
                operations={operations}
                forceEvaluationOnOpen={true}
                openFnRef={openOperationsRef}
                selected={[]}
                extra={undefined}
                row={threads[0] ?? {id: "", title: ""}}
                hidden
                location="IN_ROW"
            />
        </Box>
    );
}

function PlaygroundFrame({model, fn, ucxContent, connected, mounted, loadingSession = false, error = ""}: PlaygroundFrameProps): React.ReactNode {
    const connectionStatus = loadingSession || !mounted ? "Connecting..." : !connected ? "Reconnecting..." : error !== "" ? "Connection issue" : "Connected";

    return (
        <MainContainer
            main={
                <div className={`${PlaygroundThemeClass} ${PlaygroundWorkspaceClass}`} style={{display: "flex", flexDirection: "column", gap: 8, minHeight: 0}}>
                    {ucxContent ? <div style={{display: "none"}}>{ucxContent}</div> : null}
                    <TabbedCard
                        style={{flex: 1, minHeight: 0, overflow: "hidden"}}
                        rightControls={<>
                            <DeveloperModeToggle model={model} fn={fn} connected={connected && mounted}/>
                            <ProjectSwitcher/>
                        </>}
                    >
                        <TabbedCardTab name="Chat" icon="heroChatBubbleLeftRight">
                            <PlaygroundWorkspace model={model} fn={fn} connected={connected && mounted && error === ""} connectionStatus={connectionStatus}/>
                        </TabbedCardTab>
                    </TabbedCard>
                </div>
            }
        />
    );
}

function DeveloperModeToggle({model, fn, connected}: {model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean}): React.ReactNode {
    const developer = boolValue(fn?.modelValue(model, "developer") ?? model.developer);
    return <div style={{display: "flex", alignItems: "center", gap: 8, marginRight: 16}}>
        <span style={{fontWeight: 600, userSelect: "none"}}>Developer</span>
        <Toggle height={18} checked={developer} onChange={() => connected && fn?.sendModelInput("developer", {kind: ValueKind.Bool, bool: !developer}, "developerMode")}/>
    </div>;
}

function PlaygroundWorkspace({model, fn, connected, connectionStatus}: {model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean; connectionStatus: string}): React.ReactNode {
    const developer = boolValue(fn?.modelValue(model, "developer") ?? model.developer);

    return (
        <div className="playground-body">
            <div className="playground-main">
                <PlaygroundConversation model={model} fn={fn} connected={connected}/>
            </div>
            <div className="playground-sidebar">
                {developer ? <PlaygroundDeveloperSidebar model={model} fn={fn} connected={connected}/> : <PlaygroundThreadSidebar model={model} fn={fn} connected={connected}/>}
                <ConnectionStatusIndicator connected={connected} text={connectionStatus}/>
            </div>
        </div>
    );
}

function ConnectionStatusIndicator({connected, text}: {connected: boolean; text: string}): React.ReactNode {
    return <div style={{marginTop: "auto", display: "flex", alignItems: "center", gap: 8, color: "var(--textSecondary)", fontSize: 12}}>
        <span style={{width: 8, height: 8, borderRadius: 999, background: connected ? "var(--successMain)" : "var(--warningMain)"}}/>
        {text}
    </div>;
}

function PlaygroundConversation({model, fn, connected}: {model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean}): React.ReactNode {
    const messagesValue = fn?.modelValue(model, "chat.messages") ?? model["chat.messages"];
    const messageItems = messagesValue?.kind === ValueKind.List ? messagesValue.list : [];
    const loading = boolValue(fn?.modelValue(model, "chat.loading") ?? model["chat.loading"]);
    const currentThreadId = stringValue(fn?.modelValue(model, "currentThreadId") ?? model.currentThreadId);
    const containerRef = React.useRef<HTMLDivElement | null>(null);
    const pinnedToBottomRef = React.useRef(true);
    const previousThreadIdRef = React.useRef<string | null>(null);

    React.useLayoutEffect(() => {
        const el = containerRef.current;
        if (!el) return;
        if (previousThreadIdRef.current !== currentThreadId) {
            previousThreadIdRef.current = currentThreadId;
            pinnedToBottomRef.current = true;
        }
        if (pinnedToBottomRef.current) el.scrollTop = el.scrollHeight;
    }, [currentThreadId, messageItems.length, loading, model]);

    const composerNode = React.useMemo<UiNode>(() => ({
        id: "chatComposer",
        component: "inference_chat_composer",
        bindPath: "chat.prompt",
        optimistic: true,
        children: [],
        props: {
            placeholder: {kind: ValueKind.String, string: "Ask anything"},
            rows: {kind: ValueKind.S64, s64: 3},
            sendIcon: {kind: ValueKind.String, string: "heroArrowUp"},
            disabled: {kind: ValueKind.Bool, bool: !connected || loading},
        },
    }), [connected, loading]);

    return (
        <>
            <div
                ref={containerRef}
                onScroll={(ev) => {
                    const el = ev.currentTarget;
                    pinnedToBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight <= 8;
                }}
                style={{flex: 1, minHeight: 0, overflowY: "auto", padding: "16px 8px"}}
            >
                {messageItems.length === 0 ? <Text color="textSecondary">No messages yet.</Text> : messageItems.map((item, idx) => {
                    if (item.kind !== ValueKind.Object || !fn) return null;
                    return <ChatMessageNode key={idx} model={model} scope={item.object} fn={fn}/>;
                })}
                {loading ? <UcxSpinner /> : null}
            </div>
            {fn ? playgroundComponents.inference_chat_composer({
                node: composerNode,
                model,
                fn,
                components: playgroundComponents,
                renderChildren: () => [],
            }) : <DisabledComposerPlaceholder/>}
        </>
    );
}

function DisabledComposerPlaceholder(): React.ReactNode {
    return <Box className={ComposerActionButtonHoverClass} style={{width: "100%", flexShrink: 0, minHeight: 104, border: "1px solid var(--playground-border, var(--borderColor))", borderRadius: 16, background: "var(--playground-surface, var(--backgroundDefault))", overflow: "hidden"}}>
        <TextArea
            resize="none"
            rows={3}
            placeholder="Ask anything"
            value=""
            disabled
            onChange={doNothing}
            style={{resize: "none", border: 0, boxShadow: "none", background: "transparent", width: "100%", minHeight: 0, padding: "14px 16px 8px 16px"}}
        />
        <div style={{display: "flex", alignItems: "center", gap: 6, flexShrink: 0, padding: "0 10px 10px 10px"}}>
            <button type="button" disabled className={ComposerActionButtonClass}>
                <Icon name="heroPlus" size={18}/>
            </button>
            <div style={{flex: 1}}/>
            <button type="button" disabled className={ComposerActionButtonClass}>
                <Icon name="heroArrowUp" size={18}/>
            </button>
        </div>
    </Box>;
}

function PlaygroundThreadSidebar({model, fn, connected}: {model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean}): React.ReactNode {
    const node = React.useMemo<UiNode>(() => ({
        id: "threadList",
        component: "inference_thread_list",
        bindPath: "threads",
        optimistic: false,
        props: {},
        children: [],
    }), []);

    return <>
        <Button
            type="button"
            disabled={!connected || !fn}
            onClick={() => fn?.sendUiEvent("newThread", "click")}
            color={"secondaryMain"}
        >
            <Icon name="heroPlus" size={16} mr={8}/>
            New thread
        </Button>
        {fn ? <ThreadListNode node={node} model={model} fn={fn}/> : <Text color="textSecondary">Loading...</Text>}
    </>;
}

function PlaygroundDeveloperSidebar({model, fn, connected}: {model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean}): React.ReactNode {
    return <>
        <Section title="Settings" defaultOpen>
            <SettingToggle label="Streaming" path="chat.streaming" model={model} fn={fn} connected={connected}/>
            <SettingSlider label="Max completion tokens" path="chat.maxCompletionTokens" min={1} max={1024 * 256} step={1024} model={model} fn={fn} connected={connected} integer/>
            <SettingSlider label="Temperature" path="chat.temperature" min={0} max={2} step={0.1} model={model} fn={fn} connected={connected}/>
            <SettingSlider label="Top P" path="chat.topP" min={0} max={1} step={0.1} model={model} fn={fn} connected={connected}/>
            <SettingTextArea label="System prompt" path="chat.systemPrompt" model={model} fn={fn} connected={connected}/>
        </Section>
        <Section title="Usage" defaultOpen>
            <UsageRow label="Session input tokens" value={numberValue(fn?.modelValue(model, "chat.usage.session.input") ?? model["chat.usage.session.input"])}/>
            <UsageRow label="Session output tokens" value={numberValue(fn?.modelValue(model, "chat.usage.session.output") ?? model["chat.usage.session.output"])}/>
            <UsageRow label="Session tokens reported for usage" value={numberValue(fn?.modelValue(model, "chat.usage.session.reported") ?? model["chat.usage.session.reported"])}/>
            <UsageRow label="Latest input tokens" value={numberValue(fn?.modelValue(model, "chat.usage.lastQuery.input") ?? model["chat.usage.lastQuery.input"])}/>
            <UsageRow label="Latest output tokens" value={numberValue(fn?.modelValue(model, "chat.usage.lastQuery.output") ?? model["chat.usage.lastQuery.output"])}/>
            <UsageRow label="Latest tokens reported for usage" value={numberValue(fn?.modelValue(model, "chat.usage.lastQuery.reported") ?? model["chat.usage.lastQuery.reported"])}/>
        </Section>
        <Section title="Advanced settings">
            <SettingSlider label="Presence penalty" path="chat.presencePenalty" min={-2} max={2} step={0.1} model={model} fn={fn} connected={connected}/>
            <SettingSlider label="Frequency penalty" path="chat.frequencyPenalty" min={-2} max={2} step={0.1} model={model} fn={fn} connected={connected}/>
            <SettingToggle label="Logprobs" path="chat.logprobs" model={model} fn={fn} connected={connected}/>
            <SettingSlider label="Top log probs" path="chat.topLogprobs" min={0} max={20} step={1} model={model} fn={fn} connected={connected} integer/>
        </Section>
        <Section title="Curl">
            <pre style={{whiteSpace: "pre-wrap", overflowWrap: "anywhere", fontSize: 12}}>{stringValue(fn?.modelValue(model, "chat.curl") ?? model["chat.curl"])}</pre>
        </Section>
    </>;
}

function Section({title, defaultOpen = false, children}: React.PropsWithChildren<{title: string; defaultOpen?: boolean}>): React.ReactNode {
    const [open, setOpen] = React.useState(defaultOpen);
    return <div style={{border: "1px solid var(--playground-border, var(--borderColor))", borderRadius: 10, overflow: "hidden"}}>
        <button type="button" onClick={() => setOpen(v => !v)} style={{width: "100%", border: 0, background: "transparent", color: "inherit", padding: "10px 12px", display: "flex", alignItems: "center", justifyContent: "space-between", cursor: "pointer", fontWeight: 600}}>
            {title}
            <Icon name={open ? "heroChevronUp" : "heroChevronDown"} size={16}/>
        </button>
        {open ? <div style={{display: "flex", flexDirection: "column", gap: 10, padding: "0 12px 12px 12px"}}>{children}</div> : null}
    </div>;
}

function SettingToggle({label, path, model, fn, connected}: {label: string; path: string; model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean}): React.ReactNode {
    const checked = boolValue(fn?.modelValue(model, path) ?? model[path]);
    return <div style={{display: "flex", alignItems: "center", justifyContent: "space-between", gap: 8}}>
        <span style={{fontWeight: 600}}>{label}</span>
        <Toggle height={18} checked={checked} onChange={() => connected && fn?.sendModelInput(path, {kind: ValueKind.Bool, bool: !checked}, path)}/>
    </div>;
}

function SettingSlider({label, path, min, max, step, model, fn, connected, integer = false}: {label: string; path: string; min: number; max: number; step: number; model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean; integer?: boolean}): React.ReactNode {
    const modelNumber = numberValue(fn?.modelValue(model, path) ?? model[path]);
    const [value, setValue] = React.useState(modelNumber);
    React.useEffect(() => setValue(modelNumber), [modelNumber]);
    const commit = (next: number) => fn?.sendModelInput(path, integer ? {kind: ValueKind.S64, s64: Math.round(next)} : {kind: ValueKind.F64, f64: next}, path);
    return <label style={{display: "flex", flexDirection: "column", gap: 4}}>
        <span style={{display: "flex", justifyContent: "space-between", gap: 8}}><span>{label}</span><span>{integer ? Math.round(value) : value.toFixed(1)}</span></span>
        <input disabled={!connected || !fn} type="range" min={min} max={max} step={step} value={value || min} onChange={ev => { const next = Number(ev.currentTarget.value); setValue(next); commit(next); }}/>
    </label>;
}

function SettingTextArea({label, path, model, fn, connected}: {label: string; path: string; model: Record<string, Value>; fn?: UcxFunctionRegistry; connected: boolean}): React.ReactNode {
    const modelText = stringValue(fn?.modelValue(model, path) ?? model[path]);
    const [value, setValue] = React.useState(modelText);
    React.useEffect(() => setValue(modelText), [modelText]);
    return <label style={{display: "flex", flexDirection: "column", gap: 4}}>
        <span>{label}</span>
        <TextArea rows={4} value={value} disabled={!connected || !fn} onChange={ev => { const next = ev.currentTarget.value; setValue(next); fn?.sendModelInput(path, {kind: ValueKind.String, string: next}, path); }}/>
    </label>;
}

function UsageRow({label, value}: {label: string; value: number}): React.ReactNode {
    return <div style={{display: "flex", justifyContent: "space-between", gap: 8}}><span>{label}</span><span style={{textAlign: "right"}}>{value}</span></div>;
}

export default function Playground(): React.ReactNode {
    const [session, setSession] = React.useState<PlaygroundSession | null>(null);
    const [loading, setLoading] = React.useState(true);
    const [terminalError, setTerminalError] = React.useState("");
    const [refreshNonce, setRefreshNonce] = React.useState(0);
    const [lastModel, setLastModel] = React.useState<Record<string, Value>>({});
    const [composerDraft, setComposerDraft] = React.useState("");
    const openRetryCountRef = React.useRef(0);
    const openRetryTimerRef = React.useRef<number | null>(null);
    const mountedRef = React.useRef(true);
    const projectId = useProjectId();
    const previousProjectIdRef = React.useRef(projectId);

    usePage("Inference playground", SidebarTabId.INFERENCE);

    React.useEffect(() => {
        return () => {
            mountedRef.current = false;
        };
    }, []);

    React.useEffect(() => {
        if (previousProjectIdRef.current === projectId) return;
        previousProjectIdRef.current = projectId;
        openRetryCountRef.current = 0;
        setLastModel({});
        setComposerDraft("");
        setSession(null);
        setRefreshNonce((x) => x + 1);
    }, [projectId]);

    React.useEffect(() => {
        let cancelled = false;
        if (openRetryTimerRef.current !== null) {
            window.clearTimeout(openRetryTimerRef.current);
            openRetryTimerRef.current = null;
        }

        setLoading(true);
        void callAPI(openPlayground({providerId: null}))
            .then((result) => {
                if (cancelled) return;
                openRetryCountRef.current = 0;
                setSession(result);
                setLoading(false);
                setTerminalError("");
            })
            .catch((err) => {
                if (cancelled) return;
                setLoading(false);
                setTerminalError(
                    err instanceof Error
                        ? err.message
                        : "Failed to open the inference playground"
                );
                const retry = openRetryCountRef.current++;
                const retryDelay = Math.min(30000, 1000 * Math.pow(2, Math.min(retry, 5)));
                openRetryTimerRef.current = window.setTimeout(() => {
                    if (!mountedRef.current) return;
                    setRefreshNonce((v) => v + 1);
                }, retryDelay);
            });

        return () => {
            cancelled = true;
            if (openRetryTimerRef.current !== null) {
                window.clearTimeout(openRetryTimerRef.current);
                openRetryTimerRef.current = null;
            }
        };
    }, [refreshNonce]);

    const handleConnected = React.useCallback(() => {
        if (!mountedRef.current) return;
        setTerminalError("");
    }, []);

    const handleDisconnected = React.useCallback(() => {
        if (!mountedRef.current) return;
    }, []);

    const handleTransportError = React.useCallback((message: string) => {
        if (!mountedRef.current) return;
        setTerminalError(message);
        if (message.includes("Reconnect limit reached")) {
            setRefreshNonce((v) => v + 1);
        }
    }, []);

    const handleModelChange = React.useCallback((model: Record<string, Value>) => {
        if (Object.keys(model).length > 0) {
            setLastModel(model);
        }
    }, []);

    if (!session) {
        return (
            <PlaygroundComposerDraftContext.Provider value={{draft: composerDraft, setDraft: setComposerDraft}}>
                <PlaygroundFrame
                    model={lastModel}
                    connected={false}
                    mounted={false}
                    loadingSession={loading}
                    error={loading ? "" : (terminalError || "Unable to open inference playground.")}
                />
            </PlaygroundComposerDraftContext.Provider>
        );
    }

    const providerDomain = playgroundProviderDomain(session.connectTo);

    return (
        <PlaygroundProviderDomainContext.Provider value={providerDomain}>
            <PlaygroundComposerDraftContext.Provider value={{draft: composerDraft, setDraft: setComposerDraft}}>
                <UcxView
                    key={`${projectId ?? ""}:${session.sessionToken}`}
                    url={session.connectTo}
                    authToken={session.sessionToken}
                    sysHello={JSON.stringify({})}
                    maxReconnectAttempts={MAX_RECONNECT_ATTEMPTS}
                    onConnected={handleConnected}
                    onDisconnected={handleDisconnected}
                    onTransportError={handleTransportError}
                    onModelChange={handleModelChange}
                    components={playgroundComponents}
                    rehydrateModelPaths={PLAYGROUND_REHYDRATE_PATHS}
                    renderFrame={({connected, mounted, transportError, content, model, fn}) => (
                        <PlaygroundFrame
                            model={model ?? lastModel}
                            fn={fn}
                            ucxContent={content}
                            connected={connected}
                            mounted={mounted ?? false}
                            error={terminalError || transportError}
                        />
                    )}
                />
            </PlaygroundComposerDraftContext.Provider>
        </PlaygroundProviderDomainContext.Provider>
    );
}

function stringProp(
    node: { props?: Record<string, Value> },
    key: string,
    fallback: string
): string {
    const value = node.props?.[key];
    if (value && value.kind === ValueKind.String && value.string !== "") {
        return value.string;
    }
    return fallback;
}

function numberProp(
    node: { props?: Record<string, Value> },
    key: string,
    fallback: number
): number {
    const value = node.props?.[key];
    if (!value) return fallback;
    if (value.kind === ValueKind.S64) return value.s64;
    if (value.kind === ValueKind.F64) return value.f64;
    return fallback;
}

function boolProp(
    node: { props?: Record<string, Value> },
    key: string,
    fallback: boolean
): boolean {
    const value = node.props?.[key];
    if (value && value.kind === ValueKind.Bool) {
        return value.bool;
    }
    return fallback;
}

function optionsProp(node: { props?: Record<string, Value> }, key: string): PlaygroundOption[] {
    const value = node.props?.[key];
    if (!value || value.kind !== ValueKind.List) return [];
    return value.list.flatMap((item: Value) => {
        if (item.kind !== ValueKind.Object) return [];
        const optionKey = stringValue(item.object.key);
        const optionValue = stringValue(item.object.value);
        if (optionKey === "") return [];
        return [{key: optionKey, value: optionValue === "" ? optionKey : optionValue}];
    });
}

function playgroundProviderDomain(connectTo: string): string {
    try {
        return new URL(connectTo).host;
    } catch {
        return "";
    }
}

function playgroundAttachmentUrl(providerDomain: string, attachmentId: string): string {
    if (providerDomain === "" || attachmentId === "") return "";
    return `https://${providerDomain}/api/inference/attachments/download?id=${encodeURIComponent(attachmentId)}`;
}

function textGenerationModelOptions(value: any): PlaygroundOption[] {
    if (!value || value.kind !== ValueKind.List) return [];
    return value.list.flatMap((item: Value) => {
        if (item.kind !== ValueKind.Object) return [];
        const capabilities = item.object.capabilities;
        if (!capabilities || capabilities.kind !== ValueKind.List) return [];
        const supportsTextGeneration = capabilities.list.some((capability) => stringValue(capability) === "TextGeneration");
        if (!supportsTextGeneration) return [];
        const name = stringValue(item.object.name);
        const title = stringValue(item.object.title);
        if (name === "") return [];
        return [{key: name, value: title === "" ? name : title}];
    }).sort((a, b) => a.key.localeCompare(b.key));
}

function modelCapabilities(model: Record<string, Value>, modelName: string): string[] {
    const models = model.models;
    if (!models || models.kind !== ValueKind.List) return [];
    for (const item of models.list) {
        if (item.kind !== ValueKind.Object) continue;
        if (stringValue(item.object.name) !== modelName) continue;
        const capabilities = item.object.capabilities;
        if (!capabilities || capabilities.kind !== ValueKind.List) return [];
        return capabilities.list.map(stringValue).filter(Boolean);
    }
    return [];
}

async function detectPlaygroundAttachmentKind(file: File): Promise<PlaygroundAttachmentKind> {
    const mimeKind = typeFromMime(file.type);
    if (mimeKind === "image" || mimeKind === "video" || mimeKind === "audio") return mimeKind;
    if (isConvertiblePlaygroundAttachment(file.name, file.type)) return "convertible";

    const extKind = extensionType(extensionFromPath(file.name));
    if (extKind === "image" || extKind === "video" || extKind === "audio") return extKind;
    if (extKind === "text" || extKind === "code" || extKind === "markdown") return "text";

    const sample = new Uint8Array(await file.slice(0, Math.min(file.size, 4096)).arrayBuffer());
    return isUtf8Text(sample) ? "text" : "unsupported";
}

function isConvertiblePlaygroundAttachment(fileName: string, mimeType: string): boolean {
    const ext = extensionFromPath(fileName).toLowerCase();
    if (["pdf", "ppt", "pptx", "doc", "docx", "xls", "xlsx", "epub", "zip"].includes(ext)) return true;
    return [
        "application/pdf",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/epub+zip",
        "application/zip",
        "application/x-zip-compressed",
    ].includes(mimeType.toLowerCase());
}

function playgroundAttachmentRejection(kind: PlaygroundAttachmentKind, capabilities: string[]): string {
    switch (kind) {
        case "image":
            return capabilities.includes("Vision") ? "" : "The selected model does not support image attachments.";
        case "video":
            return capabilities.includes("VideoVision") ? "" : "The selected model does not support video attachments.";
        case "audio":
            return capabilities.includes("Audio") ? "" : "The selected model does not support audio attachments.";
        case "text":
            return "";
        case "convertible":
            return "";
        case "unsupported":
            return "This file type is not supported by the selected model.";
    }
}

function isUtf8Text(bytes: Uint8Array): boolean {
    try {
        new TextDecoder("utf-8", {fatal: true}).decode(bytes);
        return true;
    } catch {
        return false;
    }
}

function stringValue(value: any): string {
    if (!value || value.kind !== ValueKind.String) return "";
    return value.string ?? "";
}

function boolValue(value: any): boolean {
    if (!value || value.kind !== ValueKind.Bool) return false;
    return value.bool;
}

function numberValue(value: any): number {
    if (!value) return 0;
    if (value.kind === ValueKind.F64) return value.f64 ?? 0;
    if (value.kind === ValueKind.S64) return value.s64 ?? 0;
    return 0;
}

function chatMessagePartsValue(value: any): ChatMessagePart[] {
    if (!value || value.kind !== ValueKind.List) return [];
    return value.list.flatMap((item: Value) => {
        if (item.kind !== ValueKind.Object) return [];
        const kind = stringValue(item.object.kind);
        if (kind !== "text" && kind !== "thinking" && kind !== "image" && kind !== "video" && kind !== "audio" && kind !== "attachment") return [];
        return [
            {
                kind,
                text: stringValue(item.object.text),
                summary: stringValue(item.object.summary),
                body: stringValue(item.object.body),
                open: boolValue(item.object.open),
                fileName: stringValue(item.object.fileName),
                url: stringValue(item.object.url),
            },
        ];
    });
}

function chatMessagesValue(value: any): ChatMessageListItem[] {
    if (!value || value.kind !== ValueKind.List) return [];
    return value.list.flatMap((item: Value) => {
        if (item.kind !== ValueKind.Object) return [];
        const role = stringValue(item.object.role);
        if (role === "") return [];
        return [{
            role,
            content: stringValue(item.object.content),
            parts: chatMessagePartsValue(item.object.parts),
        }];
    });
}

function isAttachmentMessagePart(part: ChatMessagePart): boolean {
    return part.kind === "image" || part.kind === "video" || part.kind === "audio" || part.kind === "attachment";
}

function isAttachmentOnlyUserMessage(message: ChatMessageListItem): boolean {
    return message.role === "user" && message.parts.length > 0 && message.parts.every(isAttachmentMessagePart);
}

function isTextUserMessage(role: string, content: string, parts: ChatMessagePart[]): boolean {
    if (role !== "user" || content.trim() === "") return false;
    return !parts.every(isAttachmentMessagePart);
}

function shouldHideCollapsedAttachmentMessage(
    messages: ChatMessageListItem[],
    messageIndex: number,
    role: string,
    content: string,
    parts: ChatMessagePart[]
): boolean {
    if (!isAttachmentOnlyUserMessage({role, content, parts})) return false;
    for (let i = messageIndex + 1; i < messages.length; i++) {
        const message = messages[i];
        if (message.role === "assistant") return false;
        if (isTextUserMessage(message.role, message.content, message.parts)) return true;
    }
    return false;
}

function collapsedAttachmentPartsForMessage(messages: ChatMessageListItem[], messageIndex: number): ChatMessagePart[] {
    const message = messages[messageIndex];
    if (!message || !isTextUserMessage(message.role, message.content, message.parts)) return [];

    const attachments: ChatMessagePart[] = [];
    for (let i = messageIndex - 1; i >= 0; i--) {
        const previous = messages[i];
        if (!isAttachmentOnlyUserMessage(previous)) break;
        attachments.unshift(...previous.parts);
    }
    return attachments;
}

function orderUserMessageParts(parts: ChatMessagePart[]): ChatMessagePart[] {
    return [
        ...parts.filter(part => !isAttachmentMessagePart(part)),
        ...parts.filter(isAttachmentMessagePart),
    ];
}

function threadListValue(value: any): ThreadListItem[] {
    if (!value || value.kind !== ValueKind.List) return [];
    return value.list.flatMap((item: Value) => {
        if (item.kind !== ValueKind.Object) return [];
        const id = stringValue(item.object.id);
        const title = stringValue(item.object.title);
        if (id === "") return [];
        return [{id, title: title === "" ? "New thread" : title}];
    });
}
