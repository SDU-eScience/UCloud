import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {useSelector} from "react-redux";
import {editor} from "monaco-editor";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {TreeAction, TreeApi} from "@/ui-components/Tree";
import {Box, ExternalLink, Flex, FtIcon, Icon, Image, Label, Markdown, Select, Truncate} from "@/ui-components";
import {fileName, pathComponents} from "@/Utilities/FileUtilities";
import {capitalized, copyToClipboard, doNothing, errorMessageOrDefault, extensionFromPath, getLanguageList, languageFromExtension, populateLanguages} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {VimEditor} from "@/Vim/VimEditor";
import {VimWasm} from "@/Vim/vimwasm";
import * as Heading from "@/ui-components/Heading";
import {TooltipV2} from "@/ui-components/Tooltip";
import {usePrettyFilePath} from "@/Files/FilePath";
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
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";

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

const monacoCache = new AsyncCache<any>();

export async function getMonaco() {
    return monacoCache.retrieve("", async () => {
        const monaco = await (import("monaco-editor"));

        const editorWorker = (await import('monaco-editor/esm/vs/editor/editor.worker?worker')).default;
        const jsonWorker = (await import('monaco-editor/esm/vs/language/json/json.worker?worker')).default;
        const cssWorker = (await import('monaco-editor/esm/vs/language/css/css.worker?worker')).default;
        const htmlWorker = (await import('monaco-editor/esm/vs/language/html/html.worker?worker')).default;
        const tsWorker = (await import('monaco-editor/esm/vs/language/typescript/ts.worker?worker')).default;

        populateLanguages(monaco.languages.getLanguages().map(l =>
            ({language: l.id, extensions: l.extensions?.map(it => it.slice(1)) ?? []}))
        );

        self.MonacoEnvironment = {
            getWorker: function (workerId, label) {
                switch (label) {
                    case 'json':
                        return new jsonWorker();
                    case 'css':
                    case 'scss':
                    case 'less':
                        return new cssWorker();
                    case 'html':
                    case 'handlebars':
                    case 'razor':
                        return new htmlWorker();
                    case 'typescript':
                    case 'javascript':
                        return new tsWorker();
                    default:
                        return new editorWorker();
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
    invalidateTree: (path: string) => Promise<void>;
}

const SETTINGS_PATH = "xXx__/SETTINGS\\__xXx";
const RELEASE_NOTES_PATH = "xXx__/RELEASE_NOTES\\__xXx";

const CURRENT_EDITOR_VERSION = "0.1";
const EDITOR_VERSION_STORAGE_KEY = "editor-version";
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
    renamingFile?: string;
    onRename?: (args: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}) => Promise<boolean>;
    readOnly: boolean;
}> = props => {
    const [overridenSyntaxes, setOverridenSyntaxes] = React.useState<Record<string, string>>({});
    const [languageList, setLanguageList] = React.useState(getLanguageList().map(l => ({language: l.language, displayName: toDisplayName(l.language)})));
    const [engine, setEngine] = useState<EditorEngine>(localStorage.getItem("editor-engine") as EditorEngine ?? "monaco");
    const [state, dispatch] = useReducer(singleEditorReducer, 0, () => defaultEditor(props.vfs, props.title, props.initialFolderPath, props.initialFilePath));
    const editorView = useRef<HTMLDivElement>(null);
    const currentTheme = useSelector((red: ReduxObject) => red.sidebar.theme);
    const monacoInstance = useMonaco(engine === "monaco");
    const [editor, setEditor] = useState<IStandaloneCodeEditor | null>(null);
    const monacoRef = useRef<any>(null);
    const [tabs, setTabs] = useState<{open: string[], closed: string[]}>({
        open: [state.currentPath],
        closed: [],
    });

    const prettyPath = usePrettyFilePath(state.currentPath);
    if (state.currentPath === SETTINGS_PATH) {
        usePage("Settings", SidebarTabId.FILES);
    } else if (state.currentPath === RELEASE_NOTES_PATH) {
        usePage("Release notes", SidebarTabId.FILES);
    } else if (state.currentPath === "") {
        usePage("Preview", SidebarTabId.FILES);
    } else {
        usePage(fileName(prettyPath), SidebarTabId.FILES);
    }

    const {showReleaseNoteIcon, onShowReleaseNotesShown} = useShowReleaseNoteIcon();

    const [operations, setOperations] = useState<Operation<any, undefined>[]>([]);
    const anyTabOpen = tabs.open.length > 0;
    const isSettingsOpen = state.currentPath === SETTINGS_PATH && anyTabOpen;
    const isReleaseNotesOpen = state.currentPath === RELEASE_NOTES_PATH && anyTabOpen;
    const settingsOrReleaseNotesOpen = isSettingsOpen || isReleaseNotesOpen;

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
        setLanguageList(getLanguageList().map(l => ({language: l.language, displayName: toDisplayName(l.language)})));
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
        const dataPromise = (path === SETTINGS_PATH || path === RELEASE_NOTES_PATH) ?
            "" :
            cachedContent !== undefined ?
                Promise.resolve(cachedContent) :
                props.vfs.readFile(path);

        const syntaxExtension = findNode(state.sidebar.root, path)?.file?.requestedSyntax;
        const syntax = overridenSyntaxes[path] ?? languageFromExtension(syntaxExtension ?? extensionFromPath(path));

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
    }, [state, props.vfs, dispatch, reloadBuffer, readBuffer, props.onOpenFile, overridenSyntaxes]);

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
                openTab(path);
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

        // Define the syntax highlighting rules for Jinja2
        m.languages.setMonarchTokensProvider('jinja2', jinja2monarchTokens);

        const editor: IStandaloneCodeEditor = m.editor.create(node, {
            value: "",
            language: languageFromExtension(extensionFromPath(state.currentPath)),
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
    }, [monacoInstance]);

    useLayoutEffect(() => {
        openFile(state.currentPath, false);
    }, []);

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
            setTabs(tabs => {
                if (tabs.open.includes(SETTINGS_PATH)) {
                    return tabs;
                } else return {open: [...tabs.open, SETTINGS_PATH], closed: tabs.closed};
            })
            dispatch({type: "EditorActionOpenFile", path: SETTINGS_PATH})
        });
    }, []);

    const toggleReleaseNotes = useCallback(() => {
        saveBufferIfNeeded().then(() => {
            setTabs(tabs => {
                if (tabs.open.includes(RELEASE_NOTES_PATH)) {
                    return tabs;
                } else return {open: [...tabs.open, RELEASE_NOTES_PATH], closed: tabs.closed};
            })
            dispatch({type: "EditorActionOpenFile", path: RELEASE_NOTES_PATH})
        });

        onShowReleaseNotesShown();
    }, []);

    const openTab = React.useCallback(async (path: string) => {
        if (state.currentPath === path) return;
        await openFile(path, true);
        const fileWasFetched = state.cachedFiles[path] != null;
        if (fileWasFetched) {
            setTabs(tabs => {
                if (tabs.open.includes(path)) {
                    return tabs;
                } else return {open: [...tabs.open, path], closed: tabs.closed};
            });
        }
    }, [state.currentPath]);

    const closeTab = useCallback((path: string, index: number) => {
        setTabs(tabs => {
            const result = tabs.open.filter(tabTitle => tabTitle !== path);
            if (state.currentPath === path) {
                const preceedingPath = result.at(index - 1);
                if (preceedingPath) {
                    openFile(preceedingPath, true);
                }
            }
            const closed = tabs.closed;
            if (!closed.includes(path)) closed.push(path);
            return {open: result, closed};
        });
    }, [state.currentPath]);

    const openTabOperationWindow = useRef<(x: number, y: number) => void>(noopCall)

    const openTabOperations = React.useCallback((title: string | undefined, position: {x: number; y: number;}) => {
        const ops = tabOperations(title, setTabs, openTab, tabs.closed.length > 0, state.currentPath);
        setOperations(ops);
        openTabOperationWindow.current(position.x, position.y);
    }, [tabs, state.currentPath]);

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

    const onRename = React.useCallback(async (args: {newAbsolutePath: string; oldAbsolutePath: string; cancel: boolean;}) => {
        if (!props.onRename) return;

        const fileUpdated = await props.onRename(args);
        if (fileUpdated) {
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
    }, []);

    const doOverrideLanguage = React.useCallback((path: string, language: string) => {
        setOverridenSyntaxes(syntaxes => {
            if (state.currentPath === path) {
                const monaco = monacoRef.current;
                const editor = editorRef.current;
                if (monaco && editor) {
                    monaco.editor.setModelLanguage(editor.getModel(), language);
                }
            }
            return {...syntaxes, [path]: language};
        });
    }, [state.currentPath]);

    // Current path === "", can we use this as empty/scratch space, or is this in use for Scripts/Workflows
    const showEditorHelp = tabs.open.length === 0;

    const selectedOverride = overridenSyntaxes[state.currentPath] ?? languageFromExtension(extensionFromPath(state.currentPath));

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
            renamingFile={props.renamingFile}
            onRename={onRename}
        />

        <div className={"main-content"}>
            <div className={"title-bar-code"} style={{minWidth: "400px", paddingRight: "12px", width: `calc(100vw - 250px - var(--sidebarWidth) - 20px)`}}>
                <div onContextMenu={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    openTabOperations(undefined, {x: e.clientX, y: e.clientY});
                }} style={{display: "flex", height: "32px", maxWidth: `calc(100% - 48px)`, overflowX: "auto", width: "100%"}}>
                    {tabs.open.map((t, index) =>
                        <EditorTab
                            key={t}
                            isDirty={false /* TODO */}
                            isActive={t === state.currentPath}
                            onActivate={() => openTab(t)}
                            onContextMenu={e => {
                                e.preventDefault();
                                e.stopPropagation();
                                openTabOperations(t, {x: e.clientX, y: e.clientY});
                            }}
                            close={() => {
                                /* if (fileIsDirty) promptSaveFileWarning() else */
                                closeTab(t, index);
                            }}
                            children={t}
                        />
                    )}

                    <Box mx="auto" />
                    {tabs.open.length === 0 || settingsOrReleaseNotesOpen || props.customContent ? null : <Box width={"180px"}>
                        <RichSelect
                            fullWidth
                            items={languageList}
                            keys={["language"]}
                            dropdownWidth="180px"
                            FullRenderSelected={p =>
                                <Flex borderRight={"1px solid var(--borderColor)"} borderLeft={"1px solid var(--borderColor)"} height="32px" width="200px">
                                    <LanguageItem {...p} /><Icon mr="6px" ml="auto" mt="8px" size="14px" name="chevronDownLight" />
                                </Flex>}
                            RenderRow={LanguageItem}
                            selected={{language: selectedOverride, displayName: toDisplayName(selectedOverride)}}
                            onSelect={element => {
                                doOverrideLanguage(state.currentPath, element.language)
                            }}
                        />
                    </Box>}
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
                    {tabs.open.length === 0 || state.currentPath === SETTINGS_PATH || state.currentPath === RELEASE_NOTES_PATH || props.customContent ? null :
                        props.toolbarBeforeSettings
                    }
                    {!hasFeature(Feature.EDITOR_VIM) ? null : <>
                        {showReleaseNoteIcon ? <TooltipV2 tooltip={"See release notes"} contentWidth={100}>
                            <Icon name={"heroGift"} size={"20px"} cursor={"pointer"} onClick={toggleReleaseNotes} />
                        </TooltipV2> : null}
                        <TooltipV2 tooltip={"Settings"} contentWidth={100}>
                            <Icon name={"heroCog6Tooth"} size={"20px"} cursor={"pointer"} onClick={toggleSettings} />
                        </TooltipV2>
                    </>}
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

                    </Flex> : null}
                {isReleaseNotesOpen ? <Box p="18px"><Markdown children={EditorReleaseNotes} /></Box> : null}
                <>
                    {/* 
                        Note(Jonas): For some reason, if we have the showEditorHelp in a different terniary expression, this breaks the monaco-instance
                        I would assume that the `isSettingsOpen`-flag would cause the same issue, but it doesn't for some reason. 
                        06/02/2025 - Maybe it does?
                    */}
                    {showEditorHelp && props.help ? props.help : null}
                    <div style={{
                        display: props.showCustomContent || (showEditorHelp && props.help) || settingsOrReleaseNotesOpen ? "none" : "block",
                        width: "100%",
                        height: "100%",
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
            </div>
        </div>
    </div >;
};

/* TODO(Jonas): Improve parameters this is... not good */
function tabOperations(
    tabPath: string | undefined,
    setTabs: React.Dispatch<React.SetStateAction<{open: string[], closed: string[]}>>,
    openTab: (path: string) => void,
    anyTabsClosed: boolean,
    currentPath: string,
): Operation<any>[] {
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
            shortcut: ShortcutKey.U,
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
            enabled: () => true, /* anyTabsOpen > 0 */
            shortcut: ShortcutKey.U,
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
        shortcut: ShortcutKey.W,
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
        shortcut: ShortcutKey.E,
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
        shortcut: ShortcutKey.R,
    }, /* {
        text: "Close saved",
        onClick: () => console.log("TODO"),
        enabled: () => true,
        shortcut: ShortcutKey.T,
    }, */ {
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
        shortcut: ShortcutKey.U,
    }, {
        text: "Copy path to clipboard",
        onClick: () => copyToClipboard({value: tabPath, message: ""}),
        enabled: () => true,
        shortcut: ShortcutKey.U,
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
        shortcut: ShortcutKey.U,
    }];
}

