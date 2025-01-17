import * as React from "react";
import {Tree, TreeAction, TreeApi, TreeNode} from "@/ui-components/Tree";
import {injectStyle} from "@/Unstyled";
import {Operation, Operations} from "@/ui-components/Operation";
import {doNothing, extensionFromPath} from "@/UtilityFunctions";
import {PrettyFileName} from "./FilePath";
import {Flex, FtIcon, Truncate} from "@/ui-components";

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
    operations?: (file: VirtualFile) => Operation<any>[];
    width?: string;
    canResize?: boolean;
}

export function FileTree({tree, onTreeAction, onNodeActivated, root, ...props}: FileTreeProps) {
    const width = props.width ?? "250px";
    const resizeSetting = props.canResize ? "horizontal" : "none";

    const style = {
        "--tree-width": width,
        "--resize-setting": resizeSetting,
    } as React.CSSProperties;

    return <div style={style} className={FileTreeClass}>
        <Tree apiRef={tree} onAction={onTreeAction}>
            <FileNode
                initialFolder={props.initialFolder}
                initialFilePath={props.initialFilePath}
                node={root}
                onAction={onNodeActivated}
                operations={props.operations}
            />
        </Tree>
    </div>
}

const FileNode: React.FunctionComponent<{
    node: EditorSidebarNode;
    onAction: (open: boolean, row: HTMLElement) => void;
    initialFilePath?: string;
    initialFolder?: string;
    operations?: (file: VirtualFile) => Operation<any>[];
}> = props => {
    const children = !props.node.file.isDirectory ? undefined : <>
        {props.node.children.map(child => (
            <FileNode key={child.file.absolutePath} node={child} onAction={props.onAction} operations={props.operations} />
        ))}
    </>;

    const absolutePath = props.node.file.absolutePath;
    if (absolutePath === "" || absolutePath === "/" || absolutePath === props.initialFolder) return children;

    const isInitiallyOpen = props.node.file.isDirectory &&
        props.initialFilePath?.startsWith(props.node.file.absolutePath);

    const openOperations = React.useRef<(left: number, top: number) => void>(doNothing);
    const onContextMenu = React.useCallback((ev: React.MouseEvent) => {
        // TODO(Jonas): only one at max should be open at any given point
        ev.preventDefault();
        openOperations.current(ev.clientX, ev.clientY);
    }, []);

    const ops = React.useMemo(() => {
        const ops = props.operations;
        if (!ops) return [];
        return ops(props.node.file);
    }, [props.operations, props.node]);

    return <TreeNode
        data-path={props.node.file.absolutePath}
        onActivate={props.onAction}
        data-open={isInitiallyOpen}
        onContextMenu={onContextMenu}
        slim
        left={
            <>
                <Flex gap={"8px"} alignItems={"center"} fontSize={"12px"}>
                    {props.node.file.isDirectory ? null :
                        <FtIcon
                            fileIcon={{
                                type: "FILE",
                                ext: extensionFromPath(props.node.file.absolutePath)
                            }}
                            size={"16px"}
                        />
                    }
                    <PrettyFileName path={props.node.file.absolutePath} />
                </Flex>

                <Operations
                    entityNameSingular={""}
                    operations={ops}
                    forceEvaluationOnOpen={true}
                    openFnRef={openOperations}
                    selected={[]}
                    extra={null}
                    row={42}
                    hidden
                    location={"IN_ROW"}
                />
            </>
        }
        children={children}
    />;
}

const FileTreeClass = injectStyle("file-tree", k => `
    ${k} {
        width: var(--tree-width);
        max-width: var(--tree-width);
        overflow-y: auto;
        resize: var(--resize-setting);
        
        flex-shrink: 0;
        border-right: var(--borderThickness) solid var(--borderColor);
    }
`);