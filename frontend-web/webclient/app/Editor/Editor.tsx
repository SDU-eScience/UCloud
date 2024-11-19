import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {useSelector} from "react-redux";
import {editor} from "monaco-editor";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {injectStyle} from "@/Unstyled";
import {Tree, TreeAction, TreeApi, TreeNode} from "@/ui-components/Tree";
import {Box, ExternalLink, Flex, FtIcon, Icon, Label, Select} from "@/ui-components";
import {fileName, pathComponents} from "@/Utilities/FileUtilities";
import {doNothing, extensionFromPath} from "@/UtilityFunctions";
import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {VimEditor} from "@/Vim/VimEditor";
import {VimWasm} from "@/Vim/vimwasm";
import * as Heading from "@/ui-components/Heading";
import {TooltipV2} from "@/ui-components/Tooltip";
import {PrettyFileName} from "@/Files/FilePath";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

export interface Vfs {
    listFiles(path: string): Promise<VirtualFile[]>;

    readFile(path: string): Promise<string>;

    writeFile(path: string, content: string): Promise<void>;

    // Notifies the VFS that a file is dirty, but do not synchronize it yet.
    notifyDirtyFile(path: string, content: string): void;
}

export interface VirtualFile {
    absolutePath: string;
    isDirectory: boolean;
    requestedSyntax?: string;
}

export interface EditorState {
    vfs: Vfs;
    title: string;
    sidebar: EditorSidebar;
    cachedFiles: Record<string, string>;
    viewState: Record<string, monaco.editor.ICodeEditorViewState>;
    currentPath: string;
}

export interface EditorSidebar {
    root: EditorSidebarNode;
}

export interface EditorSidebarNode {
    file: VirtualFile;
    children: EditorSidebarNode[];
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
    const components = pathComponents(path);
    if (components.length === 0) return root;

