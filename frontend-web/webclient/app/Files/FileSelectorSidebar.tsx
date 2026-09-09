import * as React from "react";
import {callAPI as callAPIBase} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {FileCollection, api as FileCollectionsApi} from "@/UCloud/FileCollectionsApi";
import FilesApi from "@/UCloud/FilesApi";
import {UFile} from "@/UCloud/UFile";
import {fetchProjects} from "@/Project/ProjectSwitcher";
import {Project} from "@/Project";
import {fileName, pathComponents} from "@/Utilities/FileUtilities";
import {fetchAll} from "@/Utilities/PageUtilities";
import {VirtualizedTree, VirtualizedTreeApi} from "@/ui-components/VirtualizedTree";
import {Icon, Truncate} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {sidebarFavoriteCache} from "./FavoriteCache";
import {injectStyle} from "@/Unstyled";
import {ResourceIncludeFlags} from "@/UCloud/ResourceApi";
import {ActionEntry, ActionMenu} from "@/ui-components/Actions";
import {FullpathFileLanguageIcon} from "@/Editor/Editor";
import {ThemeColor} from "@/ui-components/theme";

export type FileTreeResourceNode = {
    id: string;
    title: string;
    kind: "favorites" | "project" | "member-files" | "provider" | "drive" | "directory" | "file" | "placeholder";
    project?: string;
    provider?: string;
    path?: string;
    children: FileTreeResourceNode[];
    loaded?: boolean;
};

export type FileTreeResourceOperations = (node: FileTreeResourceNode) => ActionEntry<FileTreeResourceNode, null>[];