const LEFT_MOUSE_BUTTON = 0;
const MIDDLE_MOUSE_BUTTON = 1;
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
    close(): void;
}>): React.ReactNode {
    const [hovered, setHovered] = useState(false);

    const isSettings = title === SETTINGS_PATH;
    const isReleaseNotes = title === RELEASE_NOTES_PATH;

    const onClose = React.useCallback((e: React.SyntheticEvent<HTMLDivElement>) => {
        e.preventDefault();
        e.stopPropagation();

        close();
    }, [close]);

    const tabTitle = (
        isSettings ? "Settings" :
            isReleaseNotes ? "Release notes" :
                fileName(title as string)
    );

    return (
        <Flex onContextMenu={onContextMenu} className={EditorTabClass} mt="auto" data-active={isActive} minWidth="250px" width="250px" onClick={e => {
            if (e.button === LEFT_MOUSE_BUTTON) {
                onActivate();
            } else if (e.button == MIDDLE_MOUSE_BUTTON) {
                onClose(e);
            }
        }}>
            {isSettings ? <Icon name="heroCog6Tooth" size="18px" />
                : isReleaseNotes ? <Icon name="heroGift" size="18px" />
                    : <FullpathFileLanguageIcon filePath={tabTitle} />}
            <Truncate ml="8px" width="50%">{isSettings ? "Editor settings" : tabTitle}</Truncate>
            <Icon
                className={IconHoverBlockClass}
                onMouseEnter={() => setHovered(true)}
                onMouseLeave={() => setHovered(false)}
                cursor="pointer" name={isDirty && !hovered ? "circle" : "close"}
                size={16}
                onClick={onClose} />
        </Flex >
    );
}

