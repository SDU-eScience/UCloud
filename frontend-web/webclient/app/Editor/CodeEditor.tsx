import * as React from "react";
import {useEffect, useId, useLayoutEffect, useRef, useState} from "react";
import {useSelector} from "react-redux";
import {editor} from "monaco-editor";
import {initVimMode, VimMode} from "monaco-vim";
import {Flex, Text} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import {TabStrip} from "@/ui-components/TabStrip";
import {FullpathFileLanguageIcon} from "@/Editor/Editor";
import {injectStyle} from "@/Unstyled";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {sendFailureNotification} from "@/Notifications";
import {useMonaco} from "./Monaco";
import {bindEditorSchema, EditorSchemaRegistry} from "./SchemaRegistry";
import {allowEditDialog, getEditorOptions, mapStoredBindings, MonacoEditorSettings, subscribeEditorSettings} from "./EditorSettings";

export interface CodeEditorProps {
    documentId?: string;
    value?: string;
    language?: string;
    schemaId?: string;
    schemaRegistry?: EditorSchemaRegistry;
    readOnly?: boolean;
    saving?: boolean;
    closeLabel?: string;
    dirty?: boolean;
    autoFocus?: boolean;
    tabLabel?: string;
    manageModel?: boolean;
    showToolbar?: boolean;
    settingsOpen?: boolean;
    showContent?: boolean;
    children?: React.ReactNode;
    toolbar?: React.ReactNode;
    statusBarStart?: React.ReactNode;
    statusBarEnd?: React.ReactNode;
    statusBarClassName?: string;
    style?: React.CSSProperties;
    onChange?: (value: string) => void;
    onSave?: (value: string) => void | boolean | Promise<void | boolean>;
    onClose?: () => void | Promise<void>;
    onOpenFile?: (path: string) => void;
    onReady?: (instance: editor.IStandaloneCodeEditor | null) => void;
    onFocus?: () => void;
    onReadOnlyChange?: (readOnly: boolean) => void;
    onSettingsToggle?: () => void;
}

interface EditorCommands {
    save: () => Promise<boolean>;
    close: () => void;
    open: (path: string) => void;
}

interface VimEditorAdapter {
    editor: editor.IStandaloneCodeEditor;
}

const vimCommands = new Map<editor.IStandaloneCodeEditor, EditorCommands>();
let vimCommandsRegistered = false;

function registerVimCommands() {
    if (vimCommandsRegistered) return;
    VimMode.Vim.defineEx("write", "w", adapter => {
        void vimCommands.get(adapter.editor)?.save();
    });
    VimMode.Vim.defineEx("quit", "q", adapter => {
        vimCommands.get(adapter.editor)?.close();
    });
    const saveAndClose = async (adapter: VimEditorAdapter) => {
        const commands = vimCommands.get(adapter.editor);
        if (!commands) return;
        const saved = await commands.save();
        if (saved) commands.close();
    };
    VimMode.Vim.defineEx("wq", "wq", saveAndClose);
    VimMode.Vim.defineEx("x-write-and-quit", "x", saveAndClose);
    VimMode.Vim.defineEx("e-open-file", "e", (adapter, params) => {
        const path = params.args?.at(-1);
        if (path) vimCommands.get(adapter.editor)?.open(path);
    });
    vimCommandsRegistered = true;
}

