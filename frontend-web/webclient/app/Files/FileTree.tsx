import * as React from "react";
import {injectStyle} from "@/Unstyled";
import {doNothing} from "@/UtilityFunctions";
import {usePrettyFilePath} from "./FilePath";
import {Box, Flex, Icon, Input, Truncate} from "@/ui-components";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import {FullpathFileLanguageIcon} from "@/Editor/Editor";
import {FileIconHint} from ".";
import {VirtualizedTree, VirtualizedTreeApi} from "@/ui-components/VirtualizedTree";
import {ActionEntry, ActionMenu} from "@/ui-components/Actions";

export interface EditorSidebarNode {
    file: VirtualFile;
    children: EditorSidebarNode[];
    childrenLoaded?: boolean;
}

export interface VirtualFile {
    absolutePath: string;
    isDirectory: boolean;
    requestedSyntax?: string;
    fileHint?: FileIconHint;
}

interface FileTreeProps {
    tree: React.RefObject<VirtualizedTreeApi | null>
    onFileActivated(file: VirtualFile): void;
    onDirectoryPrefetch(file: VirtualFile): void;
    root: EditorSidebarNode;
    initialFolder: string;
    initialFilePath?: string;
    actions?: (file?: VirtualFile) => ActionEntry<VirtualFile, null>[];
    width?: string;
    canResize?: boolean;
    visible?: boolean;
    fileHeaderOperations?: React.ReactNode;
    renamingFile?: string;
    onRename?: (args: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}) => void;
}

