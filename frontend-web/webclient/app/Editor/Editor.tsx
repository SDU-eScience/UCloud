import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {editor} from "monaco-editor";
import {Uri} from "monaco-editor";
import {injectStyle} from "@/Unstyled";
import {Box, Flex, FtIcon, Image, Text} from "@/ui-components";
import {fileName, getParentPath, pathComponents} from "@/Utilities/FileUtilities";
import {capitalized, copyToClipboard, createKeyboardShortcut, errorMessageOrDefault, extensionFromPath, extensionType, getLanguageList, languageFromExtension} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {usePrettyFilePath} from "@/Files/FilePath";
import {ActionEntry, ActionMenu} from "@/ui-components/Actions";
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import {EditorSidebarNode, FileTree, VirtualFile} from "@/Files/FileTree";
import {noopCall} from "@/Authentication/DataHook";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useBeforeUnload} from "react-router-dom";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import {CodeEditor} from "./CodeEditor";
import {useMonaco} from "./Monaco";
import {allowEditDialog} from "./EditorSettings";
export {getMonaco, useMonaco, jinja2monarchTokens} from "./Monaco";
export {allowEditing} from "./EditorSettings";
import {addStandardDialog, addStandardInputDialog} from "@/UtilityComponents";
import {FileWriteFailure, WriteFailureEvent} from "@/Files/Uploader";
import ITextModel = editor.ITextModel;
import EndOfLineSequence = editor.EndOfLineSequence;
import {sendFailureNotification, sendInformationNotification} from "@/Notifications";
import {TabStrip} from "@/ui-components/TabStrip";
import {IconButton} from "@/ui-components/IconButton";
import {CSSVarCurrentSidebarStickyWidth} from "@/ui-components/List";
import {VirtualizedTreeApi} from "@/ui-components/VirtualizedTree";

export interface Vfs {
    isReal(): boolean;

    listFiles(path: string): Promise<VirtualFile[]>;

    readFile(path: string): Promise<string | Uint8Array>;

    writeFile(path: string): Promise<void>;

    // Notifies the VFS that a file is dirty, but do not synchronize it yet.
    setDirtyFileContent(path: string, content: string): void;
}

export interface EditorState {
    vfs: Vfs;
    title: string;
    sidebar: EditorSidebar;
    cachedFiles: Record<string, string | Uint8Array>;
    viewState: Record<string, editor.ICodeEditorViewState>;
    currentPath: string;
}

export interface EditorSidebar {
    root: EditorSidebarNode;
}

export interface EditorActionCreate {
    type: "EditorActionCreate";
    vfs: Vfs;
    title: string;
}

export interface EditorActionUpdateTitle {
    type: "EditorActionUpdateTitle";
    title: string;
}

export interface EditorActionOpenFile {
    type: "EditorActionOpenFile";
    path: string
}

export interface EditorActionFilesLoaded {
    type: "EditorActionFilesLoaded";
    path: string;
    files: VirtualFile[];
}

export interface EditorActionSaveState {
    type: "EditorActionSaveState";
    editorState: editor.ICodeEditorViewState | null;
    newPath: string;
    oldContent: string;
}

export type EditorAction =
    | EditorActionCreate
    | EditorActionUpdateTitle
    | EditorActionOpenFile
    | EditorActionFilesLoaded
    | EditorActionSaveState
    ;

function defaultEditor(vfs: Vfs, title: string, initialFolder: string, initialFile?: string): EditorState {
    return {
        sidebar: {
            root: {
                file: {
                    absolutePath: initialFolder,
                    isDirectory: true,
                },
                children: [],
            }
        },
        currentPath: initialFile ?? initialFolder,
        viewState: {},
        cachedFiles: {},
        title,
        vfs,
    }
}

function findNode(root: EditorSidebarNode, path: string): EditorSidebarNode | null {
    const components = pathComponents(path.replace(root.file.absolutePath, ""));
    if (components.length === 0) return root;

    let currentNode = root;
    let currentPath = root.file.absolutePath + "/";
    for (let i = 0; i < components.length; i++) {
        currentPath += components[i];
        const node = currentNode.children.find(it => [currentPath, path].includes(it.file.absolutePath));
        if (!node) return null;
        currentNode = node;
        if (currentNode.file.absolutePath === path) break;
        // Note(Jonas): If we haven't found the wanted file or directory, the current path must be a directory, and we add the slash
        currentPath += "/";
    }
    return currentNode;
}

function findOrAppendNodeForMutation(root: EditorSidebarNode, path: string): [EditorSidebarNode, EditorSidebarNode] {
    const components = pathComponents(path);
    let leafNode: EditorSidebarNode;

    if (path === "/" || path === "") {
        const newRoot = {...root};
        return [newRoot, newRoot];
    }

    function traverseAndCopy(currentNode: EditorSidebarNode, i: number): EditorSidebarNode {
        const selfCopy = {...currentNode};
        if (i < components.length) {
            const newPath = "/" + components.slice(0, i + 1).join("/");
            let foundChild = currentNode.children.find(it => it.file.absolutePath === newPath);
            if (!foundChild) {
                foundChild = {
                    file: {absolutePath: newPath, isDirectory: true},
                    children: []
                };
            }

            const copied = traverseAndCopy(foundChild, i + 1);
            selfCopy.children = [
                ...selfCopy.children.filter(it => it.file.absolutePath !== newPath),
                copied,
            ];

            selfCopy.children.sort((a, b) => virtualFileSort(a.file, b.file));
        } else {
            leafNode = selfCopy;
        }

        return selfCopy;
    }

    /* Note(Jonas): pathComponents length is to see how much needs to be skipped, as it is considered the current root. */
    /* A depth/i-value of 0 means it starts at the root (/, ). */
    const newRoot = traverseAndCopy(root, pathComponents(root.file.absolutePath).length);
    return [newRoot, leafNode!];
}

