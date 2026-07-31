import * as React from "react";
import {Tree, TreeAction, TreeApi, TreeNode} from "@/ui-components/Tree";
import {injectStyle} from "@/Unstyled";
import {Operation, Operations} from "@/ui-components/Operation";
import {doNothing, extensionFromPath} from "@/UtilityFunctions";
import {usePrettyFilePath} from "./FilePath";
import {Box, Flex, FtIcon, Input, Truncate} from "@/ui-components";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import {FullpathFileLanguageIcon} from "@/Editor/Editor";
import {FileIconHint} from ".";

export interface EditorSidebarNode {
    file: VirtualFile;
    children: EditorSidebarNode[];
}

export interface VirtualFile {
    absolutePath: string;
    isDirectory: boolean;
    requestedSyntax?: string;
    fileHint?: FileIconHint;
}

interface FileTreeProps {
    tree: React.RefObject<TreeApi | null>
    onTreeAction: ((row: HTMLElement, action: TreeAction) => void);
    onNodeActivated(open: boolean, row: HTMLElement): void;
    root: EditorSidebarNode;
    initialFolder: string;
    initialFilePath?: string;
    operations?: (file?: VirtualFile) => Operation<VirtualFile, null | undefined>[];
    width?: string;
    canResize?: boolean;
    visible?: boolean;
    fileHeaderOperations?: React.ReactNode;
    renamingFile?: string;
    onRename?: (args: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}) => void;
}

export function FileTree({tree, onTreeAction, onNodeActivated, root, ...props}: FileTreeProps) {
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

    const [operations, setOperations] = React.useState<Operation<VirtualFile, null | undefined>[]>([]);

    const style = {
        "--tree-width": `${treeWidth}px`,
        display: props.visible === false ? "none" : undefined,
    } as React.CSSProperties;

    const getOperations = React.useCallback((file?: VirtualFile) => {
        const {operations} = props;
        if (!operations) return;
        setOperations(operations(file));
    }, [props.operations]);

    const openOperations = React.useRef<(left: number, top: number) => void>(doNothing);
    const onContextMenu = React.useCallback((ev: React.MouseEvent, file?: VirtualFile) => {
        ev.preventDefault();
        getOperations(file);
        openOperations.current(ev.clientX, ev.clientY);
    }, [getOperations]);

    const prettyInitialFolderPath = usePrettyFilePath(props.initialFolder);

    return <div ref={treeRef} onContextMenu={e => onContextMenu(e, undefined)} style={style} className={FileTreeClass}>
        <Flex alignItems={"center"} pl="6px" className="title-bar" gap={"8px"}>
            <FtIcon fileIcon={{type: "DIRECTORY", ext: extensionFromPath(props.initialFolder)}} size={"18px"} />
            <Box minWidth={0} flexGrow={1}><Truncate width="100%" title={prettyInitialFolderPath}>{fileName(prettyInitialFolderPath)}</Truncate></Box>
            {props.fileHeaderOperations ? (
                <>
                    {props.fileHeaderOperations}
                    <Box mr="8px" />
                </>
            ) : null}
        </Flex>
        <Box className="tree-content" overflowY="auto">
            <Tree apiRef={tree} onAction={onTreeAction}>
                <FileNode
                    initialFolder={props.initialFolder}
                    initialFilePath={props.initialFilePath}
                    node={root}
                    renamingFile={props.renamingFile}
                    onRename={props.onRename}
                    onAction={onNodeActivated}
                    onContextMenu={onContextMenu}
                />
            </Tree>
            <Operations
                entityNameSingular={""}
                operations={operations}
                forceEvaluationOnOpen={true}
                openFnRef={openOperations}
                selected={[]}
                row={42 as any} // This works, for some reason
                extra={null}
                hidden
                location={"IN_ROW"}
            />
        </Box>
        {props.canResize ? <div className="tree-resizer" onPointerDown={onResizeStart} /> : null}
    </div>
}

const FileNode: React.FunctionComponent<{
    node: EditorSidebarNode;
    onAction: (open: boolean, row: HTMLElement) => void;
    initialFilePath?: string;
    initialFolder?: string;
    operations?: (file: VirtualFile) => Operation<any>[];
    onContextMenu?: (e: React.MouseEvent<HTMLDivElement>, file: VirtualFile) => void;
    renamingFile?: string;
    onRename?: (args: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}) => void;
}> = props => {
    const children = !props.node.file.isDirectory ? undefined : <>
        {props.node.children.map(child => (
            <FileNode key={child.file.absolutePath} onRename={props.onRename} renamingFile={props.renamingFile} node={child} onAction={props.onAction} operations={props.operations} onContextMenu={props.onContextMenu} />
        ))}
    </>;

    const didRename = React.useRef(false);

    const renameFile = React.useCallback((newName: string, cancel: boolean) => {
        didRename.current = true;
        const parentPath = getParentPath(props.node.file.absolutePath);
        const newFullPath = parentPath + newName;
        props.onRename?.({
            newAbsolutePath: newFullPath,
            oldAbsolutePath: props.node.file.absolutePath,
            cancel
        });
    }, []);

    const absolutePath = props.node.file.absolutePath;
    if (absolutePath === "" || absolutePath === "/" || absolutePath === props.initialFolder) return children;

    const isInitiallyOpen = props.node.file.isDirectory &&
        props.initialFilePath?.startsWith(props.node.file.absolutePath);

    const prettyPath = usePrettyFilePath(props.node.file.absolutePath);

    const isRenaming = props.renamingFile === props.node.file.absolutePath;

    React.useEffect(() => {
        if (isRenaming) {
            didRename.current = false;
        }
    }, [isRenaming]);

    return <TreeNode
        cursor="pointer"
        data-path={props.node.file.absolutePath}
        onActivate={props.onAction}
        data-open={isInitiallyOpen}
        onContextMenu={e => {
            e.stopPropagation();
            props.onContextMenu?.(e, props.node.file)
        }}
        slim
        left={
            <Flex gap={"8px"} alignItems={"center"} fontSize={"12px"} minWidth={0}>
                {props.node.file.isDirectory ? null :
                    <FullpathFileLanguageIcon filePath={props.node.file.absolutePath} size="16px" />
                }

                {isRenaming ?
                    <Input autoFocus onBlur={e => {
                        e.preventDefault();
                        if (didRename.current) {
                            return;
                        }
                        renameFile(e.target["value"], false);
                    }} onKeyDown={e => {
                        e.stopPropagation();
                        if (e.key === "Enter") {
                            renameFile(e.target["value"], false);
                        } else if (e.key === "Escape") {
                            renameFile("", true);
                        }
                    }} defaultValue={fileName(props.node.file.absolutePath)} width={1} /> :
                    // Note(Jonas): A bit fragile, but this component relies on the tree-node CSS variable called --indent
                    <Truncate title={prettyPath} maxWidth="calc(var(--tree-width) - var(--indent) - 64px)">{fileName(prettyPath)}</Truncate>}
            </Flex >
        }
        children={children}
    />;
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
        border-right: var(--borderThickness) solid var(--borderColor);
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

    ${k} .tree-resizer::before {
        content: "";
        position: absolute;
        top: 0;
        bottom: 0;
        left: 3px;
        width: 2px;
        background: transparent;
    }

    ${k} .tree-resizer:hover::before,
    ${k} .tree-resizer:active::before {
        background: var(--primaryMain);
    }

    ${k} > .tree-header {
        flex: none;
        border-bottom: var(--borderThickness) solid var(--borderColor);
    }
`);