export function FileSelectorSidebar({tree, initialPath, initialProject, additionalFilters, selectedPath, includeFiles = true, onOpen, onKeyboardActivate, onSelectionChange, projectOperations, driveOperations, directoryOperations, fileOperations}: {
    tree?: React.RefObject<VirtualizedTreeApi | null>;
    initialPath?: string;
    initialProject?: string;
    additionalFilters?: Record<string, string> & ResourceIncludeFlags;
    selectedPath?: string;
    includeFiles?: boolean;
    onOpen(path: string, project?: string): void;
    onKeyboardActivate?(): void;
    onSelectionChange?(node?: FileTreeResourceNode): void;
    projectOperations?: FileTreeResourceOperations;
    driveOperations?: FileTreeResourceOperations;
    directoryOperations?: FileTreeResourceOperations;
    fileOperations?: FileTreeResourceOperations;
}): React.ReactNode {
    const favorites = React.useSyncExternalStore(
        listener => sidebarFavoriteCache.subscribe(listener),
        () => sidebarFavoriteCache.getSnapshot(),
    );
    const [projects, setProjects] = React.useState<Project[]>([]);
    const [projectsLoaded, setProjectsLoaded] = React.useState(false);
    const [projectNodesReady, setProjectNodesReady] = React.useState(false);
    const [nodes, setNodes] = React.useState<FileTreeResourceNode[]>([]);
    const [favoriteInfoVersion, setFavoriteInfoVersion] = React.useState(0);
    const [expandedIds, setExpandedIds] = React.useState<string[]>([]);
    const [initialExpansionComplete, setInitialExpansionComplete] = React.useState(!initialPath);
    const nodesRef = React.useRef(nodes);
    const loading = React.useRef(new Map<string, Promise<FileTreeResourceNode>>());
    const [contextMenu, setContextMenu] = React.useState<{node: FileTreeResourceNode; left: number; top: number} | null>(null);
    const openOperations = React.useRef<(left: number, top: number) => void>(() => undefined);

    React.useEffect(() => {
        nodesRef.current = nodes;
    }, [nodes]);

    React.useEffect(() => {
        loading.current.clear();
        setNodes(current => current.map(node => node.kind === "project" ? {...node, children: [], loaded: false} : node));
    }, [additionalFilters, includeFiles]);

    React.useEffect(() => {
        fetchProjects()
            .then(page => setProjects(
                page.items.filter(project =>
                    !project.status.archived &&
                    project.status.isHidden !== true &&
                    project.specification.canConsumeResources === true)
                )
            )
            .finally(() => setProjectsLoaded(true));
        if (!sidebarFavoriteCache.initialized) sidebarFavoriteCache.fetch();
    }, []);

    React.useEffect(() => {
        sidebarFavoriteCache.fetchFileInfo(favorites.items.map(favorite => favorite.path))
            .then(() => setFavoriteInfoVersion(current => current + 1))
            .catch(() => undefined);
    }, [favorites]);

    React.useEffect(() => {
        const favoriteNodes = favorites.items.flatMap<FileTreeResourceNode>(favorite => {
            const file = sidebarFavoriteCache.fileInfoIfPresent(favorite.path);
            if (file?.status.type !== "DIRECTORY") return [];
            return [{
                id: `favorite:${favorite.path}`,
                title: fileName(favorite.path),
                kind: "directory",
                project: file.owner.project,
                provider: file.specification.product.provider,
                path: favorite.path,
                children: [],
                loaded: true,
            }];
        });
        const projectNodes = projects.map<FileTreeResourceNode>(project => ({
            id: `project:${project.id}`,
            title: project.specification.title,
            kind: "project",
            project: project.id,
            children: [],
        }));
        const workspace: FileTreeResourceNode = {
            id: "project:workspace",
            title: "My workspace",
            kind: "project",
            children: [],
        };

        setNodes(current => {
            const oldNodes = new Map(current.map(node => [node.id, node]));
            const next = [
                {id: "favorites", title: "Favorites", kind: "favorites", children: favoriteNodes.length ? favoriteNodes : [{
                    id: "favorites:empty",
                    title: "No favorites",
                    kind: "placeholder",
                    children: [],
                    loaded: true,
                }], loaded: true},
                oldNodes.get(workspace.id) ?? workspace,
                ...projectNodes.map(node => oldNodes.get(node.id) ?? node),
            ] as FileTreeResourceNode[];
            nodesRef.current = next;
            return next;
        });
        if (projectsLoaded) setProjectNodesReady(true);
    }, [favoriteInfoVersion, favorites, projects, projectsLoaded]);

    const replaceNode = React.useCallback((id: string, update: (node: FileTreeResourceNode) => FileTreeResourceNode) => {
        const visit = (entries: FileTreeResourceNode[]): FileTreeResourceNode[] => entries.map(node => {
            if (node.id === id) return update(node);
            const children = visit(node.children);
            return children === node.children ? node : {...node, children};
        });
        setNodes(current => {
            const next = visit(current);
            nodesRef.current = next;
            return next;
        });
    }, []);

    const loadNode = React.useCallback((node: FileTreeResourceNode): Promise<FileTreeResourceNode> => {
        if (node.loaded) return Promise.resolve(node);
        const existing = loading.current.get(node.id);
        if (existing) return existing;
        const promise = (async () => {
            if (node.kind === "project") {
                const request = (filterMemberFiles: string) => callAPI(FileCollectionsApi.browse({
                    itemsPerPage: 250,
                    ...additionalFilters,
                    filterMemberFiles,
                }), node.project);
                const [allDrives, memberDrives] = await Promise.all([request("all"), node.project ? request("true") : Promise.resolve({items: []})]);
                const memberIds = new Set(memberDrives.items.map(drive => drive.id));
                const ownDrives = allDrives.items.filter(drive => !memberIds.has(drive.id));
                const providers = new Set(allDrives.items.map(drive => drive.specification.product.provider));
                const driveNodes = makeDriveNodes(ownDrives, node.project, providers.size > 1, node.id);
                const children = memberDrives.items.length === 0 ? driveNodes : [{
                    id: `${node.id}:members`,
                    title: "Member files",
                    kind: "member-files" as const,
                    project: node.project,
                    children: makeDriveNodes(memberDrives.items, node.project, providers.size > 1, `${node.id}:members`),
                    loaded: true,
                }, ...driveNodes];
                replaceNode(node.id, current => ({...current, children, loaded: true}));
                return {...node, children, loaded: true};
            } else if ((node.kind === "drive" || node.kind === "directory") && node.path) {
                const files = await fetchAll<UFile>(next => callAPI(FilesApi.browse({
                    path: node.path!,
                    itemsPerPage: 250,
                    next,
                    ...additionalFilters,
                }), node.project));
                const children = files
                    .filter(file => includeFiles || file.status.type === "DIRECTORY")
                    .sort((a, b) => a.id.localeCompare(b.id))
                    .map<FileTreeResourceNode>(file => ({
                        id: `directory:${node.project ?? "workspace"}:${file.id}`,
                        title: fileName(file.id),
                        kind: file.status.type === "DIRECTORY" ? "directory" : "file",
                        project: node.project,
                        provider: file.specification.product.provider,
                        path: file.id,
                        children: [],
                    }));
                replaceNode(node.id, current => ({...current, children, loaded: true}));
                return {...node, children, loaded: true};
            }
            return node;
        })().finally(() => loading.current.delete(node.id));
        loading.current.set(node.id, promise);
        return promise;
    }, [additionalFilters, includeFiles, replaceNode]);

    React.useEffect(() => {
        if (initialExpansionComplete || !initialPath || !projectNodesReady) return;
        const projectId = initialProject ?? Client.projectId;
        const projectNodeId = `project:${projectId || "workspace"}`;
        const collectionId = pathComponents(initialPath)[0];
        let cancelled = false;
        const expandInitialPath = async () => {
            let projectNode = findNode(nodesRef.current, projectNodeId);
            const ids: string[] = [];
            try {
                if (!projectNode) return;
                projectNode = await loadNode(projectNode);
                const drive = findNodeByPath(projectNode.children, `/${collectionId}`);
                if (!drive || cancelled) return;

                ids.push(projectNodeId, ...findParentIds(projectNode.children, drive.id), drive.id);
                let current = drive;
                let path = `/${collectionId}`;
                for (const component of pathComponents(initialPath).slice(1)) {
                    current = await loadNode(current);
                    path += `/${component}`;
                    const child = findNodeByPath(current.children, path);
                    if (!child || cancelled) break;
                    current = child;
                    ids.push(child.id);
                }
            } finally {
                if (!cancelled) {
                    setExpandedIds([...new Set(ids)]);
                    setInitialExpansionComplete(true);
                }
            }
        };
        void expandInitialPath().catch(() => undefined);
        return () => {cancelled = true;};
    }, [initialExpansionComplete, initialPath, initialProject, loadNode, projectNodesReady]);

    React.useLayoutEffect(() => {
        if (!contextMenu) return;
        openOperations.current(contextMenu.left, contextMenu.top);
    }, [contextMenu]);

    const operationsFor = React.useCallback((node: FileTreeResourceNode): ActionEntry<FileTreeResourceNode, null>[] => {
        switch (node.kind) {
            case "project": return projectOperations?.(node) ?? [];
            case "drive": return driveOperations?.(node) ?? [];
            case "directory": return directoryOperations?.(node) ?? [];
            case "file": return fileOperations?.(node) ?? [];
            default: return [];
        }
    }, [directoryOperations, driveOperations, fileOperations, projectOperations]);

    const renderNode = React.useCallback((node: FileTreeResourceNode, state: {expanded: boolean; toggle(): void}) => {
        if (node.kind === "placeholder") {
            return <div className="selector-tree-empty">{node.title}</div>;
        }
        const branch = isBranch(node);
        let icon = iconForNode(node);
        let iconColor: ThemeColor = "FtFolderColor";
        if (node.kind === "directory") {
            if (state.expanded && branch) {
                icon = "heroFolderOpen";
            } else {
                icon = "heroFolder";
            }
        }
        if (icon === "starFilled") {
            iconColor = "favoriteColor";
        }

        return <div className="selector-tree-node" onClick={() => {
            if (node.path) onOpen(node.path, node.project);
            else if (branch) state.toggle();
        }}>
            <span className="selector-tree-toggle" onClick={event => {
                event.stopPropagation();
                if (branch) state.toggle();
            }}>
                {branch ? <Icon name="heroChevronRight" color="textPrimary" size={14} rotation={state.expanded ? 90 : undefined} /> : null}
            </span>
            {node.kind === "provider" && node.provider ? <ProviderLogo providerId={node.provider} size={18} /> : node.kind === "file" && node.path ?
                <FullpathFileLanguageIcon filePath={node.path} size="18px" /> :
                <Icon name={icon} size={18} color={iconColor} />}
            <Truncate width="100%" title={node.title}>{node.title}</Truncate>
        </div>;
    }, [onOpen]);

    return <aside className={SelectorSidebarClass}>
        <div className="selector-sidebar-header">
            <Icon name="heroMapPin" color="textPrimary" size={22} />
            Places
        </div>
        <div className="selector-sidebar-tree">
            {initialExpansionComplete ? <VirtualizedTree
            apiRef={tree}
            nodes={nodes}
            getId={node => node.id}
            getChildren={node => node.children}
            isBranch={isBranch}
            renderNode={renderNode}
            ariaLabel={node => node.title}
            label="File locations"
            selectionMode="single"
            selectedId={selectedPath ? findNodeByPath(nodes, selectedPath)?.id : undefined}
            initialExpandedIds={expandedIds}
            onPrefetch={loadNode}
            onActivate={node => {
                if (node.path) onOpen(node.path, node.project);
            }}
            onKeyboardActivate={node => {
                if (node.path) onKeyboardActivate?.();
            }}
            onSelectionChange={selected => onSelectionChange?.(selected[0])}
            onContextMenu={(event, node) => {
                if (operationsFor(node).length === 0) return;
                event.preventDefault();
                event.stopPropagation();
                setContextMenu({node, left: event.clientX, top: event.clientY});
            }}
            /> : null}
        </div>
        <ActionMenu
            actions={contextMenu ? operationsFor(contextMenu.node) : []}
            openFnRef={openOperations}
            selected={contextMenu ? [contextMenu.node] : []}
            callbacks={null}
            trigger={null}
        />
    </aside>;
}

