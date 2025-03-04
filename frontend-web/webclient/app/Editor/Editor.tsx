import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {useSelector} from "react-redux";
import {editor} from "monaco-editor";
import {Uri} from "monaco-editor";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {injectStyle} from "@/Unstyled";
import {TreeAction, TreeApi} from "@/ui-components/Tree";
import {Box, Flex, FtIcon, Icon, Image, Markdown, Select, Truncate} from "@/ui-components";
import {fileName, pathComponents} from "@/Utilities/FileUtilities";
import {capitalized, copyToClipboard, errorMessageOrDefault, extensionFromPath, getLanguageList, languageFromExtension, populateLanguages} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
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
import {RichSelect, RichSelectChildComponent, RichSelectProps} from "@/ui-components/RichSelect";
import {initVimMode, VimMode} from "monaco-vim";
import {addStandardDialog} from "@/UtilityComponents";

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
        const monaco = await import("monaco-editor");


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

const EditorClass = injectStyle("editor", k => `
    ${k} {
        display: flex;
        width: 100%;
        max-width: 100%;
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
    | "monaco";

export interface EditorApi {
    path: string;
    notifyDirtyBuffer: () => Promise<void>;
    openFile: (path: string) => void;
    invalidateTree: (path: string) => Promise<void>;
    onFileSaved: (path: string) => void;
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
    onRequestSave: (path: string) => Promise<void>;
    readOnly: boolean;
    dirtyFileCountRef: React.MutableRefObject<number>;
}> = props => {

    const help = props.help ?? <></>;
    const [activeSyntax, setActiveSyntax] = React.useState("");
    const savedAtAltVersionId = React.useRef<Record<string, number>>({});
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

    const [dirtyFiles, setDirtyFiles] = React.useState<Set<string>>(new Set());

    const prettyPath = usePrettyFilePath(state.currentPath, !props.vfs.isReal());
    if (state.currentPath === SETTINGS_PATH) {
        usePage("Settings", SidebarTabId.FILES);
    } else if (state.currentPath === RELEASE_NOTES_PATH) {
        usePage("Release notes", SidebarTabId.FILES);
    } else if (state.currentPath === "") {
        usePage("Preview", SidebarTabId.FILES);
    } else {
        usePage(fileName(prettyPath), SidebarTabId.FILES);
    }

    const disposeModels = React.useCallback(() => {
        if (monacoRef.current) {
            for (const m of monacoRef.current.editor.getModels()) {
                m.dispose();
            }
        }
    }, []);

    const {showReleaseNoteIcon, onShowReleaseNotesShown} = useShowReleaseNoteIcon();

    const [operations, setOperations] = useState<Operation<any, undefined>[]>([]);
    const anyTabOpen = tabs.open.length > 0;
    const isSettingsOpen = state.currentPath === SETTINGS_PATH && anyTabOpen;
    const isReleaseNotesOpen = state.currentPath === RELEASE_NOTES_PATH && anyTabOpen;
    const settingsOrReleaseNotesOpen = isSettingsOpen || isReleaseNotesOpen;

    // NOTE(Dan): This code is quite ref heavy given that the components we are controlling are very much the
    // opposite of reactive. There isn't much we can do about this.
    const engineRef = useRef<EditorEngine>("monaco");
    const stateRef = useRef<EditorState>();
    const tree = useRef<TreeApi | null>(null);
    const [, setVimModeObject] = useState<any /* vimAdapter */>(null);
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

    const reloadBuffer = useCallback((name: string, content: string, syntax: string) => {
        const editor = editorRef.current;
        const engine = engineRef.current;
        switch (engine) {
            case "monaco": {
                if (!editor) return;
                const existingModel = getModelFromEditor(name);
                if (!existingModel) {
                    const model = monacoRef.current?.editor?.createModel(content, syntax, Uri.file(name));
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
                } else {
                    editor.setModel(existingModel);
                }
                break;
            }
        }
    }, [dirtyFiles]);

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
        return monacoRef.current?.editor.getModel(Uri.file(path));
    }, []);

    const openFile = useCallback(async (path: string, saveState: boolean): Promise<boolean> => {
        if (path === SETTINGS_PATH || path === RELEASE_NOTES_PATH) {
            dispatch({type: "EditorActionOpenFile", path});
            return false;
        }

        const cachedContent = state.cachedFiles[path] ?? getModelFromEditor(path)?.getValue();
        const dataPromise =
            cachedContent !== undefined ?
                Promise.resolve(cachedContent) :
                props.vfs.readFile(path);

        const syntaxExtension = findNode(state.sidebar.root, path)?.file?.requestedSyntax;
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
                    monaco.editor.setModelLanguage(editor.getModel(), syntax);
                    setActiveSyntax(syntax);
                }
            }

            return true;
        } catch (error) {
            snackbarStore.addFailure(errorMessageOrDefault(error, "Failed to fetch file"), false);
            return true; // What does true or false mean in this context?
        }
    }, [state, props.vfs, dispatch, reloadBuffer, readBuffer, props.onOpenFile, dirtyFiles, state.sidebar.root]);

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

        if (state.currentPath === "" || state.currentPath === "/") return;

        if (engine === "monaco" && editor == null) return;

        const res = await readBuffer();
        if (didUnmount.current) return;

        props.vfs.setDirtyFileContent(state.currentPath, res);
        props.onOpenFile?.(state.currentPath, res);
        dispatch({type: "EditorActionSaveState", editorState: null, oldContent: res, newPath: state.currentPath});
    }, [props.onOpenFile, state.currentPath]);

    const invalidateTree = useCallback(async (folder: string): Promise<void> => {
        const files = await props.vfs.listFiles(folder);
        dispatch({type: "EditorActionFilesLoaded", path: folder, files});
    }, [props.vfs]);

    const onFileSaved = React.useCallback((path: string) => {
        const model = getModelFromEditor(path);
        if (model) {
            savedAtAltVersionId.current[path] = model.getAlternativeVersionId();
            setDirtyFiles(dirtyFiles => {
                if (dirtyFiles.has(path)) dirtyFiles.delete(path);
                return new Set([...dirtyFiles]);
            });
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
            onFileSaved
        }
    }, []);


    useEffect(() => {
        if (props.apiRef) props.apiRef.current = api;
    }, [api, props.apiRef]);

    useEffect(() => {
        invalidateTree(props.initialFolderPath);
    }, []);

    useLayoutEffect(() => {
        document.querySelector(`.${EditorTabClass}[data-active=true]`)?.scrollIntoView();;
    }, [state.currentPath]);

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
        m.languages.register({id: "jinja2"});

        // Define the syntax highlighting rules for Jinja2
        m.languages.setMonarchTokensProvider('jinja2', jinja2monarchTokens);

        const editor: IStandaloneCodeEditor = m.editor.create(node, {
            language: languageFromExtension(extensionFromPath(state.currentPath)),
            readOnly: props.readOnly,
            readOnlyMessage: {
                // Note(Jonas): Setting this to null will not behave well, so this seems the best.
                value: ""
            },
            minimap: {enabled: false},
            renderLineHighlight: "none",
            fontFamily: "Jetbrains Mono",
            fontSize: 14,
            theme: currentTheme === "light" ? "light" : "ucloud-dark",
            wordWrap: "off",
            ...getEditorOptions(),
        });

        editor.updateOptions({readOnly: props.readOnly});

        if (props.readOnly) {
            setReadonlyWarning(editor);
        }

        setEditor(editor);

        const vimEnabled = getEditorOption("vim") === true;
        if (vimEnabled) {
            setVimModeObject(initVimMode(editor, getStatusBarElement()));
        }
    }, [monacoInstance]);

    useLayoutEffect(() => {
        // NOTE(Dan): This timer is needed to make sure that if the file opens faster than the engine can initialize
        // then we do reload the file. See the branch when returns early in openFile.
        let timer = -1;
        const fn = async () => {
            const res = await openFile(state.currentPath, false);
            if (!res) timer = window.setTimeout(fn, 50);
        };
        timer = window.setTimeout(fn, 50);

        return () => {
            window.clearTimeout(timer);
        };
    }, [state.sidebar.root]);

    const setVimMode = React.useCallback((active: boolean) => {
        setVimModeObject(vimModeObject => {
            updateEditorSetting("vim", active);
            if (active) {
                return initVimMode(editorRef.current, getStatusBarElement());
            } else {
                vimModeObject?.dispose();
                return null;
            }
        })
    }, []);

    useEffect(() => {
        const theme = currentTheme === "light" ? "light" : "ucloud-dark";
        monacoInstance?.editor?.setTheme(theme);
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
        const fileWasFetched = getModelFromEditor(path)?.getValue ?? state.cachedFiles[path] != null;
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
                } else {
                    dispatch({type: "EditorActionOpenFile", path: ""});
                }
            }

            const closed = tabs.closed;
            if (!closed.includes(path)) closed.push(path);

            getModelFromEditor(path)?.dispose();

            return {open: result, closed};
        });
    }, [state.currentPath]);

    const openTabOperationWindow = useRef<(x: number, y: number) => void>(noopCall)

    const openTabOperations = React.useCallback((title: string | undefined, position: {x: number; y: number;}) => {
        const ops = tabOperations(title, setTabs, openTab, tabs, dirtyFiles, state.currentPath);
        setOperations(ops);
        openTabOperationWindow.current(position.x, position.y);
    }, [tabs, state.currentPath, dirtyFiles]);

    useBeforeUnload((e: BeforeUnloadEvent): BeforeUnloadEvent => {
        // TODO(Jonas): Only handles closing window, not UCloud navigation 
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
            const newModel = monacoRef.current?.editor?.createModel(oldModel.getValue(), oldModel.getLanguageId(), Uri.file(args.newAbsolutePath));
            if (editor.getModel()?.uri.path === Uri.file(args.oldAbsolutePath).path) {
                editor.setModel(newModel);
                openTab(args.newAbsolutePath);
            }
            oldModel.dispose();
        }

        invalidateTree(props.initialFolderPath);
    }, []);

    const fullRenderSelectedSyntax = useCallback((p: RichSelectProps<{
        language: string;
        displayName: string;
    }>) => {
        return <Flex key={p.element?.language} borderRight={"1px solid var(--borderColor)"} borderLeft={"1px solid var(--borderColor)"} height="32px" width="180px">
            <LanguageItem key={p.element?.language} {...p} /><Icon mr="6px" ml="auto" mt="8px" size="14px" name="chevronDownLight" />
        </Flex>
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

    // VimMode.Vim.defineEx(name, shorthand, callback);
    VimMode.Vim.defineEx("write", "w", () => {
        saveBufferIfNeeded();
        props.onRequestSave(state.currentPath);
        onFileSaved(state.currentPath);
    });

    VimMode.Vim.defineEx("quit", "q", (args, b, c) => {
        const idx = tabs.open.findIndex(it => it === state.currentPath);
        closeTab(state.currentPath, idx);
    });

    VimMode.Vim.defineEx("x-write-and-quit", "x", () => {
        saveBufferIfNeeded();
        props.onRequestSave(state.currentPath).then(() => {
            onFileSaved(state.currentPath);
            const idx = tabs.open.findIndex(it => it === state.currentPath);
            closeTab(state.currentPath, idx);
        });
    });

    VimMode.Vim.defineEx("e-open-file", "e", (a, b, c) => {
        if (b.args) {
            openTab(props.initialFolderPath + "/" + b.args.at(-1));
        }
    });

    // Current path === "", can we use this as empty/scratch space, or is this in use for Scripts/Workflows
    const showEditorHelp = tabs.open.length === 0;

    return <div className={EditorClass} onKeyDown={onKeyDown}>
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
            <div className={"title-bar-code"} style={{minWidth: "400px", paddingRight: "12px", width: "100%"}}>
                <div onContextMenu={e => {
                    e.preventDefault();
                    e.stopPropagation();
                    openTabOperations(undefined, {x: e.clientX, y: e.clientY});
                }} style={{display: "flex", height: "32px", maxWidth: `calc(100% - 48px)`, overflowX: "auto", width: "100%"}}>
                    {tabs.open.map((t, index) =>
                        <EditorTab
                            key={t}
                            isDirty={dirtyFiles.has(t)}
                            isActive={t === state.currentPath}
                            onActivate={() => openTab(t)}
                            onContextMenu={e => {
                                e.preventDefault();
                                e.stopPropagation();
                                openTabOperations(t, {x: e.clientX, y: e.clientY});
                            }}
                            close={() => {
                                if (dirtyFiles.has(t)) {
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
                            }}
                            children={t}
                        />
                    )}
                    <Box mx="auto" />
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
                {tabs.open.length === 0 || settingsOrReleaseNotesOpen || props.customContent ? null : <Box width={"180px"}>
                    <RichSelect
                        key={activeSyntax}
                        items={languageList}
                        keys={SyntaxSelectorKeys}
                        dropdownWidth="180px"
                        FullRenderSelected={fullRenderSelectedSyntax}
                        RenderRow={LanguageItem}
                        selected={selectedSynax}
                        onSelect={setModelLanguage}
                    />
                </Box>}
                <Flex alignItems={"center"} ml="16px" gap="16px">
                    {tabs.open.length === 0 || isReleaseNotesOpen || isSettingsOpen || props.customContent ? null :
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
                    <Flex gap={"32px"} maxHeight="calc(100% - 64px)" flexDirection={"column"} margin={64} width={"100%"} height={"100%"}>
                        <MonacoEditorSettings editor={editor} setVimMode={setVimMode} />
                    </Flex> : null}
                {isReleaseNotesOpen ? <Box p="18px" maxHeight="calc(100% - 64px)"><Markdown children={EditorReleaseNotes} /></Box> : null}
                <>
                    {showEditorHelp ? help : null}
                    <div style={{
                        display: props.showCustomContent || showEditorHelp || settingsOrReleaseNotesOpen ? "none" : "block",
                        width: "100%",
                        height: "100%",
                    }}>
                        <div className={"code"} ref={editorView} onFocus={() => tree?.current?.deactivate?.()} />
                    </div>

                    <div style={{
                        display: props.showCustomContent ? "block" : "none",
                        width: "100%",
                        height: "100%",
                        maxHeight: "calc(100% - 64px)",
                        padding: "16px",
                        overflow: "auto",
                    }}>{props.customContent}</div>
                </>
            </div>
            <div style={{display: !anyTabOpen || settingsOrReleaseNotesOpen ? "none" : undefined}} className={StatusBar} />
        </div>
    </div>;
};

/* TODO(Jonas): Improve parameters this is... not good */
function tabOperations(
    tabPath: string | undefined,
    setTabs: React.Dispatch<React.SetStateAction<{open: string[], closed: string[]}>>,
    openTab: (path: string) => void,
    tabs: {open: string[], closed: string[]},
    dirtyFiles: Set<string>,
    currentPath: string,
): Operation<any>[] {
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
            enabled: () => anyTabsOpen,
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
        shortcut: ShortcutKey.T,
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

const SyntaxSelectorKeys = ["language" as const];

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
        </Flex>
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

const StatusBar = injectStyle("status-bar", k => `
    ${k} {
        height: 24px;
        left: calc(var(--currentSidebarWidth) + 250px);
        width: 100%;
        background-color: var(--primaryMain);
        font-family: var(--monospace);
        color: white;
        padding-left: 4px;
    }

    ${k} input {
        background: transparent;
        border: none;
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
    return <Flex key={language} my="4px" onClick={props.onSelect} {...props.dataProps}>
        <FileLanguageIcon language={language} /> {props.element?.displayName}
    </Flex>;
}

export function FullpathFileLanguageIcon({filePath, size}: {filePath: string; size?: string;}) {
    const language = languageFromExtension(extensionFromPath(filePath));
    return <FileLanguageIcon key={language} language={language} size={size} m="" ext={extensionFromPath(filePath)} />
}

function FileLanguageIcon({language, ext, size = "18px", m = "2px 8px 0px 8px"}: {ext?: string; language: string; size?: string; m?: string;}): React.ReactNode {
    const [iconPath, setIconPath] = useState(toIconPath(language ?? ""));
    const [didError, setError] = useState(false);

    React.useEffect(() => {
        if (language) {
            setIconPath(toIconPath(language));
        }
    }, [language]);

    if (didError && ext) {
        return <FtIcon fileIcon={{type: "FILE", ext}} size={"18px"} />
    }

    return <Image
        m={m}
        background={"var(--successContrast)"}
        borderRadius={"4px"}
        height={size}
        width={size}
        onError={() => {
            setError(true);
            setIconPath(fallbackIcon)
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

function MonacoEditorSettings({editor, setVimMode}: {editor: IStandaloneCodeEditor | null, setVimMode(enable: boolean): void;}) {
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
        <div>
            Allow file editing
            <Select defaultValue={allowEditing() ? "Allow" : "Disallow"} onChange={e => {
                const canEdit = e.target.value === "Allow";
                setOption(EditorOption.readOnly, !canEdit);

                if (!canEdit) setReadonlyWarning(editor);

                setAllowEditing(canEdit.toString());
            }}>
                <option value={"Allow"}>Allow</option>
                <option value={"Disallow"}>Disallow</option>
            </Select>
        </div>
        <div>
            Vim mode
            <Select defaultValue={getEditorOption("vim") ? "Enabled" : "Disabled"} onChange={e => setVimMode(e.target.value === "Enabled")}>
                <option value="Enabled">Enabled</option>
                <option value="Disabled">Disabled</option>
            </Select>
        </div>
        {!getEditorOption("vim") ? null : (
            "So much work to do here"
        )}
    </>;
}

interface StoredSettings {
    fontSize?: number;
    fontWeight?: string;
    wordWrap?: string;
    vim?: boolean;
}

const PreviewEditorSettingsLocalStorageKey = "PreviewEditorSettings";
function getEditorOptions(): StoredSettings {
    return JSON.parse(localStorage.getItem(PreviewEditorSettingsLocalStorageKey) ?? "{}");
}

function getEditorOption<K extends keyof StoredSettings>(key: K): StoredSettings[K] {
    return getEditorOptions()[key];
}

function updateEditorSettings(settings: StoredSettings): void {
    const opts = getEditorOptions();
    storeEditorSettings({...opts, ...settings});
}

function updateEditorSetting<K extends keyof StoredSettings>(key: K, value: StoredSettings[K]): void {
    const opts = getEditorOptions();
    opts[key] = value;
    storeEditorSettings(opts);
}

function storeEditorSettings(settings: StoredSettings): void {
    localStorage.setItem(PreviewEditorSettingsLocalStorageKey, JSON.stringify(settings));
}

const ALLOW_EDITING_KEY = "EDITOR:ALWAYS_ALLOW_EDITING_KEY"
export function allowEditing() {
    return localStorage.getItem(ALLOW_EDITING_KEY) === "true";
}

function setAllowEditing(doAllow: string) {
    localStorage.setItem(ALLOW_EDITING_KEY, doAllow);
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

function setReadonlyWarning(editor: IStandaloneCodeEditor) {
    editor.onDidAttemptReadOnlyEdit(e => {
        addStandardDialog({
            title: "Enable editing?",
            message: "Editing files is disabled. This can be changed later in settings. Enable?",
            confirmText: "Enable",
            onConfirm() {
                editor.updateOptions({readOnly: false});
                setAllowEditing(true.toString());
            },
            cancelText: "Dismiss",
            addToFront: true,
        });
    });
}

function getStatusBarElement(): Element | null {
    return document.getElementsByClassName(StatusBar).item(0);
}

const EditorReleaseNotes = `
# Preview editor


## 1.1 - New fancy feature


- Auto-save, maybe


## 1.0 initial release 


- Nothing of note :(

`