import {callAPI} from "@/Authentication/DataHook";
import {colorNames} from "@/Accounting/Diagrams";
import {usePage} from "@/Navigation/Redux";
import {useProjectId} from "@/Project/Api";
import AppRoutes from "@/Routes";
import FileCollectionsApi, {FileCollection, FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import FilesApi from "@/UCloud/FilesApi";
import {FilesVisualizeEntry, FilesVisualizeResponse, UFile} from "@/UCloud/UFile";
import {Box, Card, Error, Icon} from "@/ui-components";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {injectStyle} from "@/Unstyled";
import {fetchAll} from "@/Utilities/PageUtilities";
import {fileName, pathComponents, sizeToString} from "@/Utilities/FileUtilities";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {prettyFilePath, usePrettyFilePath} from "@/Files/FilePath";
import {IconButton} from "@/ui-components/IconButton";
import {UcxSpinner} from "@/UCX/UcxView";
import {useWindowDimensions} from "@/Utilities/StylingUtilities";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {hierarchy, treemap, treemapSquarify, type HierarchyRectangularNode} from "d3";
import React, {useEffect, useRef, useState} from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {pluralize} from "@/Utilities/TextUtilities";

interface VisualizationEntry extends FilesVisualizeEntry {
    label: string;
    prettyPath: string;
}

interface VisualizationData {
    entries: VisualizationEntry[];
    lastUpdatedAt: number | null;
    fileCount: number | null;
    directoryCount: number | null;
}

interface TreeDatum {
    entry: VisualizationEntry | null;
    name: string;
    size: number;
    synthetic?: boolean;
    children?: TreeDatum[];
}

const VisualizationStyle = injectStyle("files-visualization", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 16px;
        width: calc(100vw - var(--currentSidebarStickyWidth));
        min-height: calc(100vh - var(--termsize, 0px));
        padding: 16px;
        box-sizing: border-box;
    }

    ${k} .visualization-header {
        width: 100%;
    }

    ${k} .visualization-heading {
        display: flex;
        align-items: flex-end;
        gap: 8px;
        flex-wrap: wrap;
        align-items: center;
    }

    ${k} .visualization-heading h2 { margin: 0; }
    ${k} .visualization-subtitle { color: var(--textSecondary); margin-top: 4px; }

    ${k} .visualization-crumbs {
        display: flex;
        align-items: center;
        gap: 5px;
        min-width: 0;
        overflow-x: auto;
        padding: 3px 0;
        scrollbar-width: thin;
    }

    ${k} .visualization-crumb {
        border: 0;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
        font: inherit;
        padding: 3px 5px;
        border-radius: 4px;
        white-space: nowrap;
    }

    ${k} .visualization-crumb:hover { background: var(--rowHover); }
    ${k} .visualization-crumb:last-of-type { font-weight: 600; }
    ${k} .visualization-crumb-separator { color: var(--textDisabled); }

    ${k} .visualization-workspace {
        display: grid;
        grid-template-columns: minmax(0, 1fr) clamp(300px, 25vw, 450px);
        gap: 16px;
        align-items: start;
    }

    ${k} .visualization-table {
        padding: 0;
        overflow: hidden;
        width: 100%;
        max-width: 450px;
        min-width: 300px;
    }

    ${k} .visualization-map { min-width: 0; }

    ${k} .visualization-canvas {
        position: relative;
        background: var(--backgroundDefault);
    }

    ${k} .visualization-canvas svg { display: block; width: 100%; height: 100%; }
    ${k} .visualization-node { cursor: default; outline: none; }
    ${k} .visualization-node[data-folder="true"] { cursor: pointer; }
    ${k} .visualization-node rect { transition: opacity 120ms ease, stroke 120ms ease, stroke-width 120ms ease; }
    ${k} .visualization-node:hover rect,
    ${k} .visualization-node[data-highlighted="true"] rect { stroke: var(--chart-hover-on-fill); stroke-width: 3px; }
    ${k} .visualization-directory-header:hover rect,
    ${k} .visualization-directory-header[data-highlighted="true"] rect { stroke: var(--chart-hover-on-surface); }

    ${k} .visualization-empty {
        min-height: 480px;
        display: grid;
        place-items: center;
        color: var(--textSecondary);
        text-align: center;
        padding: 32px;
    }

    ${k} .visualization-list { height: 552px; overflow: auto; }
    ${k} .visualization-list-header,
    ${k} .visualization-list-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) 92px 58px;
        gap: 10px;
        align-items: center;
        padding: 9px 14px;
    }

    ${k} .visualization-list-header {
        position: sticky;
        top: 0;
        z-index: 1;
        background: var(--backgroundCard);
        border-bottom: 1px solid var(--borderColor);
        color: var(--textSecondary);
        font-size: 11px;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: .04em;
    }

    ${k} .visualization-list-row {
        position: relative;
        min-height: 50px;
        border-bottom: 1px solid var(--borderColor);
        cursor: default;
        overflow: hidden;
    }

    ${k} .visualization-list-row[data-folder="true"] { cursor: pointer; }
    ${k} .visualization-list-row:hover,
    ${k} .visualization-list-row[data-highlighted="true"] { background: var(--chart-list-hover); }
    ${k} .visualization-list-row:focus-visible { outline: 2px solid var(--chart-hover-on-surface); outline-offset: -2px; }

    ${k} .visualization-list-bar {
        position: absolute;
        inset: 0 auto 0 0;
        background: var(--chart-list-bar);
        pointer-events: none;
    }
    
    html.light ${k} .visualization-list-bar {
        opacity: 0.3;
    }

    ${k} .visualization-entry-name { min-width: 0; position: relative; }
    ${k} .visualization-entry-title { display: flex; align-items: center; gap: 7px; min-width: 0; }
    ${k} .visualization-entry-title span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    ${k} .visualization-entry-path { color: var(--textSecondary); font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-top: 2px; }
    ${k} .visualization-number { text-align: right; font-variant-numeric: tabular-nums; position: relative; }

    ${k} .visualization-status {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        border-radius: 999px;
        padding: 4px 9px;
        background: var(--dialogToolbar);
        font-size: 12px;
    }
    
    html.dark ${k} .visualization-status {
        background: white;
        color: black;
    }

    ${k} .visualization-state { min-height: 520px; display: grid; place-items: center; }

    @media (max-width: 899px) {
        ${k} .visualization-workspace { grid-template-columns: 1fr; }
        ${k} .visualization-table { display: none; }
    }

    @media (max-width: 560px) {
        ${k} .visualization-workspace { gap: 10px; }
        ${k} .visualization-list-header,
        ${k} .visualization-list-row { grid-template-columns: minmax(0, 1fr) 80px; padding: 8px 10px; }
        ${k} .visualization-percent { display: none; }
    }