export function FileTree({tree, onFileActivated, onDirectoryPrefetch, root, ...props}: FileTreeProps) {
    const initialWidth = parseFloat(props.width ?? "250px") || 250;
    const [treeWidth, setTreeWidth] = React.useState(initialWidth);
    const isResizing = React.useRef(false);
    const treeRef = React.useRef<HTMLDivElement | null>(null);

    const setWidth = React.useCallback((width: number) => {
        const left = treeRef.current?.getBoundingClientRect().left ?? 0;
        const maxWidth = Math.max(180, window.innerWidth - left - 600);
        setTreeWidth(Math.min(Math.max(width, 180), maxWidth));
    }, []);

    const pointerMoveHandler = React.useCallback((e: PointerEvent) => {
        if (!isResizing.current) return;
        const left = treeRef.current?.getBoundingClientRect().left ?? 0;
        setWidth(e.clientX - left);
    }, [setWidth]);

    const stopResize = React.useCallback(() => {
        if (!isResizing.current) return;
        isResizing.current = false;
        window.removeEventListener("pointermove", pointerMoveHandler);
        window.removeEventListener("pointerup", stopResize);
        window.removeEventListener("pointercancel", stopResize);
        window.removeEventListener("blur", stopResize);
    }, [pointerMoveHandler]);

    React.useEffect(() => stopResize, [stopResize]);

    const onResizeStart = React.useCallback((e: React.PointerEvent<HTMLDivElement>) => {
        if (!props.canResize || e.button !== 0) return;
        e.preventDefault();
        isResizing.current = true;
        window.addEventListener("pointermove", pointerMoveHandler);
        window.addEventListener("pointerup", stopResize);
        window.addEventListener("pointercancel", stopResize);
        window.addEventListener("blur", stopResize);
    }, [pointerMoveHandler, props.canResize, stopResize]);

    const [contextMenu, setContextMenu] = React.useState<{
        file?: VirtualFile;
        left: number;
        top: number;
    } | null>(null);

    const style = {
        "--tree-width": `${treeWidth}px`,
        display: props.visible === false ? "none" : undefined,
    } as React.CSSProperties;

    const openOperations = React.useRef<(left: number, top: number) => void>(doNothing);
    const onContextMenu = React.useCallback((ev: React.MouseEvent, file?: VirtualFile) => {
        ev.preventDefault();
        setContextMenu({file, left: ev.clientX, top: ev.clientY});
    }, []);

    React.useLayoutEffect(() => {
        if (!contextMenu) return;
        openOperations.current(contextMenu.left, contextMenu.top);
        setContextMenu(null);
    }, [contextMenu]);

    const prettyInitialFolderPath = usePrettyFilePath(props.initialFolder);
    const initialExpandedIds = React.useMemo(() => parentPathsWithin(
        props.initialFilePath,
        props.initialFolder,
    ), [props.initialFilePath, props.initialFolder]);

    const renderNode = React.useCallback((node: EditorSidebarNode, state: {expanded: boolean; focused: boolean; selected: boolean; toggle(): void}) => {
        return <FileTreeNodeContent
            node={node}
            branch={isDirectoryNode(node)}
            expanded={state.expanded}
            treeWidth={treeWidth}
            renamingFile={props.renamingFile}
            onRename={props.onRename}
            onToggle={state.toggle}
        />;
    }, [props.onRename, props.renamingFile, treeWidth]);

    return <div ref={treeRef} onContextMenu={e => onContextMenu(e, undefined)} style={style} className={FileTreeClass}>
        <Flex alignItems={"center"} px="8px" className="title-bar" gap={"8px"}>
            <Icon size={"18px"} name={"heroFolder"} color={"FtFolderColor"} />
            <Box minWidth={0} flexGrow={1}><Truncate width="100%" title={prettyInitialFolderPath}>{fileName(prettyInitialFolderPath)}</Truncate></Box>
            {props.fileHeaderOperations ? (
                <>
                    {props.fileHeaderOperations}
                </>
            ) : null}
        </Flex>
        <Box className="tree-content">
            <VirtualizedTree
                apiRef={tree}
                nodes={root.children}
                getId={getNodeId}
                getChildren={getNodeChildren}
                isBranch={isDirectoryNode}
                renderNode={renderNode}
                ariaLabel={getNodeLabel}
                label="Files"
                initialExpandedIds={initialExpandedIds}
                initialSelectedId={props.initialFilePath}
                onActivate={node => {
                    if (!node.file.isDirectory) onFileActivated(node.file);
                }}
                onPrefetch={node => onDirectoryPrefetch(node.file)}
                onContextMenu={(event, node) => {
                    event.preventDefault();
                    event.stopPropagation();
                    onContextMenu(event, node.file);
                }}
            />
            <ActionMenu
                actions={props.actions?.(contextMenu?.file) ?? []}
                openFnRef={openOperations}
                selected={[]}
                callbacks={null}
                trigger={null}
            />
        </Box>
        {props.canResize ? <div className="tree-resizer" onPointerDown={onResizeStart} /> : null}
    </div>
}

