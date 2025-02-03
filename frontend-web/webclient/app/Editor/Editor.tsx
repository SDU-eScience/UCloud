import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {useSelector} from "react-redux";
import {editor} from "monaco-editor";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {injectStyle} from "@/Unstyled";
import {TreeAction, TreeApi} from "@/ui-components/Tree";
import {Box, ExternalLink, Flex, FtIcon, Icon, Label, Select, Truncate} from "@/ui-components";
import {fileName, pathComponents} from "@/Utilities/FileUtilities";
import {copyToClipboard, doNothing, errorMessageOrDefault, extensionFromPath} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {VimEditor} from "@/Vim/VimEditor";
import {VimWasm} from "@/Vim/vimwasm";
import * as Heading from "@/ui-components/Heading";
import {TooltipV2} from "@/ui-components/Tooltip";
import {PrettyFilePath, usePrettyFilePath} from "@/Files/FilePath";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Operation, Operations, ShortcutKey} from "@/ui-components/Operation";
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import EditorOption = editor.EditorOption;
import {Feature, hasFeature} from "@/Features";
import {EditorSidebarNode, FileTree, VirtualFile} from "@/Files/FileTree";
import {noopCall} from "@/Authentication/DataHook";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useBeforeUnload} from "react-router-dom";

export interface Vfs {
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
    viewState: Record<string, monaco.editor.ICodeEditorViewState>;
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
    editorState: monaco.editor.ICodeEditorViewState | null;
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