    let currentNode = root;
    let currentPath = "/";
    for (let i = 0; i < components.length; i++) {
        currentPath += components[i];
        const node = currentNode.children.find(it => it.file.absolutePath === currentPath || it.file.absolutePath === path);
        if (!node) return null;
        else if (node.file.absolutePath === path) {
            return node;
        }
        currentNode = node;
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
            }
        }

        case "EditorActionUpdateTitle": {
            break;
        }

        case "EditorActionOpenFile": {
            return {
                ...state,
                currentPath: action.path
            }
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

    return state;
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
    
    ${k} > .editor-files {
        width: 250px;
        /* TODO(Jonas): scroll on overflow-x */
        height: min(calc(100vh - 32px), 100%);
        overflow-y: auto;
        
        flex-shrink: 0;
        border-right: var(--borderThickness) solid var(--borderColor);
    }
    
    ${k} > .main-content {
        display: flex;
        flex-direction: column;
        width: 100%;
        height: 100%;
    }
    
    ${k} .title-bar-code,
    ${k} .title-bar {
        display: flex;
        align-items: center;
        height: 48px;
        width: 100%;
        flex-shrink: 0;
        border-bottom: var(--borderThickness) solid var(--borderColor);
        padding: 0 8px;
    }
    
    ${k} .title-bar-code {
        padding: 0 20px; /* this aligns the file icon with the line gutter */
    }
    
    ${k} .panels {
        display: flex;
        width: 100%;
        height: 100%;
    }
    
    ${k} .panels > .code {
        flex-grow: 1;
        height: 100%;
    }
`);

type EditorEngine =
    | "monaco"
    | "vim";

export interface EditorApi {
    notifyDirtyBuffer: () => Promise<void>;
}

export const Editor: React.FunctionComponent<{
    vfs: Vfs;
    title: string;
    initialFilePath?: string;
    initialFolderPath: string;
    toolbarBeforeSettings?: React.ReactNode;
    toolbar?: React.ReactNode;
    apiRef?: React.MutableRefObject<EditorApi | null>;
}> = props => {
    const [engine, setEngine] = useState<EditorEngine>(localStorage.getItem("editor-engine") as EditorEngine ?? "monaco");
    const [state, dispatch] = useReducer(singleEditorReducer, 0, () => defaultEditor(props.vfs, props.title, props.initialFolderPath, props.initialFilePath));
    const editorView = useRef<HTMLDivElement>(null);
    const currentTheme = useSelector((red: ReduxObject) => red.sidebar.theme);
    const monacoInstance = useMonaco(engine === "monaco");
    const [editor, setEditor] = useState<IStandaloneCodeEditor | null>(null);
    const monacoRef = useRef<any>(null);
    const [isSettingsOpen, setIsSettingsOpen] = useState(false);

    // NOTE(Dan): This code is quite ref heavy given that the components we are controlling are very much the
    // opposite of reactive. There isn't much we can do about this.
    const engineRef = useRef<EditorEngine>("vim");
    const stateRef = useRef<EditorState>();
    const settingsOpenRef = useRef(false);
    const vimRef = useRef<VimWasm | null>(null);
    const tree = useRef<TreeApi | null>(null);
    const editorRef = useRef<IStandaloneCodeEditor | null>(null);

    useEffect(() => {
        engineRef.current = engine;
        localStorage.setItem("editor-engine", engine);
        if (engine !== "monaco" && engine !== "vim") setEngine("monaco");
    }, [engine]);

    useEffect(() => {
        editorRef.current = editor;
    }, [editor]);

    useEffect(() => {
        settingsOpenRef.current = isSettingsOpen;
    }, [isSettingsOpen]);

    useEffect(() => {
        stateRef.current = state;
    }, [state]);

    useEffect(() => {
        monacoRef.current = monacoInstance;
    }, [monacoInstance]);

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

    const openFile = useCallback((path: string, saveState: boolean): Promise<boolean> => {
        const cachedContent = state.cachedFiles[path];
        const dataPromise = cachedContent !== undefined ?
            Promise.resolve(cachedContent) :
            props.vfs.readFile(path);

        const syntax = findNode(state.sidebar.root, path)?.file?.requestedSyntax;

        return dataPromise.then(async content => {
            const editor = editorRef.current;
            const engine = engineRef.current;
            const isSettingsOpen = settingsOpenRef.current;

            if (isSettingsOpen) return true;
            if (didUnmount.current) return true;

            if (state.currentPath !== "/" && state.currentPath !== "" && saveState) {
                let editorState: monaco.editor.ICodeEditorViewState | null = null;
                const model = editor?.getModel();
                if (editor && model) {
                    editorState = editor.saveViewState();
                }

                const oldContent = await readBuffer();
                props.vfs.notifyDirtyFile(state.currentPath, oldContent);
                dispatch({type: "EditorActionSaveState", editorState, oldContent, newPath: path});
            } else {
                dispatch({type: "EditorActionOpenFile", path});
            }

            if (engine === "vim" && vimRef.current != null && !vimRef.current.initialized) return false;
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
            return true;
        }).catch(error => {
            snackbarStore.addFailure(error, false);
            return true; // What does true or false mean in this context?
        });
    }, [state, props.vfs, dispatch, reloadBuffer, readBuffer]);

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
        const isSettingsOpen = settingsOpenRef.current;
        const state = stateRef.current!;
        const vim = vimRef.current;

        if (isSettingsOpen) return;
        if (state.currentPath === "" || state.currentPath === "/") return;

        if (engine === "vim" && vimRef.current != null && !vimRef.current.initialized) return;
        if (engine === "monaco" && editor == null) return;
        if (engine === "vim" && vimRef.current == null) return;

        const res = await readBuffer();
        if (didUnmount.current) return;
        props.vfs.notifyDirtyFile(state.currentPath, res);
        dispatch({type: "EditorActionSaveState", editorState: null, oldContent: res, newPath: state.currentPath});
    }, []);

    const api: EditorApi = useMemo(() => {
        return {
            notifyDirtyBuffer: saveBufferIfNeeded,
        }
    }, []);

    useEffect(() => {
        if (props.apiRef) props.apiRef.current = api;
    }, [api, props.apiRef]);

    useEffect(() => {
        let didCancel = false;
        props.vfs.listFiles(props.initialFolderPath).then(files => {
            if (didCancel) return;
            dispatch({type: "EditorActionFilesLoaded", path: props.initialFolderPath, files});
        });

        return () => {
            didCancel = true;
        };
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

        // Define the syntax highlighting rules for Jinja2
        m.languages.setMonarchTokensProvider('jinja2', jinja2monarchTokens);

        const editor: IStandaloneCodeEditor = m.editor.create(node, {
            value: "",
            language: "jinja2",
            readOnly: false,
            minimap: {enabled: false},
            renderLineHighlight: "none",
            fontFamily: "Jetbrains Mono",
            fontSize: 14,
            theme: currentTheme === "light" ? "light" : "ucloud-dark",
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

        window.addEventListener("resize", listener);

        return () => {
            window.removeEventListener("resize", listener);
        };
    }, [editor]);

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

            openFile(path, true);
        }
    }, [editor, state, props.vfs, dispatch, reloadBuffer, readBuffer]);

    const onNodeActivated = useCallback((open: boolean, row: HTMLElement) => {
        const path = row.getAttribute("data-path");
        if (open && path) onOpen(path, row);
    }, [onOpen]);

    const onTreeAction = useCallback((row: HTMLElement, action: TreeAction) => {
        if (action === TreeAction.OPEN || action === TreeAction.TOGGLE) {
            const path = row.getAttribute("data-path");
            if (path) onOpen(path, row);
        }
    }, [onOpen]);

    const toggleSettings = useCallback(() => {
        saveBufferIfNeeded().then(doNothing);
        setIsSettingsOpen(p => !p);
    }, []);

    return <div className={editorClass} onKeyDown={onKeyDown}>
        <div className={"editor-files"}>
            <div className={"title-bar"}><b>{state.title}</b></div>
            <Tree apiRef={tree} onAction={onTreeAction}>
                <SidebarNode initialFilePath={props.initialFilePath} node={state.sidebar.root} onAction={onNodeActivated} />
            </Tree>
        </div>

        <div className={"main-content"}>
            <div className={"title-bar-code"}>
                <Flex alignItems={"center"} gap={"8px"} flexGrow={1}>
                    <FtIcon fileIcon={{type: "FILE", ext: extensionFromPath(state.currentPath)}} size={"24px"} />
                    {fileName(state.currentPath)}
                </Flex>
                <Flex alignItems={"center"} gap={"16px"}>
                    {props.toolbarBeforeSettings}
                    <TooltipV2 tooltip={"Settings"} contentWidth={100}>
                        <Icon name={"heroCog6Tooth"} size={"20px"} cursor={"pointer"} onClick={toggleSettings} />
                    </TooltipV2>
                    {props.toolbar}
                </Flex>
            </div>
            <div className={"panels"}>
                {isSettingsOpen ?
                    <Flex gap={"32px"} flexDirection={"column"} margin={64} width={"100%"} height={"100%"}>
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

                    </Flex> :
                    <>
                        {engine !== "monaco" ? null :
                            <div className={"code"} ref={editorView} onFocus={() => tree?.current?.deactivate?.()} />
                        }

                        {engine !== "vim" ? null :
                            <VimEditor vim={vimRef} onInit={doNothing} />
                        }
                    </>
                }
            </div>
        </div>
    </div>;
};

const SidebarNode: React.FunctionComponent<{
    node: EditorSidebarNode;
    onAction: (open: boolean, row: HTMLElement) => void;
    initialFilePath?: string;
}> = props => {
    let children = !props.node.file.isDirectory ? undefined : <>
        {props.node.children.map(child => (
            <SidebarNode key={child.file.absolutePath} node={child} onAction={props.onAction} />
        ))}
    </>;

    const absolutePath = props.node.file.absolutePath;
    if (absolutePath === "" || absolutePath === "/") return children;

    const isInitiallyOpen = props.node.file.isDirectory &&
        props.initialFilePath?.startsWith(props.node.file.absolutePath);

    return <TreeNode
        data-path={props.node.file.absolutePath}
        onActivate={props.onAction}
        data-open={isInitiallyOpen}
        left={
            <Flex gap={"8px"} alignItems={"center"}>
                {props.node.file.isDirectory ? null :
                    <Box ml={"8px"}>
                        <FtIcon
                            fileIcon={{
                                type: "FILE",
                                ext: extensionFromPath(props.node.file.absolutePath)
                            }}
                            size={"16px"}
                        />
                    </Box>
                }
                <PrettyFileName path={props.node.file.absolutePath} />
            </Flex>
        }
        children={children}
    />;
}

/* Note(Jonas): I'm not sure this should be pushed down here, but I can't put it into a .JSON file due to the regexes */
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