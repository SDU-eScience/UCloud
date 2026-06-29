import * as React from "react";
import ReactModal from "react-modal";

import {callAPI} from "@/Authentication/DataHook";
import {dialogStore} from "@/Dialog/DialogStore";
import FileBrowse from "@/Files/FileBrowse";
import {MainContainer} from "@/ui-components/MainContainer";
import {Box, Button, Flex, Icon, Image, Input, Text, TextArea,} from "@/ui-components";
import CodeSnippet from "@/ui-components/CodeSnippet";
import {Toggle} from "@/ui-components/Toggle";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {api as FilesApi} from "@/UCloud/FilesApi";
import {Selection} from "@/ui-components/ResourceBrowser";
import UcxView, {UcxComponentRegistry, UcxRenderContext} from "@/UCX/UcxView";
import {Value, ValueKind} from "@/UCX/protocol";
import {usePrettyFilePath} from "@/Files/FilePath";
import {UFile} from "@/UCloud/UFile";
import {copyToClipboard, doNothing, removeTrailingSlash} from "@/UtilityFunctions";
import {addStandardInputDialog} from "@/UtilityComponents";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {Operation, Operations, ShortcutKey} from "@/ui-components/Operation";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {openPlayground} from "./api";
import {getParentPath} from "@/Utilities/FileUtilities";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import Table from "@/ui-components/Table";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import * as Heading from "@/ui-components/Heading";
import Tooltip from "@/ui-components/Tooltip";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {RichSelect} from "@/ui-components/RichSelect";
import LogoDeepseek from "@/Assets/Images/inference/deepseek.png";
import LogoGoogle from "@/Assets/Images/inference/google.png";
import LogoMeta from "@/Assets/Images/inference/meta.png";
import LogoMinimax from "@/Assets/Images/inference/minimax.png";
import LogoMistral from "@/Assets/Images/inference/mistral.png";
import LogoMoonshot from "@/Assets/Images/inference/moonshot.png";
import LogoOpenAI from "@/Assets/Images/inference/oai.png";
import LogoQwen from "@/Assets/Images/inference/qwen.png";
import LogoZai from "@/Assets/Images/inference/zai.png";
import {IconName} from "@/ui-components/Icon";
import {format, isToday} from "date-fns";

type PlaygroundSession = {
    connectTo: string;
    sessionToken: string;
};