function singleEditorReducer(state: EditorState, action: EditorAction): EditorState {

    switch (action.type) {
        case "EditorActionCreate": {
            // NOTE(Dan): Handled by the root reducer, should not be called like this.
            return state;
        }

        case "EditorActionFilesLoaded": {
            const [newRoot, leaf] = findOrAppendNodeForMutation(state.sidebar.root, action.path);
            leaf.childrenLoaded = true;
            leaf.children = action.files.map(it => {
                const existing = leaf.children.find(child => child.file.absolutePath === it.absolutePath);
                return existing ?? {
                    file: it,
                    children: [],
                };
            });
            leaf.children.sort((a, b) => virtualFileSort(a.file, b.file));

            return {
                ...state,
                sidebar: {
                    root: newRoot,
                }
            };
        }

        case "EditorActionUpdateTitle": {
            return {
                ...state,
                title: action.title,
            };
        }

        case "EditorActionOpenFile": {
            return {
                ...state,
                currentPath: action.path
            };
        }

        case "EditorActionSaveState": {
            const newViewState = {...state.viewState};
            if (action.editorState) newViewState[state.currentPath] = action.editorState;

            const newCachedFiles = {...state.cachedFiles};
            newCachedFiles[state.currentPath] = action.oldContent;

            return {
                ...state,
                currentPath: action.newPath,
                viewState: newViewState,
                cachedFiles: newCachedFiles,
            };
        }
    }
}

function toDisplayName(name: string): string {
    switch (name) {
        case "typescript":
            return "TypeScript"
        case "javascript":
            return "JavaScript";
        case "coffeescript":
            return "CoffeeScript";
        case "freemarker2":
            return "FreeMarker2";
        case "csharp":
            return "C#";
        case "objective-c":
            return "Objective-C";
        case "restructuredtext":
            return "reStructuredText";
        case "html":
        case "xml":
        case "yaml":
        case "css":
        case "abap":
        case "ecl":
        case "vb":
        case "sql":
        case "json":
        case "wgsl":
            return name.toLocaleUpperCase();
        case "bat":
            return name;
    }
    return capitalized(name);
}

const EditorClass = injectStyle("editor", k => `
    ${k} {
        display: flex;
        width: 100%;
        max-width: 100%;
        height: 100%;
        min-height: 0;
        box-sizing: border-box;
        padding: 8px 8px 44px;
        gap: 8px;
        overflow: hidden;
        background: var(--backgroundCard);
    }
    
    ${k} > .main-content {
        display: flex;
        flex-direction: column;
        flex: 1 1 auto;
        width: 0;
        height: 100%;
        min-height: 0;
        min-width: 600px;
        overflow: hidden;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: var(--backgroundDefault);
    }
    
    ${k} .title-bar-code,
    ${k} .title-bar {
        display: flex;
        align-items: center;
        height: 36px;
        width: 100%;
        flex-shrink: 0;
    }
    
    ${k} .panels {
        display: flex;
        flex: 1 1 auto;
        width: 100%;
        height: 0;
        min-height: 0;
        margin-top: 10px;
        overflow: hidden;
    }
    
    ${k} .panels > div > .code {
        flex-grow: 1;
        height: 100%;
    }
`);

const EditorLoadingSidebar = injectStyle("editor-loading-sidebar", k => `
    ${k} {
        width: 250px;
        max-width: 250px;
        height: 100%;
        flex: 0 0 250px;
        box-sizing: border-box;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: var(--backgroundDefault);
    }
`);

const EditorLoadingContent = injectStyle("editor-loading-content", k => `
    ${k} {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 100%;
        height: 100%;
    }
`);

export function EditorLoadingState({children}: React.PropsWithChildren): React.ReactNode {
    return <div className={EditorClass}>
        <div className={EditorLoadingSidebar} />
        <div className="main-content">
            <div className="title-bar-code" style={{boxSizing: "border-box", minWidth: "400px", padding: "0 8px", width: "100%"}} />
            <div className="panels">
                <div className={EditorLoadingContent}>{children}</div>
            </div>
        </div>
    </div>;
}

type EditorEngine =
    | "monaco";

export interface EditorApi {
    path: string;
    notifyDirtyBuffer: () => Promise<void>;
    openFile: (path: string) => void;
    invalidateTree: (path: string) => Promise<void>;
    onFileSaved: (path: string) => RevertSaveFunction;
    onFileDeleted: (path: string) => void;
}

const SETTINGS_PATH = "xXx__/SETTINGS\\__xXx";

const SPECIAL_PATHS = [SETTINGS_PATH, "", "/"];

