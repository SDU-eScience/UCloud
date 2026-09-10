import * as React from "react";
import AutoSizer from "react-virtualized-auto-sizer";
import {FixedSizeList, ListChildComponentProps} from "react-window";
import {injectStyle} from "@/Unstyled";

export interface VirtualizedTreeApi {
    activate(): void;
    deactivate(): void;
    isActive(): boolean;
}

export interface VirtualizedTreeRow<T> {
    node: T;
    id: string;
    depth: number;
    parentId?: string;
    positionInSet: number;
    setSize: number;
}

export type VirtualizedTreeSelectionMode = "none" | "single" | "multiple";

interface VirtualizedTreeProps<T> {
    apiRef?: React.RefObject<VirtualizedTreeApi | null>;
    nodes: readonly T[];
    getId(node: T): string;
    getChildren(node: T): readonly T[];
    isBranch(node: T): boolean;
    renderNode(node: T, state: {expanded: boolean; focused: boolean; selected: boolean; toggle(): void}): React.ReactNode;
    ariaLabel(node: T): string;
    label?: string;
    initialExpandedIds?: readonly string[];
    initialFocusedId?: string;
    initialSelectedId?: string;
    initialSelectedIds?: readonly string[];
    selectedId?: string;
    selectedIds?: readonly string[];
    selectionMode?: VirtualizedTreeSelectionMode;
    selectionFollowsFocus?: boolean;
    rowHeight?: number;
    indent?: number;
    overscanCount?: number;
    onActivate?(node: T): void;
    onKeyboardActivate?(node: T): void;
    onPrefetch?(node: T): void;
    onSelectionChange?(nodes: readonly T[]): void;
    onContextMenu?(event: React.MouseEvent<HTMLDivElement>, node: T): void;
}

interface RowData<T> {
    rows: readonly VirtualizedTreeRow<T>[];
    expanded: ReadonlySet<string>;
    indent: number;
    getChildren(node: T): readonly T[];
    isBranch(node: T): boolean;
    renderNode(node: T, state: {expanded: boolean; focused: boolean; selected: boolean; toggle(): void}): React.ReactNode;
    ariaLabel(node: T): string;
    focusedId?: string;
    selectedIds: ReadonlySet<string>;
    select(row: VirtualizedTreeRow<T>): void;
    toggle(row: VirtualizedTreeRow<T>): void;
    activate(row: VirtualizedTreeRow<T>): void;
    hover(row: VirtualizedTreeRow<T>): void;
    contextMenu(event: React.MouseEvent<HTMLDivElement>, row: VirtualizedTreeRow<T>): void;
}

function treeItemId(id: string): string {
    return `virtualized-tree-item-${encodeURIComponent(id)}`;
}

function findVisibleAncestor<T>(
    nodes: readonly T[],
    targetId: string,
    visibleIds: ReadonlySet<string>,
    getId: (node: T) => string,
    getChildren: (node: T) => readonly T[],
): string | undefined {
    const search = (siblings: readonly T[], ancestors: readonly string[]): string | undefined => {
        for (const node of siblings) {
            const id = getId(node);
            if (id === targetId) {
                return [...ancestors].reverse().find(ancestorId => visibleIds.has(ancestorId));
            }
            const result = search(getChildren(node), [...ancestors, id]);
            if (result) return result;
        }
        return undefined;
    };
    return search(nodes, []);
}

function containsNode<T>(nodes: readonly T[], targetId: string, getId: (node: T) => string, getChildren: (node: T) => readonly T[]): boolean {
    for (const node of nodes) {
        if (getId(node) === targetId) return true;
        if (containsNode(getChildren(node), targetId, getId, getChildren)) return true;
    }
    return false;
}

function collectNodes<T>(nodes: readonly T[], ids: ReadonlySet<string>, getId: (node: T) => string, getChildren: (node: T) => readonly T[]): T[] {
    const result: T[] = [];
    for (const node of nodes) {
        if (ids.has(getId(node))) result.push(node);
        result.push(...collectNodes(getChildren(node), ids, getId, getChildren));
    }
    return result;
}

function flattenVisibleNodes<T>(
    nodes: readonly T[],
    expanded: ReadonlySet<string>,
    getId: (node: T) => string,
    getChildren: (node: T) => readonly T[],
    isBranch: (node: T) => boolean,
): VirtualizedTreeRow<T>[] {
    const result: VirtualizedTreeRow<T>[] = [];
    const append = (siblings: readonly T[], depth: number, parentId?: string) => {
        for (let siblingIndex = 0; siblingIndex < siblings.length; siblingIndex++) {
            const node = siblings[siblingIndex];
            const id = getId(node);
            result.push({node, id, depth, parentId, positionInSet: siblingIndex + 1, setSize: siblings.length});
            if (isBranch(node) && expanded.has(id)) append(getChildren(node), depth + 1, id);
        }
    };
    append(nodes, 1);
    return result;
}