const IconHoverBlockClass = injectStyle("icon-hover-block", k => `
    ${k} {
        padding: 4px;
    }

    ${k}:hover {
        background-color: var(--secondaryDark);
        border-radius: 4px;
    }
`);

const EditorTabClass = injectStyle("editor-tab-class", k => `
    ${k} {
        height: 32px;
        font-size: 12px;
        padding-left: 12px;
        padding-right: 12px;
        cursor: pointer;
        user-select: none;
        border-right: 2px solid var(--borderColor);
    }

    ${k} > * {
        margin-top: auto;
        margin-bottom: auto;
    }

    ${k}[data-active="true"], ${k}:hover  {
        background-color: var(--borderColor);
    }
`);

const fallbackIcon = toIconPath("default");
const LanguageItem: RichSelectChildComponent<{language: string; displayName: string}> = props => {
    const language = props.element?.language;
    if (!language) return null;
    return <Flex my="4px" onClick={props.onSelect} {...props.dataProps}>
        <FileLanguageIcon language={language} /> {props.element?.displayName}
    </Flex>;
}

export function FullpathFileLanguageIcon({filePath, size}: {filePath: string; size?: string;}) {
    const language = languageFromExtension(extensionFromPath(filePath));
    return <FileLanguageIcon language={language} size={size} m="" />
}