export const Editor: React.FunctionComponent<{
    vfs: Vfs;
    title: string;
    initialFilePath?: string;
    initialFolderPath: string;
    toolbarBeforeSettings?: React.ReactNode;
    toolbar?: React.ReactNode;
    statusBar?: React.ReactNode;
    apiRef?: React.RefObject<EditorApi | null>;
    customContent?: React.ReactNode;
    showCustomContent?: boolean;
    onOpenFile?: (path: string, content: string | Uint8Array) => void;
    actions?: (file?: VirtualFile) => ActionEntry<VirtualFile, null>[];
    help?: React.ReactNode;
    fileHeaderOperations?: React.ReactNode;
    renamingFile?: string;
    promptSaveOnNavigate?: boolean;
    onRename?: (args: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}) => Promise<boolean>;
    onRequestSave: (path: string) => Promise<void>;
    readOnly: boolean;
    dirtyFileCountRef: React.RefObject<number>;
    isModal?: boolean;
}> = props => {
    const help = props.help ?? <></>;
    const [activeSyntax, setActiveSyntax] = React.useState("");
    const savedAtAltVersionId = React.useRef<Record<string, number>>({});
    const [languageList, setLanguageList] = React.useState(getLanguageList().map(l => ({language: l.language, displayName: toDisplayName(l.language)})));
    const [engine, setEngine] = useState<EditorEngine>(localStorage.getItem("editor-engine") as EditorEngine ?? "monaco");
    const [state, dispatch] = useReducer(singleEditorReducer, 0, () => defaultEditor(props.vfs, props.title, props.initialFolderPath, props.initialFilePath));
    const editorRoot = useRef<HTMLDivElement>(null);
    const monacoInstance = useMonaco(engine === "monaco");
    const [editor, setEditor] = useState<IStandaloneCodeEditor | null>(null);
    const [readOnlyMode, setReadOnlyMode] = useState(props.readOnly === true);
    const [sidebarOpen, setSidebarOpen] = useState(true);
    const monacoRef = useRef<any>(null);
    const ownedModels = useRef(new Map<string, ITextModel>());
    const modelScope = React.useId();
    const [tabs, setTabs] = useState<{open: string[], closed: string[]}>({
        open: props.initialFilePath ? [state.currentPath] : [],
        closed: [],
    });

    const [dirtyFiles, setDirtyFiles] = React.useState<Set<string>>(new Set());

    const prettyPath = usePrettyFilePath(state.currentPath, !props.vfs.isReal());

    if (!props.isModal) {
        if (state.currentPath === SETTINGS_PATH) {
            usePage("Settings", SidebarTabId.FILES);
        } else if (state.currentPath === "") {
            usePage("Preview", SidebarTabId.FILES);
        } else {
            usePage(fileName(prettyPath), SidebarTabId.FILES);
        }
    }

    const disposeModels = React.useCallback(() => {
        for (const model of ownedModels.current.values()) model.dispose();
        ownedModels.current.clear();
    }, []);

    const dirtyFilesRef = useRef(dirtyFiles);
    dirtyFilesRef.current = dirtyFiles;
    React.useEffect(() => {
        if (props.promptSaveOnNavigate) {
            return () => {
                if (dirtyFilesRef.current.size) {
                    const pathsAndContent = [...dirtyFilesRef.current].map(it => ({path: it, content: getModelFromEditor(it)?.getValue()}));
                    setTimeout(() => {
                        addStandardDialog({
                            title: "You have unsaved changes",
                            message: "This will save contents of files: ".concat(pathsAndContent.map(it => fileName(it.path)).join(", ")),
                            onConfirm() {
                                for (const p of pathsAndContent) {
                                    if (p.content == null) {
                                        sendFailureNotification(`${p.path} failed to save`);
                                        continue;
                                    }
                                    props.vfs.setDirtyFileContent(p.path, p.content);
                                    props.vfs.writeFile(p.path);

                                    const onFileWriteFailure = (e: WriteFailureEvent) => {
                                        const failedUpload = e.detail.find(it => it.targetPath + it.name === p.path);
                                        if (failedUpload) {
                                            sendFailureNotification(failedUpload.error ?? "Upload for file " + fileName(failedUpload.name) + " failed.");
                                        }
                                        window.removeEventListener(FileWriteFailure, {handleEvent: onFileWriteFailure})
                                    }

                                    window.addEventListener(FileWriteFailure, {handleEvent: onFileWriteFailure})
                                }
                            },
                            confirmText: "Save changes",
                            cancelText: "Dismiss changes"
                        })
                    }, 0);
                }
            }
        }
        return;
    }, []);

    const [tabContextMenu, setTabContextMenu] = useState<{
        title?: string;
        left: number;
        top: number;
    } | null>(null);
    const anyTabOpen = tabs.open.length > 0;
    const isSettingsOpen = state.currentPath === SETTINGS_PATH && anyTabOpen;
    const specialPageOpen = isSettingsOpen;

    // NOTE(Dan): This code is quite ref heavy given that the components we are controlling are very much the
    // opposite of reactive. There isn't much we can do about this.
    const engineRef = useRef<EditorEngine>("monaco");
    const stateRef = useRef<EditorState>(null);
    const tree = useRef<VirtualizedTreeApi | null>(null);

    const editorRef = useRef<IStandaloneCodeEditor | null>(null);
    const showingCustomContent = useRef<boolean>(props.showCustomContent === true);

    useEffect(() => {
        showingCustomContent.current = props.showCustomContent === true;
    }, [props.showCustomContent]);

    useEffect(() => {
        dispatch({type: "EditorActionUpdateTitle", title: props.title});
    }, [props.title]);

    useEffect(() => {
        engineRef.current = engine;
        localStorage.setItem("editor-engine", engine);
        if (engine !== "monaco" && engine !== "vim") setEngine("monaco");
    }, [engine]);

    useEffect(() => {
        editorRef.current = editor;
    }, [editor]);

    useEffect(() => {
        stateRef.current = state;
    }, [state]);

    useEffect(() => {
        monacoRef.current = monacoInstance;
        setLanguageList(getLanguageList().map(l => ({language: l.language, displayName: toDisplayName(l.language)})));
    }, [monacoInstance]);

    useEffect(() => {
        const ref = props.apiRef;
        if (!ref || !ref.current) return;
        ref.current.path = state.currentPath;
    }, [props.apiRef, state.currentPath]);

    const didUnmount = useDidUnmount();
    const reloadTree = useRef<((path: string) => Promise<void>) | null>(null);

    const reloadBuffer = useCallback((name: string, content: string, syntax: string) => {
        const editor = editorRef.current;
        const engine = engineRef.current;
        switch (engine) {
            case "monaco": {
                if (!editor) return;
                const existingModel = getModelFromEditor(name);
                if (!existingModel) {
                    const model = monacoRef.current?.editor?.createModel(content, syntax, Uri.from({scheme: "ucloud-file-editor", authority: modelScope, path: name})) as ITextModel;
                    ownedModels.current.set(name, model);
                    model.setEOL(EndOfLineSequence.LF);
                    model.onDidChangeContent(e => {
                        setDirtyFiles(f => {
                            const altId = model.getAlternativeVersionId();
                            if (altId === (savedAtAltVersionId.current[name] ?? 1)) {
                                f.delete(name);
                                return new Set([...f]);
                            } else {
                                return new Set([...f, name])
                            }
                        });
                    });
                    editor.setModel(model);
                } else if (editor.getModel() !== existingModel) {
                    editor.setModel(existingModel);
                }
                break;
            }
        }
    }, []);

    React.useEffect(() => {
        return disposeModels;
    }, []);

    const readBuffer = useCallback((): Promise<string> => {
        const editor = editorRef.current;
        const engine = engineRef.current;
        switch (engine) {
            case "monaco": {
                const value = editor?.getModel()?.getValue();
                if (value == null) return Promise.reject();
                return Promise.resolve(value);
            }
        }
    }, []);

    const getModelFromEditor = React.useCallback((path: string): editor.ITextModel | null => {
        return ownedModels.current.get(path) ?? null;
    }, []);

    const openFile = useCallback(async (path: string, saveState: boolean): Promise<boolean> => {
        if (SPECIAL_PATHS.includes(path)) {
            dispatch({type: "EditorActionOpenFile", path});
            return false;
        }

        const oldPath = state.currentPath;
        const cachedContent = state.cachedFiles[path] ?? getModelFromEditor(path)?.getValue();
        const dataPromise =
            cachedContent !== undefined ?
                Promise.resolve(cachedContent) :
                props.vfs.readFile(path);

        const file = findNode(state.sidebar.root, path)?.file;
        const syntaxExtension = file?.requestedSyntax;
        const syntax = languageFromExtension(syntaxExtension ?? extensionFromPath(path));

        try {
            const content = await dataPromise;

            if (!cachedContent) { // Note(Jonas): Cache content, if fetched from backend
                state.cachedFiles[path] = content;
            }

            props.onOpenFile?.(path, content);
            const editor = editorRef.current;
            const engine = engineRef.current;

            if (didUnmount.current) return true;

            if (!showingCustomContent.current) {
                const oldModel = getModelFromEditor(oldPath);
                const oldContent = oldModel?.getValue() ?? state.cachedFiles[oldPath];
                const canSaveOldBuffer = !SPECIAL_PATHS.includes(oldPath) && saveState && typeof oldContent === "string";
                if (canSaveOldBuffer) {
                    let editorState: editor.ICodeEditorViewState | null = null;
                    if (editor && oldModel && editor.getModel() === oldModel) {
                        editorState = editor.saveViewState();
                    }

                    props.vfs.setDirtyFileContent(oldPath, oldContent);
                    dispatch({type: "EditorActionSaveState", editorState, oldContent, newPath: path});
                } else {
                    dispatch({type: "EditorActionOpenFile", path});
                }
            } else {
                dispatch({type: "EditorActionOpenFile", path});
            }

            if (typeof content === "string") {
                reloadBuffer(path, content, syntax);
                const restoredState = state.viewState[path];
                if (editor && restoredState) {
                    editor.restoreViewState(restoredState);
                }

                // NOTE(Dan): openFile on the initial path must be called via a setTimeout to handle this case.
                if (engine === "monaco" && editor == null) return false;

                tree.current?.deactivate?.();
                editor?.focus?.();

                const monaco = monacoRef.current;
                if (syntax && monaco && editor) {
                    const model = editor.getModel();
                    if (model && model.getLanguageId() !== syntax) monaco.editor.setModelLanguage(model, syntax);
                    setActiveSyntax(syntax);
                }
            }

            return true;
        } catch (error) {
            sendFailureNotification(errorMessageOrDefault(error, "Failed to fetch file"));
            return true; // What does true or false mean in this context?
        }
    }, [state, props.vfs, dispatch, reloadBuffer, readBuffer, props.onOpenFile, state.sidebar.root, state.cachedFiles]);

    useEffect(() => {
        const listener = (ev: KeyboardEvent) => {
            if (ev.defaultPrevented) return;
            if (ev.code === "Escape") {
                ev.preventDefault();
                return;
            }
        };

        window.addEventListener("keydown", listener);
        return () => {
            window.removeEventListener("keydown", listener);
        }
    }, []);

    const saveBufferIfNeeded = useCallback(async () => {
        const editor = editorRef.current;
        const engine = engineRef.current;
        const state = stateRef.current!;
        const currentPath = state.currentPath;

        if (SPECIAL_PATHS.includes(currentPath)) return;

        if (engine === "monaco" && editor == null) return;

        const cachedValue = state.cachedFiles[currentPath];
        /* Note(Jonas): If cached value isn't a string, it's not relevant to read the buffer */
        const res = typeof cachedValue !== "string" ? cachedValue : await readBuffer();
        if (didUnmount.current) return;


        props.onOpenFile?.(currentPath, res);
        /* Notes(Jonas): Only save state if content of cachedValue/buffer is a string */
        if (typeof res === "string") {
            props.vfs.setDirtyFileContent(currentPath, res);
            dispatch({type: "EditorActionSaveState", editorState: null, oldContent: res, newPath: currentPath});
        }
    }, [props.onOpenFile]);

    const loadDirectory = useCallback(async (folder: string): Promise<VirtualFile[]> => {
        const files = await props.vfs.listFiles(folder);
        if (!didUnmount.current) dispatch({type: "EditorActionFilesLoaded", path: folder, files});
        return files;
    }, [props.vfs]);

    const invalidateTree = useCallback(async (folder: string): Promise<void> => {
        await reloadTree.current?.(folder);
    }, []);

    const onFileSaved = React.useCallback((path: string): RevertSaveFunction => {
        const model = getModelFromEditor(path);
        let oldSavedAtVersionId = savedAtAltVersionId.current[path];
        let hadAsDirty = false;
        if (model) {
            savedAtAltVersionId.current[path] = model.getAlternativeVersionId();
            setDirtyFiles(dirtyFiles => {
                if (dirtyFiles.has(path)) {
                    dirtyFiles.delete(path);
                    hadAsDirty = true;
                }
                return new Set([...dirtyFiles]);
            });
        }

        return () => {
            savedAtAltVersionId.current[path] = oldSavedAtVersionId;
            if (hadAsDirty) {
                setDirtyFiles(dirtyFiles => {
                    return new Set([...dirtyFiles, path]);
                });
            }
        }
    }, []);

    React.useEffect(() => {
        props.dirtyFileCountRef.current = dirtyFiles.size;
    }, [dirtyFiles]);

    const api: EditorApi = useMemo(() => {
        return {
            path: state.currentPath,
            notifyDirtyBuffer: saveBufferIfNeeded,
            openFile: path => {
                openTab(path)
            },
            invalidateTree,
            onFileSaved,
            onFileDeleted(path: string) {
                setTabs(tabs => {
                    const withRemovedEntry = tabs.open.filter(it => it !== path);
                    if (path === this.path) {
                        const idx = tabs.open.findIndex(it => it === path);
                        dispatch({type: "EditorActionOpenFile", path: withRemovedEntry.at(idx - 1) ?? ""});
                    }
                    return ({
                        open: withRemovedEntry,
                        closed: tabs.closed
                    })
                })
            }
        }
    }, []);


    useEffect(() => {
        if (props.apiRef) props.apiRef.current = api;
    }, [api, props.apiRef]);

    useEffect(() => {
        invalidateTree(props.initialFolderPath);
    }, []);

    const initialOpenCompleted = useRef(false);

    useLayoutEffect(() => {
        if (initialOpenCompleted.current) return;
        if (!props.initialFilePath) {
            initialOpenCompleted.current = true;
            return;
        }

        // NOTE(Dan): This timer is needed to make sure that if the file opens faster than the engine can initialize
        // then we do reload the file. See the branch when returns early in openFile.
        let timer = -1;
        const fn = async () => {
            const res = await openFile(state.currentPath, false);
            if (res) initialOpenCompleted.current = true;
            if (!res) timer = window.setTimeout(fn, 50);
        };
        timer = window.setTimeout(fn, 50);

        return () => {
            window.clearTimeout(timer);
        };
    }, [state.sidebar.root]);

    const toggleReadOnlyMode = React.useCallback(() => {
        const currentEditor = editorRef.current;
        if (!currentEditor) return;

        if (readOnlyMode) {
            if (props.readOnly) {
                allowEditDialog(currentEditor, () => setReadOnlyMode(false));
            } else {
                currentEditor.updateOptions({readOnly: false});
                setReadOnlyMode(false);
            }
        } else {
            currentEditor.updateOptions({readOnly: true});
            setReadOnlyMode(true);
        }
    }, [props.readOnly, readOnlyMode]);

    useLayoutEffect(() => {
        if (!props.showCustomContent && editor) {
            editor.layout();
            editor.render(true);
            editor.focus();
        }
    }, [props.showCustomContent]);

    const onKeyDown: React.KeyboardEventHandler = useCallback(ev => {
        if (ev.code === "Escape") {
            ev.preventDefault();
            ev.stopPropagation();
        }
    }, []);

    useEffect(() => {
        const listener = (event: KeyboardEvent) => {
            if (!event.altKey || (!event.ctrlKey && !event.metaKey) || event.shiftKey) return;

            if (event.code === "Digit1") {
                event.preventDefault();
                event.stopPropagation();
                if (tree.current?.isActive()) {
                    tree.current.deactivate();
                    setSidebarOpen(false);
                } else if (!sidebarOpen) {
                    setSidebarOpen(true);
                    window.requestAnimationFrame(() => tree.current?.activate());
                } else {
                    tree.current?.activate();
                }
            } else if (event.code === "Digit2") {
                event.preventDefault();
                event.stopPropagation();
                editorRef.current?.focus();
            }
        };

        window.addEventListener("keydown", listener, true);
        return () => window.removeEventListener("keydown", listener, true);
    }, [sidebarOpen]);

    const toggleSettings = useCallback(() => {
        saveBufferIfNeeded().then(() => {
            setTabs(tabs => {
                if (tabs.open.includes(SETTINGS_PATH)) {
                    return tabs;
                } else return {open: [...tabs.open, SETTINGS_PATH], closed: tabs.closed};
            })
            dispatch({type: "EditorActionOpenFile", path: SETTINGS_PATH})
        });
    }, []);

    const openTab = React.useCallback(async (path: string) => {
        if (state.currentPath === path) return;
        await openFile(path, true);
        const fileWasFetched = getModelFromEditor(path) != null || state.cachedFiles[path] != null;
        if (fileWasFetched) {
            setTabs(tabs => {
                if (tabs.open.includes(path)) {
                    return tabs;
                } else return {open: [...tabs.open, path], closed: tabs.closed};
            });
        }
    }, [state.currentPath]);

    const closeTab = useCallback(async (path: string, index: number) => {
        const result = tabs.open.filter(tabTitle => tabTitle !== path);
        if (state.currentPath === path) {
            const preceedingPath = result.at(index - 1);
            if (preceedingPath) {
                await openFile(preceedingPath, true);
            } else {
                dispatch({type: "EditorActionOpenFile", path: ""});
            }
        }

        const closed = tabs.closed;
        if (!closed.includes(path)) closed.push(path);

        if (props.vfs.isReal()) {
            getModelFromEditor(path)?.dispose();
            ownedModels.current.delete(path);
            delete savedAtAltVersionId.current[path];
            setDirtyFiles(f => {
                f.delete(path);
                return f;
            })
        } else {
            props.vfs.setDirtyFileContent(path, getModelFromEditor(path)?.getValue() ?? "")
        }

        setTabs({open: result, closed});
    }, [state.currentPath, tabs]);

    const openTabActionMenu = useRef<(x: number, y: number) => void>(noopCall);
    const openTabContextMenu = React.useCallback((title: string | undefined, position: {x: number; y: number;}) => {
        setTabContextMenu({title, left: position.x, top: position.y});
    }, []);

    useLayoutEffect(() => {
        if (!tabContextMenu) return;
        openTabActionMenu.current(tabContextMenu.left, tabContextMenu.top);
        setTabContextMenu(null);
    }, [tabContextMenu]);

    const actions = tabActions(tabContextMenu?.title, setTabs, openTab, tabs, dirtyFiles, state.currentPath);

    useBeforeUnload((e: BeforeUnloadEvent): BeforeUnloadEvent => {
        const anyDirty = dirtyFiles.size > 0;
        if (anyDirty) {
            // Note(Jonas): Both should be done for best compatibility:
            // https://developer.mozilla.org/en-US/docs/Web/API/BeforeUnloadEvent/returnValue
            e.preventDefault();
            e.returnValue = "truthy value";
            return e;
        }
        return e;
    });

    const onRename = React.useCallback(async (args: {newAbsolutePath: string; oldAbsolutePath: string; cancel: boolean;}) => {
        if (!props.onRename) return;

        const fileUpdated = await props.onRename(args);

        if (fileUpdated) {

            setDirtyFiles(files => {
                if (files.has(args.oldAbsolutePath)) {
                    files.delete(args.oldAbsolutePath);
                    files.add(args.newAbsolutePath);
                    return new Set([...files]);
                }
                return files;
            });

            savedAtAltVersionId.current[args.newAbsolutePath] = savedAtAltVersionId.current[args.oldAbsolutePath];
            delete savedAtAltVersionId.current[args.oldAbsolutePath];


            setTabs(tabs => {
                const openTabs = tabs.open;
                const closedTabs = tabs.closed;

                const openIdx = openTabs.findIndex(it => it === args.oldAbsolutePath);
                if (openIdx !== -1) openTabs[openIdx] = args.newAbsolutePath;

                const closedIdx = closedTabs.findIndex(it => it === args.oldAbsolutePath);
                if (closedIdx !== -1) closedTabs[closedIdx] = args.newAbsolutePath;

                return {
                    open: openTabs,
                    closed: closedTabs,
                }
            });
        }


        const oldModel = getModelFromEditor(args.oldAbsolutePath);
        const editor = editorRef.current;
        if (oldModel && editor) {
            /* Note(Jonas): There's no way to rename and existing model with a new uri, which is exactly what we would want here.
                So we copy the contents and langauge id, but we lose undo/redo-stack, sadly.
                https://github.com/microsoft/monaco-editor/discussions/3751
            */
            const newModel = monacoRef.current?.editor?.createModel(oldModel.getValue(), oldModel.getLanguageId(), Uri.from({scheme: "ucloud-file-editor", authority: modelScope, path: args.newAbsolutePath}));
            ownedModels.current.delete(args.oldAbsolutePath);
            ownedModels.current.set(args.newAbsolutePath, newModel);
            if (editor.getModel() === oldModel) {
                editor.setModel(newModel);
                openTab(args.newAbsolutePath);
            }
            oldModel.dispose();
        }

        invalidateTree(getParentPath(args.newAbsolutePath));
    }, []);

    const setModelLanguage = React.useCallback((element: {
        language: string;
        displayName: string;
    }) => {
        const monaco = monacoRef.current;
        const editor = editorRef.current;
        if (monaco && editor) {
            monaco.editor.setModelLanguage(editor.getModel(), element.language);
            setActiveSyntax(element.language);
        }
    }, [state.currentPath]);

    const selectedSynax = React.useMemo(() => {
        return {language: activeSyntax, displayName: toDisplayName(activeSyntax)};
    }, [activeSyntax]);

    function doClose(t: string, index: number) {
        if (dirtyFiles.has(t) && props.vfs.isReal()) {
            addStandardDialog({
                title: "Save before closing?",
                message: "The changes made to this file has not been saved. Save before closing?",
                confirmText: "Save",
                async onConfirm() {
                    await props.onRequestSave(t);
                    closeTab(t, index);
                },
                cancelText: "Don't save",
                onCancel() {
                    closeTab(t, index);
                }
            });
        } else {
            closeTab(t, index);
        }
    }

    // Current path === "", can we use this as empty/scratch space, or is this in use for Scripts/Workflows
    const showEditorHelp = tabs.open.length === 0;

    const isMarkdown = extensionType(extensionFromPath(state.currentPath)) === "markdown";
    const toolbarBeforeSettings = (tabs.open.length === 0 || isSettingsOpen || props.showCustomContent) && !isMarkdown ? null : props.toolbarBeforeSettings;

    return <div ref={editorRoot} className={EditorClass} onKeyDown={onKeyDown}>
        <FileTree
            basePath={props.initialFolderPath}
            tree={tree}
            onFileActivated={file => openTab(file.absolutePath)}
            loadEntries={loadDirectory}
            reloadRef={reloadTree}
            width="250px"
            canResize
            visible={sidebarOpen}
            initialFilePath={props.initialFilePath}
            selectedPath={state.currentPath}
            fileHeaderOperations={props.fileHeaderOperations}
            actions={props.actions}
            renamingFile={props.renamingFile}
            onRename={onRename}
        />
        <div className={"main-content"}>
            <div className={"title-bar-code"} style={{boxSizing: "border-box", minWidth: "400px", padding: "0 8px", width: "100%"}}>
                <div onContextMenu={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    openTabContextMenu(undefined, {x: e.clientX, y: e.clientY});
                }} style={{display: "flex", flex: "1 1 auto", height: "100%", minWidth: 0}}>
                    <TabStrip
                        items={tabs.open.map(path => {
                            const isSettings = path === SETTINGS_PATH;
                            const isDirty = dirtyFiles.has(path) && props.vfs.isReal();
                            return {
                                id: path,
                                title: <EditorTabLabel path={path} />,
                                tooltip: <EditorTabLabel path={path} fullPath />,
                                icon: isSettings ? undefined : <FullpathFileLanguageIcon filePath={path} size="14px" />,
                                closeIcon: isDirty ? "circle" as const : "close" as const,
                                closeIconOnHover: "close" as const,
                                closeLabel: `Close ${fileName(path)}`,
                            };
                        })}
                        activeId={state.currentPath}
                        slim
                        autoSize={false}
                        shortcutScope={editorRoot}
                        allowUnfocusedShortcuts
                        onActivate={path => openTab(path)}
                        onClose={path => doClose(path, tabs.open.indexOf(path))}
                        onContextMenu={(path, position) => openTabContextMenu(path, position)}
                        onReorder={open => setTabs(tabs => {
                            if (open.length !== tabs.open.length || open.some(path => !tabs.open.includes(path))) return tabs;
                            return {...tabs, open};
                        })}
                    />
                    <ActionMenu
                        actions={actions}
                        openFnRef={openTabActionMenu}
                        selected={[]}
                        callbacks={null}
                        trigger={null}
                    />
                </div>
                {toolbarBeforeSettings || props.toolbar ? <Flex className={HeaderActions} alignItems={"center"}>
                    {toolbarBeforeSettings}
                    {props.toolbar}
                </Flex> : null}
            </div>
            <div className={"panels"}>
                <CodeEditor
                    documentId={state.currentPath}
                    manageModel={false}
                    showToolbar={false}
                    readOnly={readOnlyMode}
                    settingsOpen={isSettingsOpen}
                    onSettingsToggle={toggleSettings}
                    onReadOnlyChange={setReadOnlyMode}
                    onReady={instance => {
                        editorRef.current = instance;
                        setEditor(instance);
                    }}
                    onFocus={() => tree.current?.deactivate?.()}
                    onSave={async () => {
                        if (SPECIAL_PATHS.includes(state.currentPath)) return;
                        await saveBufferIfNeeded();
                        await props.onRequestSave(state.currentPath);
                        onFileSaved(state.currentPath);
                    }}
                    onClose={() => doClose(state.currentPath, tabs.open.indexOf(state.currentPath))}
                    onOpenFile={path => openTab(props.initialFolderPath + "/" + path)}
                    showContent={props.showCustomContent || showEditorHelp}
                    statusBarClassName={StatusBarWrapper}
                    statusBarStart={<IconButton tooltip={`Toggle sidebar (${createKeyboardShortcut("1", ["ctrl", "alt"])})`} onClick={() => setSidebarOpen(open => !open)} icon="sidebar" color="textPrimary" noDefaultFill />}
                    statusBarEnd={<>
                        {tabs.open.length === 0 || specialPageOpen || props.customContent ? null : <>
                            <EditorCursorPosition editor={editor} currentPath={state.currentPath} />
                            <Box className={SyntaxSelector} width={"fit-content"}>
                                <RichSelect
                                    key={activeSyntax}
                                    items={languageList}
                                    keys={SyntaxSelectorKeys}
                                    FullRenderSelected={p => <Text px="8px" textAlign="end">{p.element?.displayName}</Text>}
                                    elementHeight={29}
                                    RenderRow={LanguageItem}
                                    selected={selectedSynax}
                                    onSelect={setModelLanguage}
                                />
                            </Box>
                            <IconButton
                                tooltip={readOnlyMode ? "Enable editing" : "Disable editing"}
                                onClick={toggleReadOnlyMode}
                                icon={readOnlyMode ? "heroLockClosed" : "heroLockOpen"}
                                color={readOnlyMode ? "warningMain" : "textPrimary"}
                            />
                        </>}
                        <Flex className={StatusIconGroup} alignItems="center">
                            {props.statusBar}
                            <IconButton tooltip="Settings" onClick={toggleSettings} icon="heroCog6Tooth" color="textPrimary" />
                        </Flex>
                    </>}
                >
                    {showEditorHelp ? help : null}
                    <div style={{
                        display: !specialPageOpen && props.showCustomContent && tabs.open.length > 0 ? "block" : "none",
                        width: "100%",
                        height: "100%",
                        maxHeight: "100%",
                        padding: "16px",
                        overflow: "auto",
                    }}>{props.customContent}</div>
                </CodeEditor>
            </div>
        </div>
    </div>;
};