export function VirtualizedTree<T>(props: VirtualizedTreeProps<T>): React.ReactNode {
    const rootRef = React.useRef<HTMLDivElement>(null);
    const listRef = React.useRef<FixedSizeList<RowData<T>>>(null);
    const hoverTimer = React.useRef<number | undefined>(undefined);
    const typeahead = React.useRef("");
    const typeaheadTimer = React.useRef<number | undefined>(undefined);
    const selectionMode = props.selectionMode ?? "single";
    const selectionFollowsFocus = props.selectionFollowsFocus ?? selectionMode === "single";
    const controlledSelection = props.selectedId !== undefined || props.selectedIds !== undefined;
    const [expanded, setExpanded] = React.useState<Set<string>>(() => new Set(props.initialExpandedIds));
    const [focusedId, setFocusedId] = React.useState(props.initialFocusedId ?? props.initialSelectedId);
    const [selectedIds, setSelectedIds] = React.useState<Set<string>>(() => {
        if (selectionMode === "none") return new Set();
        const initialIds = props.initialSelectedIds ?? (props.initialSelectedId ? [props.initialSelectedId] : []);
        return new Set(selectionMode === "single" ? initialIds.slice(0, 1) : initialIds);
    });

    React.useEffect(() => {
        if (selectionMode === "none") return;
        const controlledIds = props.selectedIds ?? (props.selectedId === undefined ? undefined : [props.selectedId]);
        if (controlledIds === undefined) return;
        setSelectedIds(new Set(selectionMode === "single" ? controlledIds.slice(0, 1) : controlledIds));
        const [nextFocusedId] = controlledIds;
        if (nextFocusedId !== undefined) setFocusedId(nextFocusedId);
    }, [props.selectedId, props.selectedIds, selectionMode]);

    const rows = React.useMemo(() => flattenVisibleNodes(
        props.nodes,
        expanded,
        props.getId,
        props.getChildren,
        props.isBranch,
    ), [props.nodes, expanded, props.getId, props.getChildren, props.isBranch]);

    const focusedIndex = rows.findIndex(row => row.id === focusedId);

    React.useEffect(() => {
        if (focusedId === undefined || focusedIndex >= 0) return;
        const visibleIds = new Set(rows.map(row => row.id));
        const ancestor = findVisibleAncestor(props.nodes, focusedId, visibleIds, props.getId, props.getChildren);
        if (ancestor !== undefined) setFocusedId(ancestor);
    }, [focusedId, focusedIndex, props.getChildren, props.getId, props.nodes, rows]);

    const prefetch = React.useCallback((row: VirtualizedTreeRow<T>) => {
        if (props.isBranch(row.node)) props.onPrefetch?.(row.node);
    }, [props.isBranch, props.onPrefetch]);

    React.useEffect(() => {
        for (const row of rows) {
            if (expanded.has(row.id)) prefetch(row);
        }
    }, [expanded, prefetch, rows]);

    const updateSelection = React.useCallback((next: Set<string>) => {
        setSelectedIds(next);
        props.onSelectionChange?.(collectNodes(props.nodes, next, props.getId, props.getChildren));
    }, [props.getChildren, props.getId, props.nodes, props.onSelectionChange]);

    React.useEffect(() => {
        if (!controlledSelection || focusedIndex < 0) return;
        listRef.current?.scrollToItem(focusedIndex, "smart");
    }, [controlledSelection, focusedIndex]);

    const focusRow = React.useCallback((row: VirtualizedTreeRow<T>, select = selectionFollowsFocus) => {
        setFocusedId(row.id);
        if (select && selectionMode !== "none") updateSelection(new Set([row.id]));
        prefetch(row);
        rootRef.current?.focus({preventScroll: true});
    }, [prefetch, selectionFollowsFocus, selectionMode, updateSelection]);

    const pointerSelect = React.useCallback((row: VirtualizedTreeRow<T>) => {
        focusRow(row, true);
    }, [focusRow]);

    const toggleSelection = React.useCallback((row: VirtualizedTreeRow<T>) => {
        if (selectionMode === "none") return;
        const next = selectionMode === "single" ? new Set([row.id]) : new Set(selectedIds);
        if (selectionMode === "multiple") {
            if (next.has(row.id)) next.delete(row.id);
            else next.add(row.id);
        }
        updateSelection(next);
    }, [selectionMode, selectedIds, updateSelection]);

    const toggle = React.useCallback((row: VirtualizedTreeRow<T>) => {
        if (!props.isBranch(row.node)) return;
        prefetch(row);
        const isCollapsing = expanded.has(row.id);
        if (isCollapsing && focusedId !== undefined && focusedId !== row.id && containsNode(props.getChildren(row.node), focusedId, props.getId, props.getChildren)) {
            setFocusedId(row.id);
            if (selectionFollowsFocus && selectionMode !== "none") updateSelection(new Set([row.id]));
        }
        setExpanded(current => {
            const next = new Set(current);
            if (next.has(row.id)) next.delete(row.id);
            else next.add(row.id);
            return next;
        });
    }, [expanded, focusedId, prefetch, props.getChildren, props.getId, props.isBranch, selectionFollowsFocus, selectionMode, updateSelection]);

    const activate = React.useCallback((row: VirtualizedTreeRow<T>) => {
        props.onActivate?.(row.node);
        if (props.isBranch(row.node)) toggle(row);
    }, [props.isBranch, props.onActivate, toggle]);

    const hover = React.useCallback((row: VirtualizedTreeRow<T>) => {
        if (!props.isBranch(row.node)) return;
        if (hoverTimer.current !== undefined) window.clearTimeout(hoverTimer.current);
        hoverTimer.current = window.setTimeout(() => prefetch(row), 120);
    }, [prefetch, props.isBranch]);

    React.useEffect(() => () => {
        if (hoverTimer.current !== undefined) window.clearTimeout(hoverTimer.current);
        if (typeaheadTimer.current !== undefined) window.clearTimeout(typeaheadTimer.current);
    }, []);

    const contextMenu = React.useCallback((event: React.MouseEvent<HTMLDivElement>, row: VirtualizedTreeRow<T>) => {
        pointerSelect(row);
        props.onContextMenu?.(event, row.node);
    }, [pointerSelect, props.onContextMenu]);

    const selectIndex = React.useCallback((index: number) => {
        if (rows.length === 0) return;
        const boundedIndex = Math.max(0, Math.min(index, rows.length - 1));
        focusRow(rows[boundedIndex]);
        listRef.current?.scrollToItem(boundedIndex, "smart");
    }, [focusRow, rows]);

    const focusByTypeahead = React.useCallback((character: string): boolean => {
        const query = (typeahead.current + character).toLocaleLowerCase();
        const start = focusedIndex < 0 ? 0 : focusedIndex + 1;

        const findMatch = (value: string): VirtualizedTreeRow<T> | undefined => {
            for (let offset = 0; offset < rows.length; offset++) {
                const row = rows[(start + offset) % rows.length];
                if (props.ariaLabel(row.node).toLocaleLowerCase().startsWith(value)) return row;
            }
            return undefined;
        };

        const queryMatch = findMatch(query);
        const match = queryMatch ?? (query.length > 1 ? findMatch(character) : undefined);
        if (!match) return false;

        typeahead.current = queryMatch ? query : character;
        if (typeaheadTimer.current !== undefined) window.clearTimeout(typeaheadTimer.current);
        typeaheadTimer.current = window.setTimeout(() => {
            typeahead.current = "";
        }, 500);
        focusRow(match);
        listRef.current?.scrollToItem(rows.findIndex(row => row.id === match.id), "smart");
        return true;
    }, [focusRow, focusedIndex, props.ariaLabel, rows]);

    const onKeyDown = React.useCallback((event: React.KeyboardEvent<HTMLDivElement>) => {
        const target = event.target as HTMLElement;
        if (target !== event.currentTarget && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) return;

        if (event.key.length === 1 && event.key !== " " && !event.ctrlKey && !event.metaKey && !event.altKey) {
            if (focusByTypeahead(event.key)) {
                event.preventDefault();
                event.stopPropagation();
            }
            return;
        }

        if (rows.length === 0) return;
        if (focusedIndex < 0) {
            switch (event.key) {
                case "ArrowUp":
                case "End":
                    selectIndex(rows.length - 1);
                    break;
                case "ArrowDown":
                case "Home":
                case "ArrowLeft":
                case "ArrowRight":
                case "Enter":
                case " ":
                    selectIndex(0);
                    break;
                default:
                    return;
            }
            event.preventDefault();
            event.stopPropagation();
            return;
        }

        const index = focusedIndex;
        const row = rows[index];

        switch (event.key) {
            case "ArrowDown":
                selectIndex(index + 1);
                break;
            case "ArrowUp":
                selectIndex(index - 1);
                break;
            case "Home":
                selectIndex(0);
                break;
            case "End":
                selectIndex(rows.length - 1);
                break;
            case "ArrowRight":
                if (props.isBranch(row.node)) {
                    if (!expanded.has(row.id)) toggle(row);
                    else if (props.getChildren(row.node).length > 0) selectIndex(index + 1);
                }
                break;
            case "ArrowLeft":
                if (props.isBranch(row.node) && expanded.has(row.id)) toggle(row);
                else if (row.parentId) selectIndex(rows.findIndex(candidate => candidate.id === row.parentId));
                break;
            case "Enter":
                activate(row);
                props.onKeyboardActivate?.(row.node);
                break;
            case " ":
                toggleSelection(row);
                break;
            default:
                return;
        }
        event.preventDefault();
        event.stopPropagation();
    }, [activate, expanded, focusByTypeahead, focusedIndex, props.getChildren, props.isBranch, props.onKeyboardActivate, rows, selectIndex, toggle, toggleSelection]);

    React.useImperativeHandle(props.apiRef, () => ({
        activate() {
            rootRef.current?.focus({preventScroll: true});
            if (focusedIndex < 0) selectIndex(0);
        },
        deactivate() {
            rootRef.current?.blur();
        },
        isActive() {
            return rootRef.current?.contains(document.activeElement) ?? false;
        },
    }), [focusedIndex, selectIndex]);

    const itemData = React.useMemo<RowData<T>>(() => ({
        rows,
        expanded,
        focusedId,
        selectedIds,
        indent: props.indent ?? 16,
        getChildren: props.getChildren,
        isBranch: props.isBranch,
        renderNode: props.renderNode,
        ariaLabel: props.ariaLabel,
        select: pointerSelect,
        toggle,
        activate,
        hover,
        contextMenu,
    }), [rows, expanded, focusedId, selectedIds, props.indent, props.getChildren, props.isBranch, props.renderNode, props.ariaLabel, pointerSelect, toggleSelection, toggle, activate, hover, contextMenu]);

    return <div
        ref={rootRef}
        className={VirtualizedTreeClass}
        role="tree"
        aria-label={props.label ?? "Tree"}
        aria-activedescendant={focusedIndex >= 0 ? treeItemId(focusedId!) : undefined}
        aria-multiselectable={selectionMode === "multiple" ? true : undefined}
        tabIndex={0}
        onFocus={() => rootRef.current?.setAttribute("data-focused", "true")}
        onBlur={event => {
            if (!rootRef.current?.contains(event.relatedTarget as Node)) {
                rootRef.current?.removeAttribute("data-focused");
            }
        }}
        onKeyDown={onKeyDown}
    >
        <AutoSizer>
            {({height, width}) => <FixedSizeList
                ref={listRef}
                height={height}
                width={width}
                itemCount={rows.length}
                itemSize={props.rowHeight ?? 28}
                itemData={itemData}
                itemKey={(index, data) => data.rows[index].id}
                overscanCount={props.overscanCount ?? 8}
            >
                {VirtualizedTreeItem}
            </FixedSizeList>}
        </AutoSizer>
    </div>;
}