function FileLanguageIcon({language, size = "18px", m = "2px 8px 0px 8px"}: {language: string; size?: string; m?: string;}): React.ReactNode {
    const [iconPath, setIconPath] = useState(toIconPath(language ?? ""));

    React.useEffect(() => {
        if (language) {
            setIconPath(toIconPath(language));
        }
    }, [language]);

    return <Image
        m={m}
        background={"var(--successContrast)"}
        borderRadius={"4px"}
        height={size}
        width={size}
        onError={() => setIconPath(fallbackIcon)}
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

    return "/file-icons/" + lang + ".svg";
}

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

type EditorOptionPair<T extends EditorOption> = [string, T, editor.FindComputedEditorOptionValueById<T>[]];
const AvailableSettings: [
    EditorOptionPair<EditorOption.fontSize>,
    EditorOptionPair<EditorOption.fontWeight>,
    EditorOptionPair<EditorOption.wordWrap>,
] = [
        ["Font size", EditorOption.fontSize, [8, 10, 12, 14, 16, 18, 20, 22]],
        ["Font weight", EditorOption.fontWeight, ["200", "400", "600", "800", "bold"]],
        ["Word wrap", EditorOption.wordWrap, ["wordWrapColumn", "on", "off", "bounded"]],
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
        {AvailableSettings.map(([name, setting, options]) => <div key={setting}>
            {name}
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

function useShowReleaseNoteIcon() {
    const [showReleaseNoteIcon, setShowReleaseNotePrompt] = useState(false);

    React.useEffect(() => {
        const lastUsedEditorVersion = localStorage.getItem(EDITOR_VERSION_STORAGE_KEY)
        if (!localStorage.getItem(EDITOR_VERSION_STORAGE_KEY)) {
            localStorage.setItem(EDITOR_VERSION_STORAGE_KEY, CURRENT_EDITOR_VERSION);
        }

        if (CURRENT_EDITOR_VERSION !== lastUsedEditorVersion) {
            setShowReleaseNotePrompt(true);
        }
    }, []);

    const onShowReleaseNotesShown = React.useCallback(() => {
        localStorage.setItem(EDITOR_VERSION_STORAGE_KEY, CURRENT_EDITOR_VERSION);
        setShowReleaseNotePrompt(false);
    }, []);

    return {showReleaseNoteIcon, onShowReleaseNotesShown};
}

const EditorReleaseNotes = `
# Preview editor


## 1.1 - New fancy feature


- Auto-save, maybe


## 1.0 initial release 


- Nothing of note :(

`