const SyntaxSelector = injectStyle("editor-syntax-selector", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        height: 24px;
        border-radius: 999px;
        overflow: hidden;
    }

    ${k}:hover {
        background: var(--rowActive);
    }
`);

const HeaderActions = injectStyle("editor-header-actions", k => `
    ${k} {
        gap: 0;
        flex: 0 0 auto;
        margin-left: 16px;
    }

    ${k}:empty {
        display: none;
    }
`);

/* TODO(Jonas): Improve parameters this is... not good */
function tabActions(
    tabPath: string | undefined,
    setTabs: React.Dispatch<React.SetStateAction<{open: string[], closed: string[]}>>,
    openTab: (path: string) => void,
    tabs: {open: string[], closed: string[]},
    dirtyFiles: Set<string>,
    currentPath: string,
): ActionEntry<string, null>[] {
    const anyTabsOpen = tabs.open.length > 0;
    const anyTabsClosed = tabs.closed.length > 0;
    if (!tabPath) {
        return [{
            text: "Re-open closed tab",
            onClick: () => {
                setTabs(tabs => {
                    const tab = tabs.closed.pop();
                    if (tab) {
                        openTab(tab);
                        return {
                            open: [...tabs.open, tab],
                            closed: tabs.closed
                        };
                    }
                    return tabs;
                });
            },
            enabled: () => anyTabsClosed,
        }, {
            text: "Close all",
            onClick: () => {
                setTabs(tabs => {
                    return {
                        open: [],
                        closed: [...tabs.closed, ...tabs.open]
                    }
                });
            },
            enabled: () => anyTabsOpen,
        }];
    }

    return [{
        text: "Close tab",
        onClick: () => {
            setTabs(tabs => {
                if (currentPath === tabPath) {
                    const index = tabs.open.findIndex(it => it === tabPath);
                    if (index === -1) {
                        console.warn("No index found. This is weird. This shouldn't happen");
                        return tabs;
                    }
                    openTab(tabs.open.at(index - 1)!);
                };
                return {
                    open: tabs.open.filter(it => it !== tabPath),
                    closed: [...tabs.closed, tabPath]
                }
            });

        },
        enabled: () => true,
    }, {
        text: "Close others",
        onClick: () => {
            setTabs(tabs => {
                const remainder = tabs.open.filter(it => it !== tabPath);
                return {
                    open: [tabPath],
                    closed: [...tabs.closed, ...remainder]
                }
            });
            openTab(tabPath);
        },
        enabled: () => true,
    }, {
        text: "Close to the right",
        onClick: () => {

            setTabs(tabs => {
                const index = tabs.open.findIndex(it => it === tabPath);
                const activeIndex = tabs.open.findIndex(it => it === currentPath);

                if (activeIndex > index) {
                    openTab(tabPath);
                }

                if (index === -1) {
                    console.warn("No index found. This is weird. This shouldn't happen");
                    return tabs;
                }
                return {
                    open: tabs.open.slice(0, index + 1),
                    closed: [...tabs.closed, ...tabs.open.slice(index + 1)],
                };
            })
        },
        enabled: () => true,
    }, {
        text: "Close saved tabs",
        onClick: () => {
            const toClose: string[] = [];
            const toKeepOpen = tabs.open.filter(it => dirtyFiles.has(it));
            for (const openTab of tabs.open) {
                if (!dirtyFiles.has(openTab)) {
                    toClose.push(openTab);
                }
            }

            setTabs(t => {
                return {
                    open: toKeepOpen,
                    closed: t.closed.concat(toClose),
                }
            })
        },
        enabled: () => tabs.open.length > 0,
    }, {
        text: "Close all",
        onClick: () => {
            setTabs(tabs => {
                return {
                    open: [],
                    closed: [...tabs.closed, ...tabs.open]
                }
            });
        },
        enabled: () => true,
    }, "divider", {
        text: "Copy path to clipboard",
        onClick: () => {
            copyToClipboard(tabPath);
            sendInformationNotification("Path copied!")
        },
        enabled: () => true,
    }, {
        text: "Re-open closed tab",
        onClick: () => {
            setTabs(tabs => {
                const tab = tabs.closed.pop();
                if (tab) {
                    openTab(tab);
                    return {
                        open: [...tabs.open, tab],
                        closed: tabs.closed
                    };
                }
                return tabs;
            });
        },
        enabled: () => anyTabsClosed,
    }];
}

const SyntaxSelectorKeys = ["language" as const];

function EditorTabLabel({path, fullPath = false}: {path: string; fullPath?: boolean}): React.ReactNode {
    const isSettings = path === SETTINGS_PATH;
    const resolvedPath = usePrettyFilePath(path);
    const prettyFullPath = isSettings ? "Settings" : resolvedPath;

    if (fullPath) return prettyFullPath;
    if (isSettings) return "Editor settings";
    return fileName(prettyFullPath);
}

const StatusIconGroup = injectStyle("editor-status-icon-group", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 8px;
    }
`);