export function CodeEditor(props: CodeEditorProps) {
    const monaco = useMonaco(true);
    const theme = useSelector((state: ReduxObject) => state.sidebar.theme);
    const instanceId = useId();
    const container = useRef<HTMLDivElement>(null);
    const commandBar = useRef<HTMLDivElement>(null);
    const latest = useRef(props);
    latest.current = props;
    const [instance, setInstance] = useState<editor.IStandaloneCodeEditor | null>(null);
    const [vimMode, setVimMode] = useState<string | null>(null);
    const [localSettingsOpen, setLocalSettingsOpen] = useState(false);
    const [localSaving, setLocalSaving] = useState(false);
    const [schemaAvailable, setSchemaAvailable] = useState(false);
    const savingRef = useRef(false);
    const ownedModel = useRef<editor.ITextModel | null>(null);
    const vimAdapter = useRef<ReturnType<typeof initVimMode> | null>(null);
    const settingsOpen = props.settingsOpen ?? localSettingsOpen;
    const saving = props.saving || localSaving;

    const save = async (): Promise<boolean> => {
        const current = latest.current;
        if (!instance || !current.onSave || current.readOnly || current.saving || savingRef.current) return false;
        const model = instance.getModel();
        if (!model) return false;
        savingRef.current = true;
        setLocalSaving(true);
        try {
            const result = await current.onSave(instance.getValue());
            const sameDocument = current.documentId === latest.current.documentId && instance.getModel() === model;
            return result !== false && sameDocument;
        } catch (error) {
            sendFailureNotification(errorMessageOrDefault(error, "Failed to save document"));
            return false;
        } finally {
            savingRef.current = false;
            setLocalSaving(false);
        }
    };
    const currentCommands: EditorCommands = {
        save,
        close: () => {
            if (latest.current.saving || savingRef.current) return;
            void latest.current.onClose?.();
        },
        open: path => latest.current.onOpenFile?.(path),
    };
    const commands = useRef(currentCommands);
    commands.current = currentCommands;

    useLayoutEffect(() => {
        if (!monaco || !container.current) return;
        const {vim: _vim, ...settings} = getEditorOptions();
        const instance = monaco.editor.create(container.current, {
            model: null,
            minimap: {enabled: false},
            renderLineHighlight: "none",
            fontFamily: "Jetbrains Mono",
            fontSize: 14,
            wordWrap: "off",
            ...settings,
            readOnly: latest.current.readOnly === true,
            readOnlyMessage: {value: ""},
        });
        const observer = new ResizeObserver(() => instance.layout());
        observer.observe(container.current);
        const change = instance.onDidChangeModelContent(() => latest.current.onChange?.(instance.getValue()));
        const focus = instance.onDidFocusEditorText(() => latest.current.onFocus?.());
        const readonly = instance.onDidAttemptReadOnlyEdit(() => {
            if (latest.current.onReadOnlyChange) {
                allowEditDialog(instance, () => latest.current.onReadOnlyChange?.(false));
            }
        });
        const action = instance.addAction({
            id: "ucloud-save-document",
            label: "Save document",
            keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS],
            run: async () => { await commands.current.save(); },
        });
        registerVimCommands();
        vimCommands.set(instance, {
            save: () => commands.current.save(),
            close: () => commands.current.close(),
            open: path => commands.current.open(path),
        });
        setInstance(instance);
        latest.current.onReady?.(instance);
        return () => {
            latest.current.onReady?.(null);
            vimCommands.delete(instance);
            vimAdapter.current?.dispose();
            vimAdapter.current = null;
            observer.disconnect();
            change.dispose();
            focus.dispose();
            readonly.dispose();
            action.dispose();
            instance.setModel(null);
            ownedModel.current?.dispose();
            ownedModel.current = null;
            instance.dispose();
        };
    }, [monaco]);

    useEffect(() => {
        if (!instance) return;
        const apply = () => {
            const {vim: enabled, ...settings} = getEditorOptions();
            instance.updateOptions(settings);
            if (enabled && !vimAdapter.current) {
                mapStoredBindings();
                vimAdapter.current = initVimMode(instance, commandBar.current);
                setVimMode("NORMAL");
                vimAdapter.current.on("vim-mode-change", (mode: unknown) => setVimMode(vimModeLabel(mode)));
            } else if (!enabled && vimAdapter.current) {
                vimAdapter.current.dispose();
                vimAdapter.current = null;
                setVimMode(null);
            }
        };
        apply();
        const unsubscribe = subscribeEditorSettings(apply);
        return () => {
            unsubscribe();
            vimAdapter.current?.dispose();
            vimAdapter.current = null;
        };
    }, [instance]);

    useLayoutEffect(() => {
        if (!monaco || !instance || props.manageModel === false) return;
        const model = monaco.editor.createModel(latest.current.value ?? "", latest.current.language ?? "plaintext",
            monaco.Uri.from({scheme: "ucloud-editor", authority: instanceId, path: "/" + (props.documentId ?? "document")}));
        ownedModel.current = model;
        instance.setModel(model);
        return () => {
            if (ownedModel.current === model) {
                instance.setModel(null);
                ownedModel.current = null;
            }
            model.dispose();
        };
    }, [monaco, instance, instanceId, props.documentId, props.manageModel]);

    useEffect(() => {
        const schemaId = props.schemaId;
        const registry = props.schemaRegistry;
        if (!instance || !schemaId || !registry) return;
        const bind = () => {
            const model = instance.getModel();
            if (!model) {
                setSchemaAvailable(false);
                return () => {};
            }
            return bindEditorSchema(registry, schemaId, model, setSchemaAvailable);
        };
        let unbind = bind();
        const change = instance.onDidChangeModel(() => {
            unbind();
            unbind = bind();
        });
        return () => {
            change.dispose();
            unbind();
        };
    }, [instance, props.schemaId, props.schemaRegistry, props.documentId]);

    useLayoutEffect(() => {
        const model = ownedModel.current;
        if (model && props.value !== undefined && model.getValue() !== props.value) model.setValue(props.value);
    }, [instance, props.value]);

    useEffect(() => {
        if (monaco && ownedModel.current) monaco.editor.setModelLanguage(ownedModel.current, props.language ?? "plaintext");
    }, [monaco, instance, props.language]);

    useLayoutEffect(() => {
        instance?.updateOptions({readOnly: props.readOnly === true});
    }, [instance, props.readOnly]);

    useLayoutEffect(() => {
        if (!instance || !props.autoFocus || props.readOnly === true) return;
        if (!instance.getModel()) return;
        instance.focus();
    }, [instance, props.autoFocus, props.readOnly, props.value]);

    useEffect(() => {
        monaco?.editor.setTheme(theme === "light" ? "vs" : "ucloud-dark");
    }, [monaco, theme]);

    useLayoutEffect(() => { instance?.layout(); }, [instance, settingsOpen, props.showContent]);

    const toggleSettings = () => {
        if (props.onSettingsToggle) props.onSettingsToggle();
        else setLocalSettingsOpen(open => !open);
    };

    return <div className={CodeEditorClass} style={props.style}>
        {props.showToolbar !== false ? <Flex alignItems="center" p="8px">
            {props.tabLabel !== undefined ? <div className={CodeEditorTabBar}>
                <TabStrip
                    items={[{
                        id: "document",
                        title: props.tabLabel,
                        icon: <FullpathFileLanguageIcon filePath={props.tabLabel} size="14px" />,
                        closeLabel: "",
                        showClose: false,
                    }]}
                    activeId="document"
                    slim
                    autoSize={false}
                    onActivate={() => undefined}
                    onClose={() => undefined}
                    onReorder={() => undefined}
                />
            </div> : null}
            {props.dirty ? <Text color="textSecondary" ml="12px">Unsaved changes</Text> : null}
            <Flex ml="auto" gap="8px" alignItems="center">
                {props.toolbar}
                {props.onSave ? <IconButton tooltip={saving ? "Saving…" : "Save"} icon="heroCheck" color="successMain" disabled={props.readOnly || saving || !instance} onClick={() => void save()} /> : null}
                {props.onClose ? <IconButton tooltip={props.closeLabel ?? "Close"} icon="heroXMark" disabled={saving} onClick={() => commands.current.close()} /> : null}
                <IconButton tooltip="Settings" icon="heroCog6Tooth" onClick={toggleSettings} />
            </Flex>
        </Flex> : null}
        <div style={{flex: "1 1 0", minHeight: 0, position: "relative", overflow: "hidden"}}>
            <div ref={container} style={{width: "100%", height: "100%", display: settingsOpen || props.showContent ? "none" : "block"}} />
            {settingsOpen ? <div style={{height: "100%", overflow: "auto", padding: 32, boxSizing: "border-box"}}>
                <MonacoEditorSettings editor={instance} onReadOnlyChange={props.onReadOnlyChange} />
            </div> : props.showContent ? props.children : null}
        </div>
        <div className={props.statusBarClassName}>
            <Flex alignItems="center" gap="18px" width="100%" minHeight="32px" p="8px">
                {props.statusBarStart}
                {props.schemaId && (!props.schemaRegistry || !schemaAvailable) ? <Text fontSize="12px" color="textSecondary">Schema validation unavailable</Text> : null}
                <div ref={commandBar} className={VimCommandBar} />
                <Flex ml="auto" alignItems="center" gap="18px">
                    {vimMode ? <span className={StatusModeBadge}>{vimMode}</span> : null}
                    {props.statusBarEnd}
                </Flex>
            </Flex>
        </div>
    </div>;
}