            selfCopy.children.sort((a, b) => a.file.absolutePath.toLowerCase().localeCompare(b.file.absolutePath.toLowerCase()));
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
            leaf.children = action.files.map(it => {
                const existing = leaf.children.find(child => child.file.absolutePath === it.absolutePath);
                return existing ?? {
                    file: it,
                    children: [],
                };
            });
            leaf.children.sort((a, b) => a.file.absolutePath.toLowerCase().localeCompare(b.file.absolutePath.toLowerCase()));

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

const monacoCache = new AsyncCache<any>();

export async function getMonaco(): Promise<any> {
    return monacoCache.retrieve("", async () => {
        const monaco = await (import("monaco-editor"));
        self.MonacoEnvironment = {
            getWorker: function (workerId, label) {
                switch (label) {
                    case 'json':
                        return getWorkerModule('/monaco-editor/esm/vs/language/json/json.worker?worker', label);
                    case 'css':
                    case 'scss':
                    case 'less':
                        return getWorkerModule('/monaco-editor/esm/vs/language/css/css.worker?worker', label);
                    case 'html':
                    case 'handlebars':
                    case 'razor':
                        return getWorkerModule('/monaco-editor/esm/vs/language/html/html.worker?worker', label);
                    case 'typescript':
                    case 'javascript':
                        return getWorkerModule('/monaco-editor/esm/vs/language/typescript/ts.worker?worker', label);
                    default:
                        return getWorkerModule('/monaco-editor/esm/vs/editor/editor.worker?worker', label);
                }

                function getWorkerModule(moduleUrl, label) {
                    return new Worker(self.MonacoEnvironment!.getWorkerUrl!(moduleUrl, label), {
                        name: label,
                        type: 'module'
                    });
                }
            }
        };
        return monaco;
    })
}

export function useMonaco(active: boolean): any {
    const didInit = useRef(false);
    const [monacoInstance, setMonacoInstance] = useState<any>(undefined);
    useEffect(() => {
        if (!active) return;
        if (didInit.current) return;
        didInit.current = true;

        let didCancel = false;
        getMonaco().then((monaco) => {
            if (didCancel) return;
            setMonacoInstance(monaco);
        });


        return () => {
            didCancel = true;
        }
    }, [active]);

    return monacoInstance;
}

const editorClass = injectStyle("editor", k => `
    ${k} {
        display: flex;
        width: 100%;
        height: 100%;
        --borderThickness: 2px;
    }
    
    ${k} > .main-content {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100%;
        min-width: 600px;
    }
    
    ${k} .title-bar-code,
    ${k} .title-bar {
        display: flex;
        align-items: center;
        height: 34px;
        width: 100%;
        flex-shrink: 0;
        border-bottom: var(--borderThickness) solid var(--borderColor);
    }
    

    ${k} .panels {
        display: flex;
        width: 100%;
        height: 100%;
    }
    
    ${k} .panels > div > .code {
        flex-grow: 1;
        height: 100%;
    }
`);

type EditorEngine =
    | "monaco"
    | "vim";

export interface EditorApi {
    path: string;
    notifyDirtyBuffer: () => Promise<void>;
    openFile: (path: string) => void;
    invalidateTree: (path: string) => void;
}

const SETTINGS_PATH = "xXx__/SETTINGS\\__xXx";

export const Editor: React.FunctionComponent<{
    vfs: Vfs;
    title: string;
    initialFilePath?: string;
    initialFolderPath: string;
    toolbarBeforeSettings?: React.ReactNode;
    toolbar?: React.ReactNode;
    apiRef?: React.MutableRefObject<EditorApi | null>;
    customContent?: React.ReactNode;
    showCustomContent?: boolean;
    onOpenFile?: (path: string, content: string | Uint8Array) => void;
    operations?: (file: VirtualFile) => Operation<any>[];
    help?: React.ReactNode;
    fileHeaderOperations?: React.ReactNode;
    readOnly: boolean;
}> = props => {
    const [engine, setEngine] = useState<EditorEngine>(localStorage.getItem("editor-engine") as EditorEngine ?? "monaco");
    const [state, dispatch] = useReducer(singleEditorReducer, 0, () => defaultEditor(props.vfs, props.title, props.initialFolderPath, props.initialFilePath));
    const editorView = useRef<HTMLDivElement>(null);
    const currentTheme = useSelector((red: ReduxObject) => red.sidebar.theme);
    const monacoInstance = useMonaco(engine === "monaco");
    const [editor, setEditor] = useState<IStandaloneCodeEditor | null>(null);
    const monacoRef = useRef<any>(null);
    const [tabs, setOpenTabs] = useState<string[]>([state.currentPath]);

    const prettyPath = usePrettyFilePath(state.currentPath ?? "");
    usePage(fileName(prettyPath), SidebarTabId.FILES);

    const [closedTabs, setClosedTabs] = useState<string[]>([]);
    const [operations, setOperations] = useState<Operation<any, undefined>[]>([]);
    const isSettingsOpen = state.currentPath === SETTINGS_PATH;

    // NOTE(Dan): This code is quite ref heavy given that the components we are controlling are very much the
    // opposite of reactive. There isn't much we can do about this.
    const engineRef = useRef<EditorEngine>("vim");
    const stateRef = useRef<EditorState>();
    const vimRef = useRef<VimWasm | null>(null);
    const tree = useRef<TreeApi | null>(null);
    const editorRef = useRef<IStandaloneCodeEditor | null>(null);
    const showingCustomContent = useRef<boolean>(props.showCustomContent === true);

    useEffect(() => {
        showingCustomContent.current = props.showCustomContent === true;
    }, [props.showCustomContent]);

    useEffect(() => {
        dispatch({type: "EditorActionUpdateTitle", title: props.title});
    }, [props.title]);

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
    }, [monacoInstance]);

    useEffect(() => {
        const ref = props.apiRef;
        if (!ref || !ref.current) return;
        ref.current.path = state.currentPath;
    }, [props.apiRef, state.currentPath]);

    const didUnmount = useDidUnmount();

    const reloadBuffer = useCallback((name: string, content: string) => {
        const editor = editorRef.current;
        const engine = engineRef.current;
        switch (engine) {
            case "monaco": {
                if (!editor) return;
                const model = editor.getModel();
                if (!model) return;
                model.setValue(content);
                break;
            }

            case "vim": {
                const vim = vimRef.current;
                if (!vim) return;
                vim.reloadBuffer(name, content);
                break;
            }
        }
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

            case "vim": {
                const vim = vimRef.current;
                if (!vim) return Promise.reject();
                return vim.readBuffer();
            }
        }
    }, []);

    const openFile = useCallback(async (path: string, saveState: boolean): Promise<boolean> => {
        const cachedContent = state.cachedFiles[path];
        const dataPromise = path === SETTINGS_PATH ?
            "" :
            cachedContent !== undefined ?
                Promise.resolve(cachedContent) :
                props.vfs.readFile(path);

        const syntax = findNode(state.sidebar.root, path)?.file?.requestedSyntax;

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
                if (state.currentPath !== "/" && state.currentPath !== "" && saveState) {
                    let editorState: monaco.editor.ICodeEditorViewState | null = null;
                    const model = editor?.getModel();
                    if (editor && model) {
                        editorState = editor.saveViewState();
                    }

                    const oldContent = await readBuffer();
                    props.vfs.setDirtyFileContent(state.currentPath, oldContent);
                    dispatch({type: "EditorActionSaveState", editorState, oldContent, newPath: path});
                } else {
                    dispatch({type: "EditorActionOpenFile", path});
                }
            } else {
                dispatch({type: "EditorActionOpenFile", path});
            }

            if (engine === "vim" && vimRef.current != null && !vimRef.current.initialized) return false;
            if (typeof content === "string") {
                reloadBuffer(fileName(path), content);
                const restoredState = state.viewState[path];
                if (editor && restoredState) {
                    editor.restoreViewState(restoredState);
                }

                if (engine === "monaco" && editor == null) return false;
                if (engine === "vim" && vimRef.current == null) return false;

                vimRef.current?.focus?.();
                tree.current?.deactivate?.();
                editor?.focus?.();
                const monaco = monacoRef.current;
                if (syntax && monaco && editor) {
                    monaco.editor.setModelLanguage(editor.getModel(), syntax);
                }
            }

            return true;
        } catch (error) {
            snackbarStore.addFailure(errorMessageOrDefault(error, "Failed to fetch file"), false);
            return true; // What does true or false mean in this context?
        }
    }, [state, props.vfs, dispatch, reloadBuffer, readBuffer, props.onOpenFile]);

    useEffect(() => {
        const listener = (ev: KeyboardEvent) => {
            if (ev.defaultPrevented) return;
            if (ev.code === "Escape") {
                ev.preventDefault();
                return;
            }

            if (engineRef.current === "vim") {
                vimRef.current?.focus?.();
                tree.current?.deactivate?.();
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
        const vim = vimRef.current;

        if (state.currentPath === "" || state.currentPath === "/") return;

        if (engine === "vim" && vimRef.current != null && !vimRef.current.initialized) return;
        if (engine === "monaco" && editor == null) return;
        if (engine === "vim" && vimRef.current == null) return;

        const res = await readBuffer();
        if (didUnmount.current) return;

        props.vfs.setDirtyFileContent(state.currentPath, res);
        props.onOpenFile?.(state.currentPath, res);
        dispatch({type: "EditorActionSaveState", editorState: null, oldContent: res, newPath: state.currentPath});
    }, [props.onOpenFile]);

    const invalidateTree = useCallback((folder: string) => {
        let didCancel = false;
        props.vfs.listFiles(folder).then(files => {
            if (didCancel) return;
            dispatch({type: "EditorActionFilesLoaded", path: folder, files});
        });

        return () => {
            didCancel = true;
        };
    }, [props.vfs]);

    const api: EditorApi = useMemo(() => {
        return {
            path: state.currentPath,
            notifyDirtyBuffer: saveBufferIfNeeded,
            openFile: path => {
                openFile(path, true);
            },
            invalidateTree,
        }
    }, []);

    useEffect(() => {
        if (props.apiRef) props.apiRef.current = api;
    }, [api, props.apiRef]);

    useEffect(() => {
        invalidateTree(props.initialFolderPath);
    }, []);

    useLayoutEffect(() => {
        const m = monacoInstance;
        const node = editorView.current;
        if (!m || !node) return;

        m.editor.defineTheme('ucloud-dark', {
            base: 'vs-dark',
            inherit: true,
            rules: [],
            colors: {
                'editor.background': '#21262D'
            }
        });

        node.innerHTML = "";
        node.getAttributeNames().forEach(n => {
            if (n !== "class") node.removeAttribute(n);
        });

        // Register a new Jinja2 language
        m.languages.register({id: 'jinja2'});
        m.option

        // Define the syntax highlighting rules for Jinja2
        m.languages.setMonarchTokensProvider('jinja2', jinja2monarchTokens);

        const editor: IStandaloneCodeEditor = m.editor.create(node, {
            value: "",
            language: "jinja2",
            readOnly: props.readOnly,
            minimap: {enabled: false},
            renderLineHighlight: "none",
            fontFamily: "Jetbrains Mono",
            fontSize: 14,
            theme: currentTheme === "light" ? "light" : "ucloud-dark",
            wordWrap: "off",
            ...getEditorOptions(),
        });

        setEditor(editor);
    }, [monacoInstance, isSettingsOpen]);

    useLayoutEffect(() => {
        let timer = -1;
        const fn = async () => {
            const res = await openFile(state.currentPath, false);
            if (!res) timer = window.setTimeout(fn, 50);
        };
        timer = window.setTimeout(fn, 50);

        return () => {
            window.clearTimeout(timer);
        };
    }, [isSettingsOpen]);

    useEffect(() => {
        const theme = currentTheme === "light" ? "light" : "ucloud-dark";
        monacoInstance?.editor?.setTheme(theme);
        const vim = vimRef.current;
        if (vim) {
            (async () => {
                if (currentTheme === "light") {
                    await vim.cmdline("set background=light");
                    await vim.cmdline("colorscheme PaperColor");
                } else {
                    await vim.cmdline("set background=dark");
                    await vim.cmdline("colorscheme PaperColor");
                }
                await vim.cmdline("redraw");
            })();
        }
    }, [currentTheme]);

    useEffect(() => {
        const listener = () => {
            if (!editor) return;
            editor.layout();
        };

        const interval = window.setInterval(listener, 200);

        window.addEventListener("resize", listener);

        return () => {
            window.removeEventListener("resize", listener);
            window.clearInterval(interval);
        };
    }, [editor]);

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

    const onOpen = useCallback((path: string, element: HTMLElement) => {
        const root = state.sidebar.root;
        const node = findNode(root, path);
        if (!node || node.file.isDirectory) {
            props.vfs.listFiles(path).then(files => {
                if (didUnmount.current) return;
                dispatch({type: "EditorActionFilesLoaded", path, files});
            })
        } else {
            // NOTE(Dan): This ensures that onOpen is always allowed to be called. This might be something we want to
            // do directly in TreeNode if we know it has no children.
            element.removeAttribute("data-open");

            openTab(path);
        }
    }, [editor, state, props.vfs, dispatch, reloadBuffer, readBuffer]);

    const onNodeActivated = useCallback((open: boolean, row: HTMLElement) => {
        const path = row.getAttribute("data-path");
        if (path) {
            onOpen(path, row);
        }
    }, [onOpen]);

    const onTreeAction = useCallback((row: HTMLElement, action: TreeAction) => {
        if (action === TreeAction.OPEN || action === TreeAction.TOGGLE) {
            const path = row.getAttribute("data-path");
            if (path) onOpen(path, row);
        }
    }, [onOpen]);

    const toggleSettings = useCallback(() => {
        saveBufferIfNeeded().then(() => {
            setOpenTabs(tabs => {
                if (tabs.includes(SETTINGS_PATH)) {
                    return tabs;
                } else return [...tabs, SETTINGS_PATH];
            })
            dispatch({type: "EditorActionOpenFile", path: SETTINGS_PATH})
        });
    }, []);

    const openTab = React.useCallback(async (path: string) => {
        if (state.currentPath === path) return;
        await openFile(path, true);
        setOpenTabs(tabs => {
            if (tabs.includes(path)) {
                return tabs;
            } else return [...tabs, path];
        });
    }, [state.currentPath]);

    const closeTab = useCallback((path: string, index: number) => {
        setOpenTabs(tabs => {
            const result = tabs.filter(tabTitle => tabTitle !== path);
            if (state.currentPath === path) {
                const preceedingPath = result.at(index - 1);
                if (preceedingPath) {
                    openFile(preceedingPath, true);
                }
            }
            return result;
        });
        if (!closedTabs.includes(path)) setClosedTabs(tabs => [...tabs, path]);
    }, [state.currentPath, closedTabs]);

    const openTabOperationWindow = useRef<(x: number, y: number) => void>(noopCall)

    const openTabOperations = React.useCallback((title: string, position: {x: number; y: number;}) => {
        const ops = tabOperations(title, tabs, setOpenTabs, closedTabs, setClosedTabs, openTab, state.currentPath);
        setOperations(ops);
        openTabOperationWindow.current(position.x, position.y);
    }, [tabs, closedTabs, state.currentPath]);

    useBeforeUnload((e: BeforeUnloadEvent): BeforeUnloadEvent => {
        // TODO(Jonas): Only handles closing window, not UCloud navigation 
        const anyDirty = false;
        if (anyDirty) {
            // Note(Jonas): Both should be done for best compatibility: https://developer.mozilla.org/en-US/docs/Web/API/BeforeUnloadEvent/returnValue
            e.preventDefault();
            e.returnValue = "truthy value";
            return e;
        }
        return e;
        /* TODO(Jonas): This should check if any file is dirty and warn user. Also on redirect? */
    });


    // Current path === "", can we use this as empty/scratch space, or is this in use for Scripts/Workflows
    const showEditorHelp = state.currentPath === "";

    return <div className={editorClass} onKeyDown={onKeyDown}>
        <FileTree
            tree={tree}
            onTreeAction={onTreeAction}
            onNodeActivated={onNodeActivated}
            root={state.sidebar.root}
            width="250px"
            initialFolder={props.initialFolderPath}
            initialFilePath={props.initialFilePath}
            fileHeaderOperations={props.fileHeaderOperations}
            operations={props.operations}
        />

        <div className={"main-content"}>
            <div className={"title-bar-code"} style={{minWidth: "400px", paddingRight: "12px", width: `calc(100vw - 250px - var(--sidebarWidth) - 20px)`}}>
                <div style={{display: "flex", maxWidth: `calc(100% - 48px)`, overflowX: "auto", width: "100%"}}>
                    {tabs.map((t, index) =>
                        <EditorTab
                            key={t}
                            isDirty={false /* TODO */}
                            isActive={t === state.currentPath}
                            onActivate={() => openTab(t)}
                            onContextMenu={e => {
                                e.preventDefault();
                                openTabOperations(t, {x: e.clientX, y: e.clientY});
                            }}
                            close={() => {
                                /* if (fileIsDirty) promptSaveFileWarning() else */
                                closeTab(t, index);
                            }}
                            children={t}
                        />
                    )}
                    <Operations
                        entityNameSingular={""}
                        operations={operations}
                        forceEvaluationOnOpen={true}
                        openFnRef={openTabOperationWindow}
                        selected={[]}
                        extra={null}
                        row={42}
                        hidden
                        location={"IN_ROW"}
                    />
                </div>
                <Flex alignItems={"center"} ml="16px" gap="16px">
                    {props.toolbarBeforeSettings}
                    {!hasFeature(Feature.EDITOR_VIM) ? null :
                        <TooltipV2 tooltip={"Settings"} contentWidth={100}>
                            <Icon name={"heroCog6Tooth"} size={"20px"} cursor={"pointer"} onClick={toggleSettings} />
                        </TooltipV2>
                    }
                    {props.toolbar}
                </Flex>
            </div>
            <div className={"panels"}>
                {isSettingsOpen ?
                    <Flex gap={"32px"} flexDirection={"column"} margin={64} width={"100%"} height={"100%"}>
                        <MonacoEditorSettings editor={editor} />
                        <Label>
                            Editor engine
                            <Select value={engine} width={"100%"} onChange={ev => {
                                setEngine(ev.target.value as EditorEngine);
                            }}>
                                <option value={"monaco"}>Monaco</option>
                                <option value={"vim"}>Vim (experimental)</option>
                            </Select>
                        </Label>

                        <Flex ml={32} gap={"32px"} flexDirection={"column"}>
                            <Box>
                                <Heading.h4>Monaco Engine</Heading.h4>
                                <p>
                                    The <ExternalLink
                                        href={"https://github.com/microsoft/monaco-editor"}>Monaco</ExternalLink>{" "}
                                    engine provides an easy-to-use and familiar editor. It is best known from
                                    <ExternalLink href={"https://github.com/microsoft/vscode"}>VS Code</ExternalLink>.
                                    This engine is recommended for most users and is expected to work without any
                                    quirks.
                                </p>
                            </Box>

                            <Box>
                                <Heading.h4>Vim Engine</Heading.h4>
                                <p>
                                    The <ExternalLink href={"https://github.com/rhysd/vim.wasm"}>Vim (WASM)</ExternalLink> engine is powered by a real instance of
                                    <ExternalLink href={"https://www.vim.org/"}>Vim</ExternalLink> running in your web-browser. We encourage anyone
                                    who prefers using Vim to try out this engine, but it comes with a number of quirks
                                    that you need to know to use it efficiently.
                                </p>

                                <p><b>Quirks:</b></p>
                                <ul>
                                    <li>The Vim engine might not work in all browsers.</li>
                                    <li>
                                        Do not use <code>:e</code> or <code>:w</code> they do not function correctly
                                        for files not in <code>~/.vim</code>.
                                    </li>
                                    <li>
                                        Resizing your window can cause glitchy output. Resizing your window again or
                                        reloading the window should fix this.
                                    </li>
                                    <li>
                                        It is possible to modify your <code>.vimrc</code> by doing the following:
                                        <ul>
                                            <li>Open and modify <code>/home/web_user/.vim/.vimrc</code></li>
                                            <li>Save the file with <code>:w</code> and exit with <code>:q</code></li>
                                            <li>Do not use <code>:x</code>, this command does not work!</li>
                                            <li>Reload the browser. Your vimrc is now persisted locally (in IndexedDB)
                                            </li>
                                        </ul>
                                    </li>
                                    <li>It is not possible to install custom plugins for Vim.</li>
                                    <li>Quitting the editor via <code>:q</code> will cause the editor to become unresponsive, requiring a full reload to become functional.</li>
                                </ul>
                            </Box>
                        </Flex>

                    </Flex> : showEditorHelp && props.help ? props.help :
                        <>
                            <div style={{
                                display: props.showCustomContent ? "none" : "block",
                                width: "100%",
                                height: "100%"
                            }}>
                                {engine !== "monaco" ? null :
                                    <div className={"code"} ref={editorView} onFocus={() => tree?.current?.deactivate?.()} />
                                }

                                {engine !== "vim" ? null :
                                    <VimEditor vim={vimRef} onInit={doNothing} />
                                }
                            </div>

                            <div style={{
                                display: props.showCustomContent ? "block" : "none",
                                width: "100%",
                                height: "100%",
                                maxHeight: "calc(100vh - 64px)",
                                padding: "16px",
                                overflow: "auto",
                            }}>{props.customContent}</div>
                        </>
                }
            </div>
        </div>
    </div>;
};

/* TODO(Jonas): Improve parameters this is... not good */
function tabOperations(
    tabPath: string,
    openTabs: string[],
    setOpenTabs: React.Dispatch<React.SetStateAction<string[]>>,
    closedTabs: string[],
    setClosedTabs: React.Dispatch<React.SetStateAction<string[]>>,
    openTab: (path: string) => void,
    currentPath: string,
): Operation<any>[] {
    return [{
        text: "Close tab",
        onClick: () => {
            setOpenTabs(tabs => tabs.filter(it => it !== tabPath));
            setClosedTabs(tabs => [...tabs, tabPath]);
            if (currentPath === tabPath) {
                const index = openTabs.findIndex(it => it === tabPath);
                if (index === -1) {
                    console.warn("No index found. This is weird. This shouldn't happen");
                    return;
                }
                openTab(openTabs.at(index - 1)!);
            }
        },
        enabled: () => true,
        shortcut: ShortcutKey.W,
    }, {
        text: "Close others",
        onClick: () => {
            setClosedTabs(tabs => [
                ...tabs,
                ...openTabs.filter(it => it !== it)
            ]);
            openTab(tabPath);
        },
        enabled: () => true,
        shortcut: ShortcutKey.E,
    }, {
        text: "Close to the right",
        onClick: () => {
            const index = openTabs.findIndex(it => it === tabPath);
            const activeIndex = openTabs.findIndex(it => it === currentPath);
            if (activeIndex > index) {
                openTab(tabPath);
            }
            if (index === -1) {
                console.warn("No index found. This is weird. This shouldn't happen");
                return;
            }
            setClosedTabs(t => [...t, ...openTabs.slice(index + 1)])
            setOpenTabs(t => t.slice(0, index + 1));
        },
        enabled: () => true,
        shortcut: ShortcutKey.R,
    }, /* {
        text: "Close saved",
        onClick: () => console.log("TODO"),
        enabled: () => true,
        shortcut: ShortcutKey.T,
    }, */ {
        text: "Close all",
        onClick: () => {
            setClosedTabs(closed => [...closed, ...openTabs]);
            setOpenTabs([]);
        },
        enabled: () => true,
        shortcut: ShortcutKey.U,
    }, {
        text: "Copy path to clipboard",
        onClick: () => copyToClipboard({value: tabPath, message: ""}),
        enabled: () => true,
        shortcut: ShortcutKey.U,
    }, {
        text: "Re-open closed tab",
        onClick: () => {
            setClosedTabs(closedTabs => {
                const tab = closedTabs.pop();
                if (tab) setOpenTabs(openTabs => [...openTabs, tab]);
                return closedTabs;
            });
        },
        enabled: () => closedTabs.length > 0,
        shortcut: ShortcutKey.U,
    }];
}


function EditorTab({
    isDirty,
    isActive,
    close,
    onActivate,
    onContextMenu,
    children: title
}: React.PropsWithChildren<{
    isActive: boolean;
    isDirty: boolean;
    onContextMenu?: (e: React.MouseEvent<any>) => void;
    onActivate(): void;
    close(): void
}>): React.ReactNode {
    const [hovered, setHovered] = useState(false);

    const isSettings = title === SETTINGS_PATH;

    const onClose = React.useCallback((e: React.SyntheticEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();

        close();
    }, [close]);

    return (
        <Flex onContextMenu={onContextMenu} className={EditorTabClass} mt="auto" data-active={isActive} minWidth="250px" width="250px" onClick={onActivate}>
            {isSettings ? <Icon name="heroCog6Tooth" size="18px" /> : <FtIcon fileIcon={{type: "FILE", ext: extensionFromPath(title as string)}} size={"18px"} />}
            <Truncate ml="8px" width="50%">{isSettings ? "Editor settings" : fileName(title as string)}</Truncate>
            <Icon
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
                cursor="pointer" name={isDirty && !hovered ? "circle" : "close"}
                size={12}
                onClick={onClose} />
        </Flex>
    );
}

const EditorTabClass = injectStyle("editor-tab-class", k => `
    ${k} {
        height: 32px;
        font-size: 12px;
        padding-left: 12px;
        padding-right: 12px;
        cursor: pointer;
    }

    ${k} > * {
        margin-top: auto;
        margin-bottom: auto;
    }

    ${k}[data-active="true"], ${k}:hover  {
        background-color: var(--infoContrast);
    }
`);

const jinja2monarchTokens = {
    tokenizer: {
        root: [
            // Jinja2 variable tags: {{ variable }}
            [/\{\{/, 'keyword.control', '@variable'],

            // Jinja2 statement tags: {% statement %}
            [/\{%/, 'keyword.control', '@statement'],

            [/\{-/, 'keyword.control', '@template'],

            // Jinja2 comment tags: {# comment #}
            [/\{#/, 'comment.jinja2', '@comment'],

            // Strings inside the templates
            [/["']/, 'string'],

            [/#.*$/, 'comment'],
        ],

        variable: [
            // Closing of Jinja2 variable tags
            [/}}/, 'keyword.control', '@pop'],

            // Inside variables
            [/./, 'variable'],
        ],

        statement: [
            // Closing of Jinja2 statement tags
            [/%}/, 'keyword.control', '@pop'],

            // Inside statements
            [/./, 'keyword'],
        ],

        comment: [
            // Closing of Jinja2 comment tags
            [/#}/, 'comment', '@pop'],

            // Inside comments
            [/./, 'comment'],
        ],

        template: [
            // Closing of Jinja2 statement tags
            [/-}/, 'keyword.control', '@pop'],

            // Inside statements
            [/./, 'keyword'],
        ]
    }
};

type EditorOptionPair<T extends EditorOption> = [T, editor.FindComputedEditorOptionValueById<T>[]];
const AvailableSettings: [
    EditorOptionPair<EditorOption.fontSize>,
    EditorOptionPair<EditorOption.fontWeight>,
    EditorOptionPair<EditorOption.wordWrap>,
] = [
        [EditorOption.fontSize, [8, 10, 12, 14, 16, 18, 20, 22]],
        [EditorOption.fontWeight, ["200", "400", "600", "800", "bold"]],
        [EditorOption.wordWrap, ["wordWrapColumn", "on", "off", "bounded"]],
    ];

function MonacoEditorSettings({editor}: {editor: IStandaloneCodeEditor | null}) {
    const setOption = React.useCallback((setting: EditorOption, value: editor.FindComputedEditorOptionValueById<EditorOption>) => {
        if (!editor) return;

        const settingsChange = {
            [EditorOption[setting]]: value
        }

        editor.updateOptions(settingsChange);
        updateEditorSettings(settingsChange);
    }, [editor]);

    if (!editor) return null;

    return <>
        {AvailableSettings.map(([setting, options]) => <div key={setting}>
            {EditorOption[setting]}
            <Select defaultValue={editor.getOption(setting)} onChange={e => setOption(setting, e.target.value)}>
                {options.map((opt: string | number) =>
                    <option key={opt} value={opt}>{opt}</option>
                )}
            </Select>
        </div>)}
    </>;
}

interface StoredSettings {
    fontSize?: number;
    fontWeight?: string;
    wordWrap?: string;
}

const PreviewEditorSettingsLocalStorageKey = "PreviewEditorSettings";
function getEditorOptions(): StoredSettings {
    return JSON.parse(localStorage.getItem(PreviewEditorSettingsLocalStorageKey) ?? "{}");
}

function updateEditorSettings(settings: StoredSettings): void {
    const opts = getEditorOptions();
    storeEditorSettings({...opts, ...settings});

}

function storeEditorSettings(settings: StoredSettings): void {
    localStorage.setItem(PreviewEditorSettingsLocalStorageKey, JSON.stringify(settings));
}