const StatusBarWrapper = injectStyle("status-bar-wrapper", k => `
    ${k} {
        position: fixed;
        bottom: calc(var(--termsize, 0px) + 4px);
        left: var(${CSSVarCurrentSidebarStickyWidth});
        z-index: 9;
        box-sizing: border-box;
        display: flex;
        width: calc(100vw - var(${CSSVarCurrentSidebarStickyWidth}));
        height: 36px;
        background-color: transparent;
        color: var(--textPrimary);
        padding: 1px 8px;
    }

    ${k} input {
        background: transparent;
        border: none;
    }
`);

const StatusPosition = injectStyle("editor-status-position", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        height: 24px;
        padding: 0 4px;
        border: 0;
        border-radius: 4px;
        background: transparent;
        color: inherit;
        cursor: pointer;
        font: inherit;
        font-family: var(--monospace, monospace);
        font-size: 12px;
        white-space: nowrap;
    }

    ${k}:hover {
        background: var(--rowActive);
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);

function EditorCursorPosition({editor, currentPath}: {editor: IStandaloneCodeEditor | null; currentPath: string}): React.ReactNode {
    const [position, setPosition] = useState({line: 1, column: 1});

    useEffect(() => {
        if (!editor) return;

        const updatePosition = () => {
            const nextPosition = editor.getPosition();
            if (!nextPosition) return;
            setPosition({line: nextPosition.lineNumber, column: nextPosition.column});
        };

        updatePosition();
        const listener = editor.onDidChangeCursorPosition(updatePosition);
        return () => listener.dispose();
    }, [editor, currentPath]);

    const openPositionDialog = () => {
        const model = editor?.getModel();
        if (!editor || !model) return;

        void addStandardInputDialog({
            title: "Go to line and column",
            help: <Text>Enter a line or a position in the format <code>line:column</code>.</Text>,
            placeholder: `${position.line}:${position.column}`,
            confirmText: "Go",
            validationFailureMessage: "Enter a valid line and column.",
            validator: value => parseEditorPosition(value) !== null,
        }).then(({result}) => {
            const nextPosition = parseEditorPosition(result);
            if (!nextPosition) return;

            if (nextPosition.lineNumber > model.getLineCount()) nextPosition.lineNumber = model.getLineCount();
            if (nextPosition.column > model.getLineMaxColumn(nextPosition.lineNumber)) nextPosition.column = model.getLineMaxColumn(nextPosition.lineNumber);
            editor.setPosition(nextPosition);
            editor.revealPositionInCenter(nextPosition);
            editor.focus();
        }).catch(() => undefined);
    };

    return <button
        type="button"
        className={StatusPosition}
        onClick={openPositionDialog}
        aria-label={`Go to line ${position.line}, column ${position.column}`}
    >{position.line}:{position.column}</button>;
}