`);

export default function FilesVisualization(): React.ReactNode {
    usePage("Storage visualization", SidebarTabId.FILES);
    const projectId = useProjectId();
    const location = useLocation();
    const navigate = useNavigate();
    const path = getQueryParam(location.search, "path") ?? undefined;
    const [data, setData] = useState<VisualizationData | null>(null);
    const [dataPath, setDataPath] = useState<string>();
    const [error, setError] = useState<string>();
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let alive = true;
        let retryTimer: number | undefined;
        setLoading(true);
        setError(undefined);

        const load = async () => {
            try {
                const next = path ? await fetchVisualization(path, projectId) : await fetchDriveOverview(projectId);
                if (!alive) return;
                if (path && next.entries.length === 0 && next.lastUpdatedAt == null) {
                    if (data === null) {
                        setData(next);
                        setDataPath(path);
                    }
                    retryTimer = window.setTimeout(() => void load(), 3000);
                    return;
                }
                setData(next);
                setDataPath(path);
                setLoading(false);
            } catch (cause) {
                if (alive) {
                    setError(errorMessageOrDefault(cause, "Unable to load storage usage."));
                    setLoading(false);
                }
            }
        };
        void load();
        return () => {
            alive = false;
            if (retryTimer !== undefined) window.clearTimeout(retryTimer);
        };
    }, [path, projectId]);

    const openPath = (nextPath?: string) => navigate(AppRoutes.files.visualize(nextPath));
    const total = dataPath
        ? data?.entries.find(entry => entry.path === dataPath)?.sizeInBytes ?? 0
        : data?.entries.reduce((sum, entry) => sum + entry.sizeInBytes, 0) ?? 0;

    return <div className={VisualizationStyle}>
            <div className="visualization-header">
                <div className="visualization-heading">
                    <div>
                        <Breadcrumbs
                            path={path}
                            loading={loading && data !== null}
                            openPath={openPath}
                            openFolder={path ? () => navigate(AppRoutes.files.path(path)) : undefined}
                        />
                    </div>
                    <Box flexGrow={1} />
                    {!data ? null : <>
                        {data.directoryCount == null ? null : <div className="visualization-status">
                            <span>{data.directoryCount.toLocaleString()} {pluralize(data.directoryCount, "directory", "directories")}</span>
                        </div>}

                        {data.fileCount == null ? null : <div className="visualization-status">
                            <span>{data.fileCount.toLocaleString()} {pluralize(data.fileCount, "file")}</span>
                        </div>}

                        <div className="visualization-status">
                            <span>{sizeToString(total)}</span>
                        </div>

                        {data.lastUpdatedAt == null ? null : <div className="visualization-status">
                            <span>Indexed {new Date(data.lastUpdatedAt).toLocaleString()}</span>
                        </div>}
                    </>}
                </div>
            </div>

            {loading && data === null ? <div className="visualization-state"><UcxSpinner /></div> :
                error && data === null ? <Error error={error} /> :
                    data && path && data.entries.length === 0 && data.lastUpdatedAt == null
                        ? <Card><div className="visualization-state"><div><UcxSpinner /><p>Building the storage index...</p></div></div></Card>
                        : data ? <VisualizationWorkspace data={data} selectedPath={dataPath} openPath={openPath} /> : null}
    </div>;
}

async function fetchVisualization(path: string, projectId?: string): Promise<VisualizationData> {
    const response = await callAPI<FilesVisualizeResponse>({
        ...FilesApi.visualize({path}),
        projectOverride: projectId,
    });
    const entries = await Promise.all((response.entries ?? []).map(async entry => {
        const prettyPath = await prettyFilePath(entry.path);
        return {...entry, prettyPath, label: fileName(prettyPath)};
    }));
    return {
        entries,
        lastUpdatedAt: response.lastUpdatedAt,
        fileCount: entries.length > 0 ? response.fileCount : null,
        directoryCount: entries.length > 0 ? response.directoryCount : null,
    };
}

async function fetchDriveOverview(projectId?: string): Promise<VisualizationData> {
    const drives = await fetchAll<FileCollection>(next => callAPI({
        ...FileCollectionsApi.browse({
            itemsPerPage: 250,
            next,
            filterMemberFiles: "all",
            includeSupport: true,
        }),
        projectOverride: projectId,
    }));
    const supported = drives.filter(drive => {
        const support = drive.status.resolvedSupport?.support as FileCollectionSupport | undefined;
        return support?.stats.visualization === true;
    });

    const entries = await mapConcurrent<FileCollection, VisualizationEntry | null>(supported, 6, async drive => {
        try {
            const file = await callAPI<UFile>({
                ...FilesApi.retrieve({id: `/${drive.id}`, includeSizes: true}),
                projectOverride: projectId,
            });
            const size = file.status.sizeIncludingChildrenInBytes;
            if (size == null) return null;
            return {
                path: file.id,
                type: "DIRECTORY" as const,
                sizeInBytes: size,
                label: drive.specification.title,
                prettyPath: `/${drive.specification.title}`,
            };
        } catch {
            return null;
        }
    });

    return {
        entries: entries.filter((entry): entry is VisualizationEntry => entry !== null),
        lastUpdatedAt: null,
        fileCount: null,
        directoryCount: null,
    };
}

async function mapConcurrent<T, R>(items: T[], concurrency: number, mapper: (item: T) => Promise<R>): Promise<R[]> {
    const result = new Array<R>(items.length);
    let cursor = 0;
    const workers = Array.from({length: Math.min(concurrency, items.length)}, async () => {
        while (cursor < items.length) {
            const index = cursor++;
            result[index] = await mapper(items[index]);
        }
    });
    await Promise.all(workers);
    return result;
}

function Breadcrumbs({path, loading, openPath, openFolder}: {
    path?: string;
    loading: boolean;
    openPath: (path?: string) => void;
    openFolder?: () => void;
}): React.ReactNode {
    const components = path ? pathComponents(path) : [];
    const prettyComponents = pathComponents(usePrettyFilePath(path ?? ""));
    return <nav className="visualization-crumbs" aria-label="Storage path">
        <button className="visualization-crumb" onClick={() => openPath(undefined)}>All drives</button>
        {components.map((component, index) => {
            const componentPath = "/" + components.slice(0, index + 1).join("/");
            return <React.Fragment key={componentPath}>
                <span className="visualization-crumb-separator">/</span>
                <button className="visualization-crumb" onClick={() => openPath(componentPath)}>{prettyComponents[index] ?? component}</button>
            </React.Fragment>;
        })}
        {loading && <UcxSpinner size={22} margin="0 4px" />}
        {openFolder && <IconButton tooltip="Open folder" onClick={openFolder} icon="heroFolderOpen" />}
    </nav>;
}

function VisualizationWorkspace({data, selectedPath, openPath}: {
    data: VisualizationData;
    selectedPath?: string;
    openPath: (path: string) => void;
}): React.ReactNode {
    const [windowWidth, windowHeight] = useWindowDimensions();
    const [stickySidebarWidth] = useGlobal("sidebarStickyWidth", 64);
    const workspaceRef = useRef<HTMLDivElement>(null);
    const highlightedPath = useRef<string | undefined>(undefined);
    const setHighlightedPath = (path?: string) => {
        const workspace = workspaceRef.current;
        if (!workspace || highlightedPath.current === path) return;
        if (highlightedPath.current) {
            const key = encodeURIComponent(highlightedPath.current);
            workspace.querySelectorAll(`[data-visualization-path="${key}"]`).forEach(element => element.removeAttribute("data-highlighted"));
        }
        if (path) {
            const key = encodeURIComponent(path);
            workspace.querySelectorAll(`[data-visualization-path="${key}"]`).forEach(element => element.setAttribute("data-highlighted", "true"));
        }
        highlightedPath.current = path;
    };
    const sorted = data.entries
        .filter(entry => entry.path !== selectedPath || entry.type !== "DIRECTORY")
        .sort((a, b) => b.sizeInBytes - a.sizeInBytes || a.label.localeCompare(b.label));
    const total = selectedPath
        ? data.entries.find(entry => entry.path === selectedPath)?.sizeInBytes ?? sorted[0]?.sizeInBytes ?? 0
        : sorted.reduce((sum, entry) => sum + entry.sizeInBytes, 0);
    const hideList = windowWidth < 900;
    const listWidth = Math.min(450, Math.max(300, windowWidth * .25));
    const diagramWidth = Math.max(320, windowWidth - stickySidebarWidth - 32 - (hideList ? 0 : listWidth + 16));
    const diagramHeight = Math.max(420, windowHeight - 100);
    return <div ref={workspaceRef} className="visualization-workspace">
        <div className="visualization-map">
            <TreemapView
                entries={data.entries}
                rootPath={selectedPath}
                width={diagramWidth}
                height={diagramHeight}
                setHighlightedPath={setHighlightedPath}
                openPath={openPath}
            />
        </div>

        <Card className="visualization-table">
            <div className="visualization-list" style={{height: diagramHeight}}>
                <div className="visualization-list-header">
                    <span>Name</span><span className="visualization-number">Size</span><span className="visualization-number visualization-percent">Share</span>
                </div>
                {sorted.map(entry => {
                    const percentage = total > 0 ? entry.sizeInBytes / total * 100 : 0;
                    const isFolder = entry.type === "DIRECTORY";
                    return <div
                        key={entry.path}
                        className="visualization-list-row"
                        data-folder={isFolder}
                        data-visualization-path={encodeURIComponent(entry.path)}
                        role={isFolder ? "button" : undefined}
                        aria-label={isFolder ? `Open ${entry.label}, ${sizeToString(entry.sizeInBytes)}` : undefined}
                        tabIndex={isFolder ? 0 : undefined}
                        onMouseEnter={() => setHighlightedPath(entry.path)}
                        onMouseLeave={() => setHighlightedPath(undefined)}
                        onFocus={() => setHighlightedPath(entry.path)}
                        onBlur={() => setHighlightedPath(undefined)}
                        onClick={() => isFolder && openPath(entry.path)}
                        onKeyDown={event => {
                            if (isFolder && (event.key === "Enter" || event.key === " ")) {
                                event.preventDefault();
                                openPath(entry.path);
                            }
                        }}
                    >
                        <div className="visualization-list-bar" style={{width: `${Math.min(100, percentage)}%`}} />
                        <div className="visualization-entry-name">
                            <div className="visualization-entry-title">
                                <Icon name={isFolder ? "heroFolderOpen" : "heroDocument"} size="18px" />
                                <span title={entry.label}>{entry.label}</span>
                            </div>
                            <div className="visualization-entry-path" title={entry.prettyPath}>{entry.prettyPath}</div>
                        </div>
                        <span className="visualization-number">{sizeToString(entry.sizeInBytes)}</span>
                        <span className="visualization-number visualization-percent">{percentage < .1 && percentage > 0 ? "<0.1" : percentage.toFixed(1)}%</span>
                    </div>;
                })}
                {sorted.length === 0 && <div className="visualization-empty">No recursive storage statistics are available.</div>}
            </div>
        </Card>
    </div>;
}

function TreemapView({entries, rootPath, width, height, setHighlightedPath, openPath}: {
    entries: VisualizationEntry[];
    rootPath?: string;
    width: number;
    height: number;
    setHighlightedPath: (path?: string) => void;
    openPath: (path: string) => void;
}): React.ReactNode {
    const tree = buildTree(entries, rootPath);
    if (!tree || entries.length === 0 || !entries.some(entry => entry.sizeInBytes > 0)) {
        return <div className="visualization-canvas" style={{height}}><div className="visualization-empty">No recursive storage statistics are available.</div></div>;
    }
    const root = hierarchy(tree).sum(datum => datum.size).sort((a, b) => (b.value ?? 0) - (a.value ?? 0));
    const layout = treemap<TreeDatum>()
        .tile(treemapSquarify)
        .size([Math.max(width, 1), height])
        .paddingOuter(2)
        .paddingInner(2)
        .paddingTop(node => node.depth > 0 && node.children ? 21 : 0)
        .round(true)(root);
    const nodes = layout.descendants().filter(node => node.depth > 0);
    const internal = nodes.filter(node => node.children && node.data.entry);
    const leaves = nodes.filter(node => !node.children);

    return <div className="visualization-canvas" style={{height}}>
        <svg viewBox={`0 0 ${Math.max(width, 1)} ${height}`} role="group" aria-label="Interactive storage usage treemap">
            {internal.map((node, index) => <TreemapDirectoryHeader
                key={node.data.entry!.path}
                node={node}
                color={nodeColor(node, index)}
                setHighlightedPath={setHighlightedPath}
                openPath={openPath}
            />)}
            {leaves.map((node, index) => <TreemapLeaf
                key={`${node.data.entry?.path ?? "other"}-${index}`}
                node={node}
                color={nodeColor(node, index)}
                setHighlightedPath={setHighlightedPath}
                openPath={openPath}
            />)}
        </svg>
    </div>;
}

function TreemapDirectoryHeader({node, color, setHighlightedPath, openPath}: {
    node: HierarchyRectangularNode<TreeDatum>;
    color: string;
    setHighlightedPath: (path?: string) => void;
    openPath: (path: string) => void;
}): React.ReactNode {
    const entry = node.data.entry!;
    const width = node.x1 - node.x0;
    return <g
        className="visualization-node visualization-directory-header"
        data-folder="true"
        data-visualization-path={encodeURIComponent(entry.path)}
        role="button"
        aria-label={`Open ${entry.label}, ${sizeToString(entry.sizeInBytes)}`}
        tabIndex={0}
        onMouseEnter={() => setHighlightedPath(entry.path)}
        onMouseLeave={() => setHighlightedPath(undefined)}
        onFocus={() => setHighlightedPath(entry.path)}
        onBlur={() => setHighlightedPath(undefined)}
        onClick={() => openPath(entry.path)}
        onKeyDown={event => {
            if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                openPath(entry.path);
            }
        }}
    >
        <title>{entry.label} - {sizeToString(entry.sizeInBytes)}</title>
        <rect x={node.x0} y={node.y0} width={width} height={node.y1 - node.y0} rx={4} fill="transparent" stroke={color} strokeWidth={1.5} />
        {width > 65 && <text x={node.x0 + 7} y={node.y0 + 15} fill="var(--textPrimary)" fontSize={11} fontWeight={600}>{truncateLabel(entry.label, width / 7)}</text>}
    </g>;
}

function TreemapLeaf({node, color, setHighlightedPath, openPath}: {
    node: HierarchyRectangularNode<TreeDatum>;
    color: string;
    setHighlightedPath: (path?: string) => void;
    openPath: (path: string) => void;
}): React.ReactNode {
    const entry = node.data.entry;
    const width = node.x1 - node.x0;
    const height = node.y1 - node.y0;
    const isFolder = entry?.type === "DIRECTORY";
    const interactive = entry != null && isFolder && !node.data.synthetic;
    const label = node.data.synthetic ? "Other" : node.data.name;
    return <g
        className="visualization-node"
        data-folder={interactive}
        data-visualization-path={entry ? encodeURIComponent(entry.path) : undefined}
        role={interactive ? "button" : undefined}
        aria-label={interactive ? `Open ${entry!.label}, ${sizeToString(entry!.sizeInBytes)}` : undefined}
        tabIndex={interactive ? 0 : undefined}
        onMouseEnter={() => entry && setHighlightedPath(entry.path)}
        onMouseLeave={() => setHighlightedPath(undefined)}
        onFocus={() => entry && setHighlightedPath(entry.path)}
        onBlur={() => setHighlightedPath(undefined)}
        onClick={() => interactive && openPath(entry.path)}
        onKeyDown={event => {
            if (interactive && (event.key === "Enter" || event.key === " ")) {
                event.preventDefault();
                openPath(entry.path);
            }
        }}
    >
        <title>{label} - {sizeToString(node.value ?? 0)}</title>
        <rect x={node.x0} y={node.y0} width={width} height={height} rx={3} fill={color} fillOpacity={.82} stroke="var(--backgroundCard)" strokeWidth={1} />
        {width > 58 && height > 34 && <>
            <text x={node.x0 + 7} y={node.y0 + 16} fill="var(--fixedWhite)" fontSize={11} fontWeight={600}>{truncateLabel(label, width / 7)}</text>
            {height > 50 && <text x={node.x0 + 7} y={node.y0 + 31} fill="var(--fixedWhite)" opacity={.85} fontSize={10}>{sizeToString(node.value ?? 0)}</text>}
        </>}
    </g>;
}

function buildTree(entries: VisualizationEntry[], rootPath?: string): TreeDatum | null {
    if (entries.length === 0) return null;
    if (!rootPath) {
        return {
            entry: null,
            name: "All drives",
            size: 0,
            children: entries.map(entry => ({entry, name: entry.label, size: entry.sizeInBytes})),
        };
    }

    const rootEntry = entries.find(entry => entry.path === rootPath);
    if (!rootEntry) return null;
    if (rootEntry.type !== "DIRECTORY") {
        return {entry: null, name: rootEntry.label, size: 0, children: [{entry: rootEntry, name: rootEntry.label, size: rootEntry.sizeInBytes}]};
    }
    const byPath = new Map<string, TreeDatum>();
    for (const entry of entries) byPath.set(entry.path, {entry, name: entry.label, size: entry.sizeInBytes, children: []});
    const root = byPath.get(rootPath)!;
    for (const entry of entries) {
        if (entry.path === rootPath) continue;
        const parentPath = entry.path.slice(0, entry.path.lastIndexOf("/")) || "/";
        const parent = byPath.get(parentPath);
        if (parent) parent.children!.push(byPath.get(entry.path)!);
    }
    finalizeDirectory(root, true);
    return root;
}

function finalizeDirectory(node: TreeDatum, forceChildren = false): void {
    const children = node.children ?? [];
    for (const child of children) finalizeDirectory(child);
    if (node.entry?.type !== "DIRECTORY") {
        delete node.children;
        return;
    }
    if (children.length === 0 && !forceChildren) {
        delete node.children;
        return;
    }
    const represented = children.reduce((sum, child) => sum + (child.entry?.sizeInBytes ?? 0), 0);
    const remainder = Math.max(0, node.entry.sizeInBytes - represented);
    node.size = 0;
    if (remainder > 0) children.push({entry: node.entry, name: "Other", size: remainder, synthetic: true});
    if (children.length === 0) delete node.children;
}

function nodeColor(node: HierarchyRectangularNode<TreeDatum>, fallbackIndex: number): string {
    let top = node;
    while (top.parent?.depth && top.parent.depth > 0) top = top.parent;
    const siblings = top.parent?.children ?? [];
    const index = siblings.indexOf(top);
    return colorNames[(index >= 0 ? index : fallbackIndex) % colorNames.length];
}

function truncateLabel(label: string, maxCharacters: number): string {
    const max = Math.max(3, Math.floor(maxCharacters));
    return label.length > max ? label.slice(0, max - 1) + "…" : label;
}