function VirtualizedTreeItem<T>({index, style, data}: ListChildComponentProps<RowData<T>>): React.ReactNode {
    const row = data.rows[index];
    const focused = data.focusedId === row.id;
    const selected = data.selectedIds.has(row.id);
    const branch = data.isBranch(row.node);
    const expanded = branch && data.expanded.has(row.id);

    return <div style={style}>
        <div
            className="virtualized-tree-row"
            id={treeItemId(row.id)}
            role="treeitem"
            aria-label={data.ariaLabel(row.node)}
            aria-level={row.depth}
            aria-posinset={row.positionInSet}
            aria-setsize={row.setSize}
            aria-selected={selected}
            aria-expanded={branch ? expanded : undefined}
            data-selected={selected ? "true" : undefined}
            data-focused={focused ? "true" : undefined}
            style={{paddingLeft: `${(row.depth - 1) * data.indent + 6}px`}}
            onPointerDown={event => {
                if (event.button === 0) data.select(row);
            }}
            onDoubleClick={() => data.activate(row)}
            onMouseEnter={() => data.hover(row)}
            onContextMenu={event => data.contextMenu(event, row)}
        >
            {data.renderNode(row.node, {
                expanded,
                focused,
                selected,
                toggle: () => {
                    data.toggle(row);
                },
            })}
        </div>
    </div>;
}

const VirtualizedTreeClass = injectStyle("virtualized-tree", k => `
    ${k} {
        width: 100%;
        height: 100%;
        min-height: 0;
        outline: none;
    }

    ${k} .virtualized-tree-row {
        height: 100%;
        display: flex;
        align-items: center;
        box-sizing: border-box;
        cursor: default;
        user-select: none;
        overflow: hidden;
    }

    ${k} .virtualized-tree-row:hover {
        background: var(--rowHover);
    }

    ${k} .virtualized-tree-row[data-selected="true"] {
        background-color: var(--rowHover);
    }

    ${k}[data-focused="true"] .virtualized-tree-row[data-selected="true"] {
        background-color: var(--rowActive);
    }

`);