function parseEditorPosition(value: string): {lineNumber: number; column: number} | null {
    const match = /^(\d+)(?:\s*:\s*(\d+))?$/.exec(value.trim());
    if (!match) return null;

    const lineNumber = Number(match[1]);
    const column = match[2] === undefined ? 1 : Number(match[2]);
    if (!Number.isSafeInteger(lineNumber) || !Number.isSafeInteger(column) || lineNumber < 1 || column < 1) return null;
    return {lineNumber, column};
}

const fallbackIcon = toIconPath("default");
const monochromeFileIconLanguages = new Set([
    "c",
    "clojure",
    "d",
    "dart",
    "default",
    "haskell",
    "haxe",
    "jinja2",
    "liquid",
    "lua",
    "notebook",
    "ocaml",
    "odata",
    "perl",
    "prolog",
    "pug",
    "rust",
    "scala",
    "swift",
    "tex",
    "webpack",
]);

const DarkModeMonochromeFileIcon = injectStyle("dark-mode-monochrome-file-icon", k => `
    html.dark ${k} {
        filter: brightness(0) invert(1);
    }
`);

const LanguageItem: RichSelectChildComponent<{language: string; displayName: string}> = props => {
    const language = props.element?.language;
    if (!language) return null;
    return <Flex key={language} my="4px" onClick={props.onSelect} {...props.dataProps}>
        <FileLanguageIcon language={language} /> {props.element?.displayName}
    </Flex>;
}