function callAPI<T>(request: APICallParameters<unknown, T>, project?: string): Promise<T> {
    return callAPIBase({...request, projectOverride: project ?? ""});
}

function makeDriveNodes(drives: FileCollection[], project: string | undefined, groupProviders: boolean, parentId: string): FileTreeResourceNode[] {
    const makeDrive = (drive: FileCollection): FileTreeResourceNode => ({
        id: `drive:${project ?? "workspace"}:${drive.id}`,
        title: drive.specification.title,
        kind: "drive",
        project,
        provider: drive.specification.product.provider,
        path: `/${drive.id}`,
        children: [],
    });
    if (!groupProviders) return drives.map(makeDrive);
    const byProvider = new Map<string, FileCollection[]>();
    for (const drive of drives) {
        const provider = drive.specification.product.provider;
        byProvider.set(provider, [...(byProvider.get(provider) ?? []), drive]);
    }
    return Array.from(byProvider, ([provider, providerDrives]) => ({
        id: `${parentId}:provider:${provider}`,
        title: getProviderTitle(provider),
        kind: "provider",
        project,
        provider,
        children: providerDrives.map(makeDrive),
        loaded: true,
    }));
}

function findNode(nodes: FileTreeResourceNode[], id: string): FileTreeResourceNode | undefined {
    for (const node of nodes) {
        if (node.id === id) return node;
        const child = findNode(node.children, id);
        if (child) return child;
    }
    return undefined;
}