const FileTreeNodeContent: React.FunctionComponent<{
    node: EditorSidebarNode;
    branch: boolean;
    expanded: boolean;
    treeWidth: number;
    renamingFile?: string;
    onRename?: (args: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}) => void;
    onToggle(): void;
}> = ({node, branch, expanded, treeWidth, renamingFile, onRename, onToggle}) => {
    const didRename = React.useRef(false);
    const renameInput = React.useRef<HTMLInputElement | null>(null);

    const renameFile = React.useCallback((newName: string, cancel: boolean) => {
        didRename.current = true;
        newName = newName.trim();
        if (!cancel && newName.length === 0) cancel = true;
        const parentPath = getParentPath(node.file.absolutePath);
        const newFullPath = parentPath + newName;
        onRename?.({
            newAbsolutePath: newFullPath,
            oldAbsolutePath: node.file.absolutePath,
            cancel
        });
    }, [node.file.absolutePath, onRename]);

    const prettyPath = usePrettyFilePath(node.file.absolutePath);

    const isRenaming = renamingFile === node.file.absolutePath;

    React.useLayoutEffect(() => {
        if (!isRenaming) return;
        didRename.current = false;

        const initialValue = fileName(node.file.absolutePath);
        const extensionStart = initialValue.lastIndexOf(".");
        const selectionEnd = extensionStart === -1 ? initialValue.length : extensionStart;
        renameInput.current?.focus();
        renameInput.current?.setSelectionRange(0, selectionEnd);
    }, [isRenaming, node.file.absolutePath]);

    const toggleFromIcon = React.useCallback((event: React.MouseEvent) => {
        event.stopPropagation();
        if (event.detail > 1) return;
        onToggle();
    }, [onToggle]);

    const stopDoubleClick = React.useCallback((event: React.MouseEvent) => {
        event.stopPropagation();
    }, []);

    return <Flex gap="8px" alignItems="center" fontSize="12px" minWidth={0} width="100%">
        <Flex width="16px" flexShrink={0} justifyContent="center">
            {branch ? <Icon
                name="heroChevronDown"
                color="textPrimary"
                size={14}
                rotation={expanded ? 0 : -90}
                cursor="pointer"
                onClick={toggleFromIcon}
                onDoubleClick={stopDoubleClick}
            /> : null}
        </Flex>
        {node.file.isDirectory ?
            <Icon
                name={expanded && branch ? "heroFolderOpen" : "heroFolder"}
                color="FtFolderColor"
                size={16}
                cursor={branch ? "pointer" : undefined}
                onClick={branch ? event => {
                    event.stopPropagation();
                    if (event.detail > 1) return;
                    onToggle();
                } : undefined}
                onDoubleClick={branch ? event => event.stopPropagation() : undefined}
            /> :
            <FullpathFileLanguageIcon filePath={node.file.absolutePath} size="16px" />
        }

        {isRenaming ?
            <Input
                inputRef={renameInput}
                autoFocus
                noBorder
                style={{padding: 0, height: "auto", boxShadow: "none", backgroundColor: "transparent"}}
                onBlur={e => {
                    e.preventDefault();
                    if (didRename.current) return;
                    renameFile(e.target["value"], false);
                }}
                onKeyDown={e => {
                    e.stopPropagation();
                    if (e.key === "Enter") renameFile(e.target["value"], false);
                    else if (e.key === "Escape") renameFile("", true);
                }}
                defaultValue={fileName(node.file.absolutePath)}
                width={1}
            /> :
            <Truncate title={prettyPath} maxWidth={`${treeWidth - 88}px`}>{fileName(prettyPath)}</Truncate>}
    </Flex>;
}

function getNodeId(node: EditorSidebarNode): string {
    return node.file.absolutePath;
}

function getNodeChildren(node: EditorSidebarNode): readonly EditorSidebarNode[] {
    return node.children;
}

function isDirectoryNode(node: EditorSidebarNode): boolean {
    return node.file.isDirectory && (node.childrenLoaded !== true || node.children.length > 0);
}

function getNodeLabel(node: EditorSidebarNode): string {
    return fileName(node.file.absolutePath);
}

function parentPathsWithin(path: string | undefined, root: string): string[] {
    if (!path) return [];
    const normalizedRoot = root.replace(/\/$/, "");
    const result: string[] = [];
    let current = getParentPath(path).replace(/\/$/, "");
    while (current.startsWith(normalizedRoot) && current.length >= normalizedRoot.length) {
        if (current !== normalizedRoot) result.push(current);
        if (current === normalizedRoot) break;
        current = getParentPath(current).replace(/\/$/, "");
    }
    return result;
}

const FileTreeClass = injectStyle("file-tree", k => `
    ${k} {
        position: relative;
        width: var(--tree-width);
        max-width: var(--tree-width);
        height: 100%;
        min-height: 0;
        display: flex;
        flex-direction: column;
        flex-shrink: 0;
        overflow: hidden;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: var(--backgroundDefault);
    }

    ${k} > .tree-content {
        min-height: 0;
        flex: 1 1 auto;
    }

    ${k} .tree-resizer {
        width: 8px;
        height: 100%;
        position: absolute;
        top: 0;
        right: -4px;
        background: transparent;
        cursor: col-resize;
        touch-action: none;
        z-index: 1;
    }

    ${k} > .tree-header {
        flex: none;
    }
`);