export function FullpathFileLanguageIcon({filePath, size}: {filePath: string; size?: string;}) {
    const language = languageFromExtension(extensionFromPath(filePath));
    return <FileLanguageIcon key={language} language={language} size={size} m="" ext={extensionFromPath(filePath)} />
}

function FileLanguageIcon({language, ext, size = "18px", m = "2px 8px 0px 8px"}: {ext?: string; language: string; size?: string; m?: string;}): React.ReactNode {
    const [didError, setError] = useState(false);
    const [iconPath, setIconPath] = useState(toIconPath(language ?? ""));

    React.useEffect(() => {
        setError(false);
        setIconPath(toIconPath(language));
    }, [language]);

    const isMonochrome = monochromeFileIconLanguages.has(language) || iconPath === fallbackIcon;
    const className = isMonochrome ? DarkModeMonochromeFileIcon : undefined;

    if (didError && ext) {
        return <FtIcon fileIcon={{type: "FILE", ext}} size={size} />
    }

    return <Image
        className={className}
        m={m}
        height={size}
        width={size}
        onError={() => {
            setError(true);
            setIconPath(fallbackIcon);
        }}
        alt={"Icon for " + language}
        src={iconPath}
    />
}

function toIconPath(language: string): string {
    let lang = language;
    switch (language) {
        case "csharp":
            lang = "c-sharp";
            break;
        case "c++":
            lang = "cpp";
            break;
    }

    return "/Images/file-icons/" + lang + ".svg";
}

function virtualFileSort(a: VirtualFile, b: VirtualFile): number {
    if (a.isDirectory && b.isDirectory) return a.absolutePath.localeCompare(b.absolutePath);
    if (a.isDirectory) return -1;
    if (b.isDirectory) return 1;
    return a.absolutePath.localeCompare(b.absolutePath);
}

type RevertSaveFunction = () => void;
