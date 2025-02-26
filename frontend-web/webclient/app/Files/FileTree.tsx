import * as React from "react";
import {Tree, TreeAction, TreeApi, TreeNode} from "@/ui-components/Tree";
import {injectStyle} from "@/Unstyled";
import {Operation, Operations} from "@/ui-components/Operation";
import {doNothing, extensionFromPath} from "@/UtilityFunctions";
import {usePrettyFilePath} from "./FilePath";
import {Box, Flex, FtIcon, Icon, Input, Truncate} from "@/ui-components";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import {FullpathFileLanguageIcon} from "@/Editor/Editor";

export interface EditorSidebarNode {
    file: VirtualFile;
    children: EditorSidebarNode[];
}

export interface VirtualFile {
    absolutePath: string;
    isDirectory: boolean;
    requestedSyntax?: string;
}

interface FileTreeProps {
    tree: React.MutableRefObject<TreeApi | null>
    onTreeAction: ((row: HTMLElement, action: TreeAction) => void);
    onNodeActivated(open: boolean, row: HTMLElement): void;
    root: EditorSidebarNode;
    initialFolder: string;
    initialFilePath?: string;
    operations?: (file?: VirtualFile) => Operation<any>[];
    width?: string;
    canResize?: boolean;
    fileHeaderOperations?: React.ReactNode;
    renamingFile?: string;
    onRename?: (args: {newAbsolutePath: string, oldAbsolutePath: string, cancel: boolean}) => void;
}

export function FileTree({tree, onTreeAction, onNodeActivated, root, ...props}: FileTreeProps) {
    const width = props.width ?? "250px";
    const resizeSetting = props.canResize ? "horizontal" : "none";

    const [operations, setOperations] = React.useState<Operation<any, undefined>[]>([]);

    const style = {
        "--tree-width": width,
        "--resize-setting": resizeSetting,
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

    return <div style={style} className={FileTreeClass}>
        <Flex alignItems={"center"} pl="6px" className="title-bar" gap={"8px"}>
            <FtIcon fileIcon={{type: "DIRECTORY", ext: extensionFromPath(props.initialFolder)}} size={"18px"} />
            <Box width="150px"><Truncate width="150px" title={prettyInitialFolderPath} maxWidth="150px">{fileName(prettyInitialFolderPath)}</Truncate></Box>
            <Flex flexGrow={1} />
            {props.fileHeaderOperations ? (
                <>
                    {props.fileHeaderOperations}
                    <Box mr="8px" />
                </>
            ) : null}
        </Flex>
        <Box onContextMenu={e => onContextMenu(e, undefined)} overflowY="auto" maxHeight={"calc(100vh - 34px)"}>
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
                extra={null}
                row={42}
                hidden
                location={"IN_ROW"}
            />
        </Box>
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
            <Flex gap={"8px"} alignItems={"center"} fontSize={"12px"}>
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
                    <Truncate title={prettyPath} maxWidth="calc(200px - var(--indent))">{fileName(prettyPath)}</Truncate>}
            </Flex >
        }
        children={children}
    />;
}

const FileTreeClass = injectStyle("file-tree", k => `
    ${k} {
        width: var(--tree-width);
        max-width: var(--tree-width);
        resize: var(--resize-setting);
        flex-shrink: 0;
        border-right: var(--borderThickness) solid var(--borderColor);
    }

    ${k} > .tree-header {
        border-bottom: var(--borderThickness) solid var(--borderColor);
    }
`);