function findNodeByPath(nodes: FileTreeResourceNode[], path: string): FileTreeResourceNode | undefined {
    for (const node of nodes) {
        if (node.path === path) return node;
        const child = findNodeByPath(node.children, path);
        if (child) return child;
    }
    return undefined;
}

function findParentIds(nodes: FileTreeResourceNode[], target: string, parents: string[] = []): string[] {
    for (const node of nodes) {
        if (node.id === target) return parents;
        const result = findParentIds(node.children, target, [...parents, node.id]);
        if (result.length > 0) return result;
    }
    return [];
}

function isBranch(node: FileTreeResourceNode): boolean {
    if (node.kind === "file" || node.kind === "placeholder") return false;
    return node.kind !== "directory" || !node.loaded || node.children.length > 0;
}

function iconForNode(node: FileTreeResourceNode): IconName {
    switch (node.kind) {
        case "favorites": return "starFilled";
        case "project": return node.id === "project:workspace" ? "heroUser" : "heroUserGroup";
        case "member-files": return "heroUsers";
        case "drive": return "ftFileSystem";
        default: return "ftFolder";
    }
}

const SelectorSidebarClass = injectStyle("file-selector-sidebar", k => `
    ${k} {
        width: 250px;
        min-width: 180px;
        height: calc(100% + 16px);
        margin-top: -16px;
        border-right: 1px solid var(--borderColor);
        padding: 16px 0 16px;
        box-sizing: border-box;
        overflow: hidden;
        display: flex;
        flex-direction: column;
    }

    ${k} .selector-sidebar-header {
        display: flex;
        align-items: center;
        height: 35px;
        padding: 0 8px;
        gap: 8px;
        flex-shrink: 0;
        font-size: 110%;
        margin-bottom: 11px;
    }

    ${k} .selector-sidebar-tree {
        min-height: 0;
        flex: 1 1 auto;
    }

    ${k} .selector-tree-empty {
        color: var(--textSecondary);
        font-style: italic;
        cursor: default;
    }

    ${k} .selector-tree-node {
        display: flex;
        align-items: center;
        width: 100%;
        min-width: 0;
        gap: 6px;
        padding-right: 8px;
        box-sizing: border-box;
        cursor: pointer;
    }

    ${k} .selector-tree-toggle {
        display: flex;
        width: 14px;
        min-width: 14px;
    }

    @media (max-width: 700px) {
        ${k} {
            width: 160px;
            min-width: 130px;
        }
    }
`);