type PlaygroundOption = { key: string; value: string };

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
        --playground-panel: transparent;
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

    inference_image_preview: ({node, model, scope, fn}: UcxRenderContext) => {
        return <ImagePreviewNode node={node} model={model} scope={scope} fn={fn}/>;
    },

    inference_transcription_composer: ({
                                           node,
                                           model,
                                           scope,
                                           fn,
                                       }: UcxRenderContext) => {
        const filePathBindPath = stringProp(
            node,
            "filePathBindPath",
            "transcriptionFilePath"
        );
        const promptBindPath = stringProp(
            node,
            "promptBindPath",
            "transcriptionPrompt"
        );
        const filePathPlaceholder = stringProp(
            node,
            "filePathPlaceholder",
            "Audio file path"
        );
        const promptPlaceholder = stringProp(
            node,
            "promptPlaceholder",
            "Optional prompt"
        );
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
                onClick: (res) => {
                    const target = removeTrailingSlash(res.id);
                    fn.sendBoundInput(
                        {...node, bindPath: filePathBindPath} as any,
                        {kind: ValueKind.String, string: target},
                        model,
                        scope
                    );
                    dialogStore.success();
                },
                show: (file) => file.status.type === "FILE",
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
                FilesApi.fileSelectorModalStyle
            );
        };

        return (
            <Box
                style={{
                    position: "relative",
                    width: "100%",
                    display: "flex",
                    flexDirection: "column",
                    gap: 8,
                }}
            >
                <div style={{display: "flex", gap: 8, alignItems: "center"}}>
                    <Input
                        value={prettyPath}
                        placeholder={filePathPlaceholder}
                        readOnly={true}
                        onClick={openFileBrowser}
                        title={filePath || filePathPlaceholder}
                        style={{cursor: "pointer"}}
                    />
                    <Button type="button" onClick={openFileBrowser} m={0}>
                        Browse
                    </Button>
                </div>
                <TextArea
                    rows={rows}
                    placeholder={promptPlaceholder}
                    value={prompt}
                    onChange={(ev) =>
                        fn.sendBoundInput(
                            {...node, bindPath: promptBindPath} as any,
                            {kind: ValueKind.String, string: ev.currentTarget.value},
                            model,
                            scope
                        )
                    }
                    onKeyDown={(ev) => {
                        if ((ev.ctrlKey || ev.metaKey) && ev.key === "Enter") {
                            ev.preventDefault();
                            ev.stopPropagation();
                            send();
                        }
                    }}
                    style={{paddingRight: 96, paddingBottom: 44, resize: "none"}}
                />
                <div
                    style={{
                        position: "absolute",
                        right: 24,
                        bottom: 12,
                        padding: 0,
                        width: 36,
                        height: 36,
                    }}
                >
                    <Button type="button" disabled={!canSend} onClick={send} m={0}>
                        <Icon name={sendIcon as any} size={14}/>
                    </Button>
                </div>
            </Box>
        );
    },

    inference_image_composer: ({node, model, scope, fn}: UcxRenderContext) => {
        const placeholder = stringProp(
            node,
            "placeholder",
            "Describe the image to generate"
        );
        const rows = numberProp(node, "rows", 4);
        const sendIcon = stringProp(node, "sendIcon", "heroSparkles");
        const disabled = boolProp(node, "disabled", false);
        const value = stringValue(fn.modelValue(model, node.bindPath, scope));
        const canSend = !disabled && value.trim() !== "";

        const send = () => {
            if (!canSend) return;
            fn.sendUiEvent(node.id, "click", {
                kind: ValueKind.String,
                string: value,
            });
        };

        return (
            <Box style={{position: "relative", width: "100%"}}>
                <TextArea
                    rows={rows}
                    placeholder={placeholder}
                    value={value}
                    onChange={(ev) =>
                        fn.sendBoundInput(
                            node,
                            {kind: ValueKind.String, string: ev.currentTarget.value},
                            model,
                            scope
                        )
                    }
                    onKeyDown={(ev) => {
                        if ((ev.ctrlKey || ev.metaKey) && ev.key === "Enter") {
                            ev.preventDefault();
                            ev.stopPropagation();
                            send();
                        }
                    }}
                    style={{paddingRight: 96, paddingBottom: 44, resize: "none"}}
                />
                <div
                    style={{
                        position: "absolute",
                        right: 24,
                        bottom: 12,
                        padding: 0,
                        width: 36,
                        height: 36,
                    }}
                >
                    <Button type="button" disabled={!canSend} onClick={send} m={0}>
                        <Icon name={sendIcon as any} size={14}/>
                    </Button>
                </div>
            </Box>
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
        const value = stringValue(fn.modelValue(model, node.bindPath, scope));
        const canSend = !disabled && value.trim() !== "";

        const send = () => {
            if (!canSend) return;
            fn.sendUiEvent(node.id, "click", {
                kind: ValueKind.String,
                string: value,
            });
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
                <TextArea
                    resize={"none"}
                    rows={rows}
                    placeholder={placeholder}
                    value={value}
                    onChange={(ev) =>
                        fn.sendBoundInput(
                            node,
                            {kind: ValueKind.String, string: ev.currentTarget.value},
                            model,
                            scope
                        )
                    }
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
                <div
                    style={{
                        display: "flex",
                        alignItems: "center",
                        gap: 6,
                        flexShrink: 0,
                        padding: "0 10px 10px 10px",
                    }}
                >
                    <Tooltip tooltipContentWidth={180} trigger={
                        <span style={{display: "inline-flex"}}>
                            <button type="button" disabled className={ComposerActionButtonClass}>
                                <Icon name="heroPlus" size={18}/>
                            </button>
                        </span>
                    }>
                        Attachments are not available yet.
                    </Tooltip>
                    <RichSelect<PlaygroundOption, keyof PlaygroundOption>
                        items={modelOptions}
                        keys={["key", "value"]}
                        selected={selectedModelOption}
                        onSelect={(option) =>
                            fn.sendBoundInput(
                                {...node, bindPath: "chat.modelId"} as any,
                                {kind: ValueKind.String, string: option.key},
                                model,
                                scope
                            )
                        }
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

        React.useLayoutEffect(() => {
            const el = containerRef.current;
            if (!el) return;
            el.scrollTop = el.scrollHeight;
        }, [model, scope]);

        return (
            <div
                ref={containerRef}
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
    kind: "text" | "thinking";
    text: string;
    summary: string;
    body: string;
    open: boolean;
};

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

function ModelInferenceLogo({modelName}: { modelName: string }): React.ReactNode {
    const norm = modelName.toLowerCase();

    let img = "";
    if (norm.includes("deepseek")) {
        img = LogoDeepseek;
    } else if (norm.includes("llama")) {
        img = LogoMeta;
    } else if (norm.includes("gpt")) {
        img = LogoOpenAI;
    } else if (norm.includes("minimax")) {
        img = LogoMinimax;
    } else if (norm.includes("qwen")) {
        img = LogoQwen;
    } else if (norm.includes("glm")) {
        img = LogoZai;
    } else if (norm.includes("mistral")) {
        img = LogoMistral;
    } else if (norm.includes("google") || norm.includes("gemma")) {
        img = LogoGoogle;
    } else if (norm.includes("kimi") || norm.includes("k2.")) {
        img = LogoMoonshot;
    }

    if (img == "") {
        return (
            <span
                title={modelName}
                aria-hidden="true"
                style={{
                    width: 16,
                    height: 16,
                    borderRadius: "50%",
                    background: "var(--primaryMain)",
                    display: "inline-block",
                    flexShrink: 0,
                }}
            />
        );
    } else {
        return <Flex
            background={"var(--playground-logo-bg, var(--secondaryMain))"}
            border={"1px solid var(--playground-border, var(--borderColor))"}
            borderRadius={"8px"}
            height={24}
            width={24}
            alignItems={"center"}
            justifyContent={"center"}
        >
            <img src={img} alt={`${modelName} logo`} style={{maxHeight: 16, maxWidth: 16}} />
        </Flex>;
    }
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

    if (role === "user") {
        return (
            <Flex width="100%" justifyContent="flex-end" my={16} flexDirection="column" alignItems="flex-end" gap="6px">
                <div style={{maxWidth: "78%", borderRadius: 16, padding: "10px 14px", background: "var(--playground-user-bg, var(--secondaryMain))", color: "var(--playground-user-text, var(--textPrimary))", overflowWrap: "anywhere"}}>
                    {messageParts.map((part, idx) => <span key={idx}>{part.text}</span>)}
                </div>
                <Flex className={ComposerActionButtonHoverClass} alignItems="center" gap="8px" color="textSecondary" fontSize="12px">
                    <span>{formatTimeOfDay(generatedAt)}</span>
                    <MessageIconButton label="Copy message" icon="heroDocumentDuplicate" onClick={() => copyMessage(content)}/>
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
                return <MarkdownPart key={idx} text={part.text}/>;
            })}
            {!responseFinished ? null : <Flex className={ComposerActionButtonHoverClass} alignItems="center" gap="8px" color="textSecondary" fontSize="12px" flexWrap="wrap">
                <MessageIconButton label="Copy response" icon="heroDocumentDuplicate" onClick={() => copyMessage(content)}/>
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
                    trigger={<MessageIconButton label={`Regenerate (used: ${regenerateModelLabel})`} icon="heroArrowPath"/>}
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

function MessageIconButton({label, icon, onClick}: {label: string; icon: IconName; onClick?: () => void}): React.ReactNode {
    const button = (
        <button
            type="button"
            aria-label={label}
            onClick={onClick}
            className={ComposerActionButtonClass}
        >
            <Icon name={icon} size={18}/>
        </button>
    );

    const tooltipWidth = label.length * 10.5;

    return (
        <Tooltip tooltipContentWidth={tooltipWidth} trigger={<span style={{display: "inline-flex"}}>{button}</span>}>
            {label}
        </Tooltip>
    );
}

async function copyMessage(content: string): Promise<void> {
    try {
        await copyToClipboard(content);
        sendSuccessNotification("Message copied to clipboard");
    } catch {
        sendFailureNotification("Failed to copy message");
    }
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

function MarkdownPart({text}: { text: string }): React.ReactNode {
    if (text.trim() === "") return null;
    return (
        <ReactMarkdown
            components={{
                a: (p) => (
                    <a href={p.href} target="_blank" rel="noreferrer">
                        {p.children}
                    </a>
                ),
                pre: (p) => <Box my={16}><CodeSnippet children={p.children} maxHeight=""/></Box>,
                table: p => <MarkdownTable>{p.children}</MarkdownTable>,
                h1: p => <Heading.h1>{p.children}</Heading.h1>,
                h2: p => <Heading.h2>{p.children}</Heading.h2>,
                h3: p => <Heading.h3>{p.children}</Heading.h3>,
                h4: p => <Heading.h4>{p.children}</Heading.h4>,
                h5: p => <Heading.h5>{p.children}</Heading.h5>,
                h6: p => <Heading.h6>{p.children}</Heading.h6>,
            }}
            allowedElements={[
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "br",
                "a",
                "p",
                "strong",
                "b",
                "i",
                "em",
                "ul",
                "ol",
                "li",
                "pre",
                "code",
                "table",
                "th",
                "tbody",
                "thead",
                "td",
                "tr",
            ]}
            children={text}
            remarkPlugins={[remarkGfm]}
        />
    );
}

function MarkdownTable({children}: React.PropsWithChildren): React.ReactNode {
    const wrapperRef = React.useRef<HTMLDivElement | null>(null);
    const [layout, setLayout] = React.useState({scroll: false, minWidth: 0});

    React.useLayoutEffect(() => {
        const wrapper = wrapperRef.current;
        if (!wrapper) return;

        let frame = 0;
        const measure = () => {
            window.cancelAnimationFrame(frame);
            frame = window.requestAnimationFrame(() => {
                const next = measureMarkdownTable(wrapper);
                setLayout(prev => prev.scroll === next.scroll && prev.minWidth === next.minWidth ? prev : next);
            });
        };

        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(wrapper);
        return () => {
            window.cancelAnimationFrame(frame);
            observer.disconnect();
        };
    }, [children]);

    return <div ref={wrapperRef} style={{overflowX: layout.scroll ? "auto" : "visible", maxWidth: "100%"}}>
        <Table tableType="presentation" minWidth={layout.scroll ? `${layout.minWidth}px` : undefined}>
            {children}
        </Table>
    </div>;
}

function measureMarkdownTable(wrapper: HTMLDivElement): { scroll: boolean; minWidth: number } {
    const rows = Array.from(wrapper.querySelectorAll("tr"));
    const availableWidth = wrapper.clientWidth;
    if (rows.length === 0 || availableWidth === 0) {
        return {scroll: false, minWidth: 0};
    }

    const columnStats: Array<{ textLength: number; tokenLength: number; renderedWidth: number }> = [];
    for (const row of rows) {
        const cells = Array.from(row.children).filter((cell): cell is HTMLElement => cell instanceof HTMLElement);
        for (let idx = 0; idx < cells.length; idx++) {
            const cell = cells[idx];
            const text = (cell.textContent ?? "").replace(/\s+/g, " ").trim();
            const longestToken = text.split(/\s+/).reduce((longest, token) => Math.max(longest, token.length), 0);
            const stats = columnStats[idx] ?? {textLength: 0, tokenLength: 0, renderedWidth: 0};
            stats.textLength = Math.max(stats.textLength, text.length);
            stats.tokenLength = Math.max(stats.tokenLength, longestToken);
            stats.renderedWidth = Math.max(stats.renderedWidth, cell.getBoundingClientRect().width);
            columnStats[idx] = stats;
        }
    }

    let forceScroll = false;
    const desiredWidth = columnStats.reduce((sum, stats) => {
        let columnWidth = 120;
        if (stats.tokenLength >= 40) {
            forceScroll = true;
            columnWidth = clamp(stats.tokenLength * 8, 240, 720);
        } else if (stats.textLength >= 120) {
            columnWidth = clamp(stats.textLength * 5, 320, 640);
        } else if (stats.textLength >= 60) {
            columnWidth = clamp(stats.textLength * 4, 200, 360);
        } else {
            columnWidth = clamp(stats.textLength * 6 + 24, 80, 200);
        }
        return sum + Math.max(columnWidth, Math.min(stats.renderedWidth, 240));
    }, 0);

    const minWidth = Math.ceil(Math.max(desiredWidth, availableWidth));
    const shouldScroll = forceScroll || desiredWidth > availableWidth + 8;
    return {scroll: shouldScroll, minWidth: shouldScroll ? minWidth : 0};
}

function clamp(value: number, min: number, max: number): number {
    return Math.max(min, Math.min(max, value));
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
                        <Text color="textSecondary">Thinking...</Text>
                    ) : (
                        <MarkdownPart text={part.body}/>
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

export default function Playground(): React.ReactNode {
    const [session, setSession] = React.useState<PlaygroundSession | null>(null);
    const [loading, setLoading] = React.useState(true);
    const [terminalError, setTerminalError] = React.useState("");
    const [refreshNonce, setRefreshNonce] = React.useState(0);
    const retryCountRef = React.useRef(0);
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
        retryCountRef.current = 0;
        setSession(null);
        setRefreshNonce((x) => x + 1);
    }, [projectId]);

    React.useEffect(() => {
        let cancelled = false;

        setLoading(true);
        setSession(null);
        void callAPI(openPlayground({providerId: null}))
            .then((result) => {
                if (cancelled) return;
                setSession(result);
                setLoading(false);
                setTerminalError("");
            })
            .catch((err) => {
                if (cancelled) return;
                setSession(null);
                setLoading(false);
                setTerminalError(
                    err instanceof Error
                        ? err.message
                        : "Failed to open the inference playground"
                );
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
        setRefreshNonce((v) => v + 1);
    }, []);

    if (!session) {
        return (
            <MainContainer
                main={
                    <div style={{padding: 24}}>
                        <Text>
                            {loading
                                ? "Opening inference playground..."
                                : "Unable to open inference playground."}
                        </Text>
                        {terminalError === "" ? null : (
                            <Text color="errorMain">{terminalError}</Text>
                        )}
                    </div>
                }
            />
        );
    }

    return (
        <MainContainer
            main={
                <div className={PlaygroundThemeClass} style={{display: "flex", flexDirection: "column", gap: 8}}>
                    <Flex mb={24}>
                        <h3
                            className="title"
                            style={{marginTop: "auto", marginBottom: "auto"}}
                        >
                            AI Inference: Playground
                        </h3>
                        <Box flexGrow={1}/>
                        <ProjectSwitcher/>
                    </Flex>
                    {terminalError === "" ? null : (
                        <Text color="errorMain">{terminalError}</Text>
                    )}
                    <UcxView
                        key={`${projectId ?? ""}:${session.sessionToken}`}
                        url={session.connectTo}
                        authToken={session.sessionToken}
                        sysHello={JSON.stringify({})}
                        maxReconnectAttempts={MAX_RECONNECT_ATTEMPTS}
                        onConnected={handleConnected}
                        onDisconnected={handleDisconnected}
                        components={playgroundComponents}
                    />
                </div>
            }
        />
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
        if (kind !== "text" && kind !== "thinking") return [];
        return [
            {
                kind,
                text: stringValue(item.object.text),
                summary: stringValue(item.object.summary),
                body: stringValue(item.object.body),
                open: boolValue(item.object.open),
            },
        ];
    });
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

type ImagePreviewNodeProps = Pick<
    UcxRenderContext,
    "node" | "model" | "scope" | "fn"
>;

const ImagePreviewNode: React.FC<ImagePreviewNodeProps> = ({
                                                               node,
                                                               model,
                                                               scope,
                                                               fn,
                                                           }) => {
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

    return (
        <div
            style={{
                display: "flex",
                flexDirection: "column",
                gap: 12,
                ...fn.sxStyle(node),
            }}
        >
            {output.trim() === "" ? (
                <Text color="textSecondary">You have not requested anything yet.</Text>
            ) : null}
            {output.trim() !== "" && images.length === 0 ? (
                <Text color="textSecondary">
                    Image preview is not possible for this response format.
                </Text>
            ) : null}
            {images.length === 0 ? null : (
                <div
                    style={{
                        display: "grid",
                        gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
                        gap: 12,
                    }}
                >
                    {images.map((src, idx) => (
                        <button
                            key={`${idx}-${src.slice(0, 12)}`}
                            type="button"
                            onClick={() => openImage(src, idx)}
                            style={{
                                border: "1px solid var(--playground-border, var(--borderColor))",
                                borderRadius: 8,
                                padding: 8,
                                background: "var(--playground-surface, var(--backgroundDefault))",
                                minWidth: 0,
                                display: "block",
                                textDecoration: "none",
                                color: "inherit",
                                cursor: "pointer",
                                textAlign: "left",
                            }}
                        >
                            <img
                                src={src}
                                alt={`Generated image ${idx + 1}`}
                                style={{
                                    display: "block",
                                    width: "100%",
                                    maxHeight: 280,
                                    objectFit: "contain",
                                    borderRadius: 6,
                                }}
                            />
                        </button>
                    ))}
                </div>
            )}
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
                <div
                    style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        gap: 16,
                    }}
                >
                    <Text style={{fontWeight: 600}}>Image {selectedIndex + 1}</Text>
                    <Button type="button" onClick={closeImage} m={0}>
                        Close
                    </Button>
                </div>
                {selectedImage === null ? null : (
                    <div
                        style={{
                            flex: 1,
                            minHeight: 0,
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            padding: 16,
                            background: "var(--playground-surface, var(--backgroundDefault))",
                            borderRadius: 8,
                        }}
                    >
                        <img
                            src={selectedImage}
                            alt={`Generated image ${selectedIndex + 1}`}
                            style={{
                                maxWidth: "100%",
                                maxHeight: "100%",
                                objectFit: "contain",
                                borderRadius: 8,
                            }}
                        />
                    </div>
                )}
            </ReactModal>
            {output.trim() === "" ? null : (
                <div style={{display: "flex", flexDirection: "column", gap: 8}}>
                    <Text style={{fontWeight: 600}}>JSON response:</Text>
                    <CodeSnippet maxHeight="320px">{output}</CodeSnippet>
                </div>
            )}
        </div>
    );
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