function vimModeLabel(mode: unknown): string {
    const info = typeof mode === "object" && mode !== null ? mode as {mode?: unknown; subMode?: unknown} : undefined;
    const name = typeof mode === "string" ? mode : typeof info?.mode === "string" ? info.mode : "normal";
    if (name === "visual") {
        if (info?.subMode === "linewise") return "VISUAL LINE";
        if (info?.subMode === "blockwise") return "VISUAL BLOCK";
    }
    return name.toUpperCase();
}

const CodeEditorClass = injectStyle("embeddable-code-editor", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100%;
        min-height: 0;
        min-width: 0;
        color: var(--textPrimary);
        background: var(--backgroundDefault);
    }
`);

const CodeEditorTabBar = injectStyle("embeddable-code-editor-tab-bar", k => `
    ${k} {
        display: flex;
        align-items: center;
        width: fit-content;
        max-width: 50%;
        flex: 0 1 auto;
        min-width: 0;
        user-select: none;
    }

    ${k} > [role="tablist"] {
        flex: 0 1 auto;
        width: fit-content;
    }

    ${k} > [role="tablist"][data-slim="true"][data-auto-size="false"] .tab-strip-item {
        min-width: 0;
        width: fit-content;
        max-width: none;
        flex: 0 1 auto;
    }
`);

const VimCommandBar = injectStyle("vim-command-bar", k => `
    ${k} { min-width: 0; font-size: 12px; white-space: nowrap; }
    ${k} > span:first-child, ${k} > span:last-child { display: none; }
    ${k} input {
        width: 180px;
        padding: 0;
        outline: none;
        font: inherit;
        color: var(--textPrimary);
        background: transparent;
        border: none;
    }
`);

const StatusModeBadge = injectStyle("editor-status-mode-badge", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        height: 24px;
        padding: 0 8px;
        border-radius: 999px;
        background: color-mix(in srgb, var(--textPrimary) 12%, transparent);
        font-size: 12px;
        white-space: nowrap;
    }
`);
