import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState, useSyncExternalStore} from "react";
import {Box, Button, Icon, Input, Text} from "@/ui-components";
import {ActionEntry, ActionMenu} from "@/ui-components/Actions";
import {IconButton} from "@/ui-components/IconButton";
import {IconName} from "@/ui-components/Icon";
import {Table, TableCell, TableHeader, TableHeaderCell, TableRow as UiTableRow} from "@/ui-components/Table";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {VirtualizedTree, VirtualizedTreeApi} from "@/ui-components/VirtualizedTree";
import {injectStyle} from "@/Unstyled";
import {copyToClipboard, isLikelyMac} from "@/UtilityFunctions";
import {TableColumn, TableColumnSortType, TableRow, TableRowAction, TableUpdate} from "@/UCX/protocol";
import {UcxSpinner} from "@/UCX/UcxSpinner";
import {
    UCX_MIN_COLUMN_WIDTH,
    computeColumnWidths,
    resolveColumnWidths,
    resetUserColumnWidth,
    setUserColumnWidth,
} from "@/UCX/UcxTableColumns";

const UCX_ACTION_COLUMN_WIDTH = 40;

export interface UcxTableStats {
    filtered: number;
    total: number;
    loaded: boolean;
}

export interface UcxTableSort {
    columnKey: string;
    ascending: boolean;
}

export interface UcxTableViewHandler {
    activate(): void;
    focus(): void;
}

type UcxTableStoreListener = () => void;

export class UcxTableStore {
    private rows = new Map<string, Map<string, TableRow>>();
    private columns = new Map<string, TableColumn[]>();
    private filters = new Map<string, string>();
    private scrolls = new Map<string, number>();
    private selected = new Map<string, string | null>();
    private stats = new Map<string, UcxTableStats>();
    private sorts = new Map<string, UcxTableSort>();
    private loaded = new Set<string>();
    private activeStates = new Map<string, string>();
    private views = new Map<string, UcxTableViewHandler>();
    private listeners = new Set<UcxTableStoreListener>();
    private revision = 0;

    apply(update: TableUpdate) {
        const typeRows = this.rows.get(update.tableId) ?? new Map<string, TableRow>();
        if (update.snapshot) {
            typeRows.clear();
        }
        for (const key of update.removed) {
            typeRows.delete(key);
        }
        for (const row of update.upserts) {
            typeRows.set(row.key, row);
        }
        this.rows.set(update.tableId, typeRows);

        if (update.columns.length > 0) {
            this.columns.set(update.tableId, update.columns);
        }

        this.loaded.add(update.tableId);

        this.notify();
    }

    reset() {
        this.rows = new Map();
        this.columns = new Map();
        this.filters = new Map();
        this.scrolls = new Map();
        this.selected = new Map();
        this.stats = new Map();
        this.sorts = new Map();
        this.activeStates = new Map();
        this.loaded = new Set();
        this.notify();
    }

    getRevision(): number {
        return this.revision;
    }

    rowsFor(tableId: string): Map<string, TableRow> {
        return this.rows.get(tableId) ?? new Map<string, TableRow>();
    }

    loadedFor(tableId: string): boolean {
        return this.loaded.has(tableId);
    }

    columnsFor(tableId: string): TableColumn[] {
        return this.columns.get(tableId) ?? [];
    }

    filterFor(stateKey: string): string {
        return this.filters.get(stateKey) ?? "";
    }

    setFilter(stateKey: string, value: string) {
        if (this.filters.get(stateKey) === value) return;
        this.filters.set(stateKey, value);
        this.notify();
    }

    scrollFor(stateKey: string): number {
        return this.scrolls.get(stateKey) ?? 0;
    }

    selectedFor(stateKey: string): string | null {
        return this.selected.get(stateKey) ?? null;
    }

    setSelected(stateKey: string, key: string | null) {
        if (this.selected.get(stateKey) === key) return;
        if (key === null) {
            this.selected.delete(stateKey);
        } else {
            this.selected.set(stateKey, key);
        }
        this.notify();
    }

    statsFor(stateKey: string): UcxTableStats {
        return this.stats.get(stateKey) ?? {filtered: 0, total: 0, loaded: false};
    }

    setStats(stateKey: string, stats: UcxTableStats) {
        const previous = this.stats.get(stateKey);
        if (previous && previous.filtered === stats.filtered && previous.total === stats.total
            && previous.loaded === stats.loaded) return;
        this.stats.set(stateKey, stats);
        this.notify();
    }

    sortFor(stateKey: string): UcxTableSort | null {
        return this.sorts.get(stateKey) ?? null;
    }

    setSort(stateKey: string, sort: UcxTableSort | null) {
        const previous = this.sorts.get(stateKey) ?? null;
        if (previous === sort || (previous !== null && sort !== null
            && previous.columnKey === sort.columnKey && previous.ascending === sort.ascending)) return;
        if (sort === null) {
            this.sorts.delete(stateKey);
        } else {
            this.sorts.set(stateKey, sort);
        }
        this.notify();
    }

    claimView(viewId: string, stateKey: string): string | null {
        const previous = this.activeStates.get(viewId) ?? null;
        this.activeStates.set(viewId, stateKey);
        return previous;
    }

    clearViewState(stateKey: string) {
        const hadFilter = this.filters.delete(stateKey);
        const hadScroll = this.scrolls.delete(stateKey);
        const hadSelected = this.selected.delete(stateKey);
        const hadSort = this.sorts.delete(stateKey);
        if (hadFilter || hadScroll || hadSelected || hadSort) {
            this.notify();
        }
    }

    saveScrollIfCurrent(viewId: string, stateKey: string, scrollTop: number) {
        if (this.activeStates.get(viewId) !== stateKey) return;
        this.scrolls.set(stateKey, scrollTop);
    }

    registerView(stateKey: string, handler: UcxTableViewHandler) {
        this.views.set(stateKey, handler);
    }

    unregisterView(stateKey: string, handler: UcxTableViewHandler) {
        if (this.views.get(stateKey) === handler) {
            this.views.delete(stateKey);
        }
    }

    viewFor(stateKey: string): UcxTableViewHandler | undefined {
        return this.views.get(stateKey);
    }

    subscribe(listener: UcxTableStoreListener): () => void {
        this.listeners.add(listener);
        return () => {
            this.listeners.delete(listener);
        };
    }

    private notify() {
        this.revision++;
        for (const listener of this.listeners) {
            listener();
        }
    }
}

function useUcxTableRevision(store: UcxTableStore): number {
    return useSyncExternalStore(
        listener => store.subscribe(listener),
        () => store.getRevision(),
    );
}

const DURATION_MULTIPLIERS: Record<string, number> = {
    s: 1,
    m: 60,
    h: 3600,
    d: 86400,
};

const CAPACITY_MULTIPLIERS: Record<string, number> = {
    ki: 1024,
    mi: 1024 ** 2,
    gi: 1024 ** 3,
    ti: 1024 ** 4,
    k: 1000,
    m: 1000 ** 2,
    g: 1000 ** 3,
    t: 1000 ** 4,
};

function compareNumbers(a: number, b: number): number {
    if (a < b) return -1;
    if (a > b) return 1;
    return 0;
}

function compareDurations(a: string, b: string): number {
    const left = parseDurationSeconds(a);
    const right = parseDurationSeconds(b);
    if (left === null || right === null) return NaN;
    return compareNumbers(left, right);
}

function parseDurationSeconds(text: string): number | null {
    const match = /^(\d+)([smhd])$/.exec(text.trim());
    if (!match) return null;
    const multiplier = DURATION_MULTIPLIERS[match[2]];
    if (multiplier === undefined) return null;
    return parseInt(match[1], 10) * multiplier;
}

function compareRatios(a: string, b: string): number {
    const left = a.trim().split("/");
    const right = b.trim().split("/");
    if (left.length !== 2 || right.length !== 2) return NaN;
    const leftNum = Number(left[0]);
    const leftDen = Number(left[1]);
    const rightNum = Number(right[0]);
    const rightDen = Number(right[1]);
    if (!Number.isFinite(leftNum) || !Number.isFinite(leftDen) || !Number.isFinite(rightNum) || !Number.isFinite(rightDen)) {
        return NaN;
    }
    if (leftDen === 0 && rightDen === 0) return compareNumbers(leftNum, rightNum);
    if (leftDen === 0 || rightDen === 0) return leftDen === 0 ? 1 : -1;
    return compareNumbers(leftNum / leftDen, rightNum / rightDen);
}

function compareNumbersText(a: string, b: string): number {
    const left = Number(a.trim());
    const right = Number(b.trim());
    if (!Number.isFinite(left) || !Number.isFinite(right)) return NaN;
    return compareNumbers(left, right);
}

function compareCapacities(a: string, b: string): number {
    const left = parseCapacityBytes(a);
    const right = parseCapacityBytes(b);
    if (left === null || right === null) return NaN;
    return compareNumbers(left, right);
}

function parseCapacityBytes(text: string): number | null {
    const match = /^(\d+(?:\.\d+)?)\s*([kKmMgGtT][iI]?)$/.exec(text.trim());
    if (!match) {
        const plain = Number(text.trim());
        if (Number.isFinite(plain)) return plain;
        return null;
    }
    const multiplier = CAPACITY_MULTIPLIERS[match[2].toLowerCase()];
    if (multiplier === undefined) return null;
    return parseFloat(match[1]) * multiplier;
}

function compareIps(a: string, b: string): number {
    const left = parseIpOctets(a);
    const right = parseIpOctets(b);
    if (left === null || right === null) return NaN;
    for (let i = 0; i < 4; i++) {
        const cmp = compareNumbers(left[i], right[i]);
        if (cmp !== 0) return cmp;
    }
    return 0;
}

function parseIpOctets(text: string): number[] | null {
    const parts = text.trim().split(".");
    if (parts.length !== 4) return null;
    const octets: number[] = [];
    for (const part of parts) {
        if (!/^\d{1,3}$/.test(part)) return null;
        const value = parseInt(part, 10);
        if (value > 255) return null;
        octets.push(value);
    }
    return octets;
}

function compareBools(a: string, b: string): number {
    const left = parseBoolValue(a);
    const right = parseBoolValue(b);
    if (left === null || right === null) return NaN;
    return compareNumbers(left, right);
}

function parseBoolValue(text: string): number | null {
    const trimmed = text.trim().toLowerCase();
    if (trimmed === "true") return 1;
    if (trimmed === "false") return 0;
    return null;
}

const SORT_COMPARATORS: Partial<Record<TableColumnSortType, (a: string, b: string) => number>> = {
    [TableColumnSortType.Number]: compareNumbersText,
    [TableColumnSortType.Duration]: compareDurations,
    [TableColumnSortType.Ratio]: compareRatios,
    [TableColumnSortType.Capacity]: compareCapacities,
    [TableColumnSortType.Ip]: compareIps,
    [TableColumnSortType.Bool]: compareBools,
};

function compareCells(sortType: TableColumnSortType | undefined, a: string, b: string): number {
    const comparator = sortType === undefined ? undefined : SORT_COMPARATORS[sortType];
    if (comparator !== undefined) {
        const typed = comparator(a, b);
        if (!Number.isNaN(typed)) return typed;
    }
    return a.localeCompare(b);
}

function regionContains(root: HTMLElement | null): boolean {
    const el = document.activeElement;
    if (!root || !el || el === document.body || !(el instanceof HTMLElement)) {
        return false;
    }
    return root.contains(el);
}

function useBrowserRegionActive(ref: React.RefObject<HTMLElement | null>): boolean {
    const [active, setActive] = useState(false);

    useEffect(() => {
        const update = () => {
            const root = ref.current;
            if (!root) {
                setActive(false);
                return;
            }

            const browser = root.closest("[data-ucx-browser]");
            if (browser instanceof HTMLElement && regionContains(browser)) {
                setActive(true);
                return;
            }

            setActive(regionContains(root));
        };

        update();
        document.addEventListener("focusin", update);
        document.addEventListener("focusout", update);
        return () => {
            document.removeEventListener("focusin", update);
            document.removeEventListener("focusout", update);
        };
    }, [ref]);

    return active;
}

export function isEditableTarget(target: EventTarget | null): boolean {
    if (target instanceof HTMLTextAreaElement && target.readOnly) return false;
    return target instanceof HTMLInputElement
        || target instanceof HTMLTextAreaElement
        || (target instanceof HTMLElement && target.isContentEditable);
}

function isPrimaryBrowser(root: HTMLElement | null): boolean {
    return document.querySelector("[data-ucx-browser]") === root;
}

export type UcxBrowserPane = "sidebar" | "content" | "bottom";

interface UcxBrowserLayoutProps {
    sidebar?: React.ReactNode;
    bottom?: React.ReactNode;
    children?: React.ReactNode;
    sx?: React.CSSProperties;
    onEscape?: () => void;
    autoFocusOnPageChange?: boolean;
}

const UcxBrowserFocusContentContext = React.createContext<(() => void) | null>(null);
const UcxBrowserRestorePaneFocusContext = React.createContext<(() => () => void) | null>(null);

export function useUcxFocusContent(): () => void {
    const focus = React.useContext(UcxBrowserFocusContentContext);
    return focus ?? (() => undefined);
}

export function useUcxRestorePaneFocus(): () => () => void {
    const capture = React.useContext(UcxBrowserRestorePaneFocusContext);
    return capture ?? (() => () => undefined);
}

export const UcxBrowserLayout: React.FunctionComponent<UcxBrowserLayoutProps> = props => {
    const [activePane, setActivePane] = useState<UcxBrowserPane | null>(null);
    const activePaneRef = useRef<UcxBrowserPane>("content");
    const rootRef = useRef<HTMLDivElement | null>(null);
    const sidebarRef = useRef<HTMLDivElement | null>(null);
    const contentRef = useRef<HTMLDivElement | null>(null);
    const bottomRef = useRef<HTMLDivElement | null>(null);
    const hasSidebar = props.sidebar !== undefined && props.sidebar !== null;
    const hasBottom = props.bottom !== undefined && props.bottom !== null;
    const hasSidebarRef = useRef(hasSidebar);
    hasSidebarRef.current = hasSidebar;
    const hasBottomRef = useRef(hasBottom);
    hasBottomRef.current = hasBottom;

    const focusPane = useCallback((pane: UcxBrowserPane) => {
        if (pane === "sidebar") {
            sidebarRef.current?.focus({preventScroll: true});
        } else if (pane === "content") {
            const table = contentRef.current?.querySelector<HTMLElement>("[data-ucx-table]");
            (table ?? contentRef.current)?.focus({preventScroll: true});
        } else {
            bottomRef.current?.focus({preventScroll: true});
        }
    }, []);

    const focusContent = useCallback(() => {
        focusPane("content");
    }, [focusPane]);

    const capturePaneFocus = useCallback(() => {
        const pane = activePaneRef.current;
        return () => {
            window.requestAnimationFrame(() => {
                window.requestAnimationFrame(() => {
                    if (rootRef.current?.isConnected) focusPane(pane);
                });
            });
        };
    }, [focusPane]);

    const autoFocusOnPageChange = props.autoFocusOnPageChange !== false;

    useEffect(() => {
        if (!autoFocusOnPageChange) return;
        if (!isPrimaryBrowser(rootRef.current)) return;

        const active = document.activeElement;
        const focusOnPane = active instanceof HTMLElement
            && active.getAttribute("data-ucx-pane") != null
            && rootRef.current?.contains(active);
        if (active !== document.body && !focusOnPane) return;

        focusPane("content");
    }, [autoFocusOnPageChange, focusPane, props.children]);

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
            const paneDigit = event.code === "Digit1" ? "1" : event.code === "Digit2" ? "2" : event.code === "Digit3" ? "3" : null;
            if (paneDigit !== null && primaryPressed && event.altKey) {
                if (!regionContains(rootRef.current)) return;
                const target = paneDigit === "1" ? "sidebar" : paneDigit === "2" ? "content" : "bottom";
                const enabled = target === "sidebar" ? hasSidebarRef.current : target === "bottom" ? hasBottomRef.current : true;
                if (!enabled) return;
                event.preventDefault();
                focusPane(target);
                return;
            }

            if (event.key !== "Escape" || props.onEscape === undefined) return;
            if (event.defaultPrevented) return;
            const active = document.activeElement;
            const primary = isPrimaryBrowser(rootRef.current);
            const inRegion = regionContains(rootRef.current) || (primary && active === document.body);
            if (!inRegion) return;
            if (isEditableTarget(event.target)) {
                if (event.target instanceof HTMLElement) {
                    event.target.blur();
                }
            }

            event.preventDefault();
            props.onEscape();
        };

        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [focusPane, props.onEscape]);

    return <div
        ref={rootRef}
        className={UcxBrowserLayoutClass}
        style={props.sx}
        data-active-pane={activePane}
        data-ucx-browser="true"
        data-has-sidebar={hasSidebar ? "true" : "false"}
        data-has-bottom={hasBottom ? "true" : "false"}
        onFocus={ev => {
            const target = ev.target;
            if (!(target instanceof HTMLElement)) return;
            const pane = target.closest<HTMLElement>("[data-ucx-pane]");
            if (pane?.closest("[data-ucx-browser]") === rootRef.current) {
                const nextPane = pane.dataset.ucxPane as UcxBrowserPane;
                activePaneRef.current = nextPane;
                setActivePane(nextPane);
            }
        }}
        onBlur={ev => {
            if (!ev.currentTarget.contains(ev.relatedTarget as Node | null)) {
                setActivePane(null);
            }
        }}
        onPointerDown={ev => {
            if (!(ev.target instanceof HTMLElement)) return;
            const pane = ev.target.closest<HTMLElement>("[data-ucx-pane]");
            if (!pane || pane.closest("[data-ucx-browser]") !== rootRef.current) return;
            const focusable = ev.target.closest("button, input, select, textarea, a[href], [contenteditable], [tabindex]");
            if (ev.button === 0 && (!focusable || focusable === pane)) {
                focusPane(pane.dataset.ucxPane as UcxBrowserPane);
            } else if (ev.button === 0 && focusable instanceof HTMLElement
                && (focusable.hasAttribute("data-ucx-table") || focusable instanceof HTMLButtonElement)) {
                focusable.focus({preventScroll: true});
            }
        }}
    >
        <UcxBrowserFocusContentContext.Provider value={focusContent}>
        <UcxBrowserRestorePaneFocusContext.Provider value={capturePaneFocus}>
        {hasSidebar ?
            <aside
                ref={sidebarRef}
                tabIndex={-1}
                data-ucx-pane="sidebar"
                className={UcxBrowserSidebarClass}
                data-pane-active={activePane === "sidebar" ? "true" : undefined}
            >
                {props.sidebar}
            </aside> :
            null
        }
        <main className={UcxBrowserMainClass}>
            <div
                ref={contentRef}
                tabIndex={-1}
                data-ucx-pane="content"
                className={UcxBrowserContentClass}
                data-pane-active={activePane === "content" ? "true" : undefined}
            >
                {props.children}
            </div>
            {hasBottom ?
                <div
                    ref={bottomRef}
                    tabIndex={-1}
                    data-ucx-pane="bottom"
                    className={UcxBrowserBottomClass}
                    data-pane-active={activePane === "bottom" ? "true" : undefined}
                >
                    {props.bottom}
                </div> :
                null
            }
        </main>
        </UcxBrowserRestorePaneFocusContext.Provider>
        </UcxBrowserFocusContentContext.Provider>
    </div>;
};

export interface UcxNavItem {
    id: string;
    label: string;
    aliases?: string[];
    route?: string;
    children?: UcxNavItem[];
    separatorBefore?: boolean;
    disabled?: boolean;
}

interface UcxNavTreeNode {
    id: string;
    label: string;
    aliases: string[];
    route?: string;
    children: UcxNavTreeNode[];
    isBranch: boolean;
    separatorBefore?: boolean;
    disabled: boolean;
}

interface UcxNavTreeProps {
    nodes: UcxNavItem[];
    selectedId?: string;
    onActivate: (id: string) => void;
    commandKey?: string;
    placeholder?: string;
    searchable?: boolean;
    initialExpandedIds?: string[];
}

export const UcxNavTree: React.FunctionComponent<UcxNavTreeProps> = props => {
    const [query, setQuery] = useState<string | null>(null);
    const [commandHighlight, setCommandHighlight] = useState<number | null>(null);
    const [localSelectedId, setLocalSelectedId] = useState<string | undefined>(props.selectedId);
    const routeSyncRef = useRef<string | undefined>(props.selectedId);
    if (routeSyncRef.current !== props.selectedId) {
        routeSyncRef.current = props.selectedId;
        setLocalSelectedId(props.selectedId);
    }
    const rootRef = useRef<HTMLDivElement | null>(null);
    const treeApiRef = useRef<VirtualizedTreeApi | null>(null);
    const commandInputRef = useRef<HTMLInputElement | null>(null);
    const commandHighlightRef = useRef<number | null>(null);
    commandHighlightRef.current = commandHighlight;
    const queryRef = useRef<string | null>(null);
    queryRef.current = query;

    const allNodes = useMemo((): UcxNavTreeNode[] => props.nodes.map(toNavTreeNode), [props.nodes]);
    const regionActive = useBrowserRegionActive(rootRef);
    const searchable = props.searchable !== false;
    const commandKey = props.commandKey ?? ":";

    const matches = useCallback((item: UcxNavTreeNode, needle: string): boolean => {
        if (needle === "") return true;
        return item.label.toLowerCase().includes(needle)
            || item.id.toLowerCase().includes(needle)
            || item.aliases.some(alias => alias.toLowerCase() === needle || alias.toLowerCase().startsWith(needle));
    }, []);

    const candidates = useMemo(() => {
        const needle = (query ?? "").replace(/^:/, "").trim().toLowerCase();
        const out: UcxNavTreeNode[] = [];
        const walk = (nodes: UcxNavTreeNode[]) => {
            for (const node of nodes) {
                if (!node.isBranch && !node.disabled && matches(node, needle)) out.push(node);
                walk(node.children);
            }
        };
        walk(allNodes);
        return out;
    }, [allNodes, matches, query]);

    useEffect(() => {
        setCommandHighlight(null);
    }, [query]);

    const visibleNodes = query != null && query !== "" ? filteredNavTree(allNodes, candidates) : allNodes;
    const highlightedId = query != null && commandHighlight != null ? candidates[commandHighlight]?.id : undefined;
    const selectedTreeId = highlightedId ?? localSelectedId ?? props.selectedId;

    const focusContent = useUcxFocusContent();

    const activate = useCallback((id: string) => {
        setLocalSelectedId(id);
        setQuery(null);
        setCommandHighlight(null);
        focusContent();
        props.onActivate(id);
    }, [focusContent, props]);

    const activateCommand = useCallback((): boolean => {
        if (queryRef.current == null) return false;
        const needle = queryRef.current.replace(/^:/, "").trim().toLowerCase();
        if (needle !== "") {
            const highlighted = commandHighlightRef.current != null ? candidates[commandHighlightRef.current] : undefined;
            const exact = findExactCandidate(allNodes, needle);
            const target = highlighted
                ?? exact
                ?? candidates[0]
                ?? findLabelPrefix(allNodes, needle);
            if (target) {
                activate(target.id);
                return true;
            }
        }
        setQuery(null);
        setCommandHighlight(null);
        return false;
    }, [activate, allNodes, candidates]);

    const moveHighlight = useCallback((delta: number) => {
        if (candidates.length === 0) return;
        setCommandHighlight(prev => {
            const current = prev ?? (delta > 0 ? -1 : 0);
            const next = current + delta;
            if (next < 0) return 0;
            if (next >= candidates.length) return candidates.length - 1;
            return next;
        });
    }, [candidates.length]);

    const onSearchKeyDown = useCallback((event: React.KeyboardEvent<HTMLInputElement>) => {
        if (event.key === "Escape") {
            event.preventDefault();
            setQuery(null);
            setCommandHighlight(null);
            focusContent();
        } else if (event.key === "ArrowDown" || event.key === "Tab") {
            event.preventDefault();
            moveHighlight(1);
        } else if (event.key === "ArrowUp") {
            event.preventDefault();
            moveHighlight(-1);
        } else if (event.key === "Enter") {
            event.preventDefault();
            if (!activateCommand()) focusContent();
        }
    }, [activateCommand, focusContent, moveHighlight]);

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (!searchable || !regionActive || event.key !== commandKey || isEditableTarget(event.target)) return;
            event.preventDefault();
            setQuery(queryRef.current ?? "");
            window.setTimeout(() => commandInputRef.current?.focus(), 0);
        };

        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [commandKey, regionActive, searchable]);

    return <div
        ref={rootRef}
        className={UcxNavTreeClass}
        onFocus={event => {
            const pane = event.target instanceof HTMLElement ? event.target.closest("[data-ucx-pane]") : null;
            if (pane != null && event.target === pane) {
                treeApiRef.current?.activate();
            }
        }}
    >
        <div className="nav-tree-scroll">
            <VirtualizedTree
                apiRef={treeApiRef}
                nodes={visibleNodes}
                getId={node => node.id}
                getChildren={node => node.children}
                isBranch={node => node.isBranch}
                selectedId={selectedTreeId}
                onSelectionChange={nodes => {
                    const last = nodes[nodes.length - 1];
                    if (last && !last.isBranch) {
                        setLocalSelectedId(last.id);
                    }
                }}
                onActivate={node => {
                    if (!node.isBranch && !node.disabled) activate(node.id);
                }}
                renderNode={(node, state) => {
                    const separated = node.separatorBefore === true;
                    const disabled = node.disabled === true;
                    const body = node.isBranch
                        ? <div className="nav-tree-group" onClick={() => state.toggle()}>
                            <Icon name="heroChevronRight" size={12} rotation={state.expanded ? 90 : undefined} color="textSecondary" />
                            <span className="nav-tree-group-label">{node.label}</span>
                        </div>
                        : <div className={disabled ? "nav-tree-leaf nav-tree-leaf-disabled" : "nav-tree-leaf"}>
                            <span className="nav-tree-label">{node.label}</span>
                        </div>;
                    if (!separated) return body;
                    return <div className="nav-tree-row-separated">
                        <div className="nav-tree-separator" />
                        {body}
                    </div>;
                }}
                ariaLabel={node => node.label}
                initialExpandedIds={props.initialExpandedIds ?? allNodes.map(node => node.id)}
                rowHeight={26}
                rowHeightOf={node => node.separatorBefore === true ? 38 : 26}
                indent={12}
                selectionMode="single"
            />
        </div>
        {searchable ?
            <div className="nav-tree-command">
                <Input
                    inputRef={commandInputRef}
                    placeholder={props.placeholder ?? `${commandKey}command`}
                    value={query ?? ""}
                    onKeyDown={onSearchKeyDown}
                    onChange={ev => setQuery(ev.target.value)}
                />
                {query == null || query === "" ?
                    <span className="nav-tree-command-shortcut"><div className={ShortcutClass}>{commandKey}</div></span> :
                    null
                }
            </div> :
            null
        }
    </div>;
};

function toNavTreeNode(node: UcxNavItem): UcxNavTreeNode {
    const children = (node.children ?? []).map(toNavTreeNode);
    return {
        id: node.id,
        label: node.label,
        aliases: node.aliases ?? [],
        route: node.route,
        children,
        isBranch: children.length > 0,
        separatorBefore: node.separatorBefore === true,
        disabled: node.disabled === true,
    };
}

function filteredNavTree(all: UcxNavTreeNode[], candidates: UcxNavTreeNode[]): UcxNavTreeNode[] {
    const candidateIds = new Set(candidates.map(node => node.id));
    const rebuild = (nodes: UcxNavTreeNode[]): UcxNavTreeNode[] => {
        const out: UcxNavTreeNode[] = [];
        for (const node of nodes) {
            const children = rebuild(node.children);
            if (children.length > 0) {
                out.push({...node, children, isBranch: true});
            } else if (candidateIds.has(node.id)) {
                out.push({...node, isBranch: false});
            }
        }
        return out;
    };
    return rebuild(all);
}

function findExactCandidate(nodes: UcxNavTreeNode[], needle: string): UcxNavTreeNode | undefined {
    for (const node of nodes) {
        if (!node.isBranch && !node.disabled) {
            if (node.aliases.some(alias => alias.toLowerCase() === needle)
                || node.label.toLowerCase() === needle
                || node.id.toLowerCase() === needle) {
                return node;
            }
        }
        const found = findExactCandidate(node.children, needle);
        if (found) return found;
    }
    return undefined;
}

function findLabelPrefix(nodes: UcxNavTreeNode[], needle: string): UcxNavTreeNode | undefined {
    for (const node of nodes) {
        if (!node.isBranch && !node.disabled && node.label.toLowerCase().startsWith(needle)) return node;
        const found = findLabelPrefix(node.children, needle);
        if (found) return found;
    }
    return undefined;
}

export interface UcxTableActivationEvent {
    tableId: string;
    rowKey: string;
    row: TableRow;
}

export interface UcxTableActionDef {
    id: string;
    label: string;
    icon?: string;
    kind: string;
    color?: string;
    shortcut?: string;
}

export interface UcxTableActionEvent {
    tableId: string;
    rowKey: string;
    actionId: string;
}

export interface UcxTableGroupActionEvent {
    actionId: string;
    group: string;
}

interface UcxStreamedTableProps {
    tableId: string;
    store: UcxTableStore;
    viewId?: string;
    stateKey?: string;
    emptyMessage?: string;
    showGroupHeaders?: boolean;
    sorted?: boolean;
    actions?: UcxTableActionDef[];
    groupAction?: UcxTableActionDef;
    trailingAction?: UcxTableActionDef;
    onRowActivated: (event: UcxTableActivationEvent) => void;
    onRowAction?: (event: UcxTableActionEvent) => void;
    onGroupAction?: (event: UcxTableGroupActionEvent) => void;
    onTrailingAction?: (event: UcxTableGroupActionEvent) => void;
}

export const UcxStreamedTable: React.FunctionComponent<UcxStreamedTableProps> = props => {
    const store = props.store;
    const tableId = props.tableId;
    const viewId = props.viewId ?? `table:${tableId}`;
    const stateKey = props.stateKey ?? tableId;
    const revision = useUcxTableRevision(store);
    const loaded = store.loadedFor(tableId);
    const rowMap = store.rowsFor(tableId);
    const rows = useMemo(() => Array.from(rowMap.values()), [rowMap, revision]);
    const columns = store.columnsFor(tableId);
    const filter = store.filterFor(stateKey);
    const selectedKey = store.selectedFor(stateKey);
    const rootRef = useRef<HTMLDivElement | null>(null);
    const scrollRef = useRef<HTMLDivElement | null>(null);
    useLayoutEffect(() => {
        const pane = rootRef.current?.closest("[data-ucx-pane='content']");
        if (pane && document.activeElement === pane) {
            rootRef.current?.focus({preventScroll: true});
        }
    }, []);
    const selectedKeyRef = useRef<string | null>(null);
    selectedKeyRef.current = selectedKey;
    const scrollTopRef = useRef(0);
    const [dragColumn, setDragColumn] = useState<string | null>(null);
    const [widthRevision, setWidthRevision] = useState(0);
    const [viewportWidth, setViewportWidth] = useState(0);
    const actionDefs = props.actions ?? [];
    const hasActionDefs = actionDefs.length > 0;
    const groupAction = props.groupAction;
    const trailingAction = props.trailingAction;
    const [menuRowKey, setMenuRowKey] = useState<string | null>(null);
    const [menuPosition, setMenuPosition] = useState({x: 0, y: 0});
    const [menuRenderTick, setMenuRenderTick] = useState(0);
    const menuOpenRef = useRef<((left: number, top: number) => void) | null>(null);
    const menuCloseRef = useRef<(() => void) | null>(null);
    const menuOpenRefState = useRef(false);
    const menuRowKeyRef = useRef<string | null>(null);
    menuRowKeyRef.current = menuRowKey;
    menuOpenRefState.current = menuRowKey !== null;

    const autoWidths = useMemo(
        () => computeColumnWidths(columns, Array.from(rowMap.values())),
        [columns, rowMap],
    );
    const widths = useMemo(
        () => resolveColumnWidths(columns, autoWidths),
        [columns, autoWidths, widthRevision],
    );
    const dataWidth = useMemo(() => Object.values(widths).reduce((a, b) => a + b, 0), [widths]);
    const totalWidth = hasActionDefs && dataWidth > 0
        ? Math.max(dataWidth + UCX_ACTION_COLUMN_WIDTH, viewportWidth)
        : dataWidth + (hasActionDefs ? UCX_ACTION_COLUMN_WIDTH : 0);
    const renderedWidths = useMemo(() => {
        if (!hasActionDefs || dataWidth === 0) return widths;
        const scale = (totalWidth - UCX_ACTION_COLUMN_WIDTH) / dataWidth;
        const result: Record<string, number> = {};
        for (const column of columns) result[column.key] = widths[column.key] * scale;
        return result;
    }, [columns, widths, dataWidth, totalWidth, hasActionDefs]);

    useLayoutEffect(() => {
        if (!hasActionDefs) return;
        const viewport = scrollRef.current;
        if (!viewport) return;
        const updateWidth = () => setViewportWidth(viewport.clientWidth);
        updateWidth();
        const observer = new ResizeObserver(updateWidth);
        observer.observe(viewport);
        return () => observer.disconnect();
    }, [hasActionDefs]);

    useLayoutEffect(() => {
        const previous = store.claimView(viewId, stateKey);
        if (previous === stateKey) return;
        store.clearViewState(stateKey);
    }, [store, stateKey, viewId]);

    useEffect(() => {
        return () => {
            store.saveScrollIfCurrent(viewId, stateKey, scrollTopRef.current);
        };
    }, [stateKey, store, viewId]);

    useLayoutEffect(() => {
        const el = scrollRef.current;
        if (el) el.scrollTop = store.scrollFor(stateKey);
    }, [stateKey, store]);

    const orderedRows = useMemo(() => {
        const sorted = [...rows];
        const sort = props.sorted !== false ? store.sortFor(stateKey) : null;
        const sortIdx = sort !== null ? columns.findIndex(col => col.key === sort.columnKey) : -1;
        if (sort !== null && sortIdx >= 0) {
            const sortType = columns[sortIdx].sortType;
            const direction = sort.ascending ? 1 : -1;
            const groupPrimary = props.showGroupHeaders !== false;
            sorted.sort((a, b) => {
                if (groupPrimary) {
                    const groupCmp = a.group.localeCompare(b.group);
                    if (groupCmp !== 0) return groupCmp;
                }
                const cellCmp = compareCells(sortType, a.cells[sortIdx], b.cells[sortIdx]);
                if (cellCmp !== 0) return cellCmp * direction;
                const groupCmp = a.group.localeCompare(b.group);
                if (groupCmp !== 0) return groupCmp;
                return a.cells[0].localeCompare(b.cells[0]);
            });
            return sorted;
        }
        if (props.sorted !== false) {
            sorted.sort((a, b) => {
                const groupCmp = a.group.localeCompare(b.group);
                if (groupCmp !== 0) return groupCmp;
                return a.cells[0].localeCompare(b.cells[0]);
            });
        }
        return sorted;
    }, [rows, props.sorted, columns, stateKey, store, revision]);

    const filteredRows = useMemo(() => {
        const needle = filter.trim().toLowerCase();
        if (needle === "") return orderedRows;
        return orderedRows.filter(row => row.cells.some(cell => cell.toLowerCase().includes(needle)));
    }, [orderedRows, filter]);

    const groupedRows: {group: string; rows: TableRow[]}[] = [];
    for (const row of filteredRows) {
        const last = groupedRows[groupedRows.length - 1];
        if (last && last.group === row.group) {
            last.rows.push(row);
        } else {
            groupedRows.push({group: row.group, rows: [row]});
        }
    }

    useEffect(() => {
        store.setStats(stateKey, {filtered: filteredRows.length, total: orderedRows.length, loaded});
    }, [filteredRows.length, orderedRows.length, stateKey, store, tableId, loaded]);

    const toggleSort = useCallback((columnKey: string) => {
        const current = store.sortFor(stateKey);
        if (current === null || current.columnKey !== columnKey) {
            store.setSort(stateKey, {columnKey, ascending: true});
        } else if (current.ascending) {
            store.setSort(stateKey, {columnKey, ascending: false});
        } else {
            store.setSort(stateKey, null);
        }
    }, [stateKey, store]);

    const moveSelection = useCallback((key: string) => {
        if (filteredRows.length === 0) return;

        const currentIndex = selectedKeyRef.current != null
            ? filteredRows.findIndex(row => row.key === selectedKeyRef.current)
            : -1;

        let nextIndex: number;
        if (key === "Home") {
            nextIndex = 0;
        } else if (key === "End") {
            nextIndex = filteredRows.length - 1;
        } else if (key === "PageUp" || key === "PageDown") {
            const first = scrollRef.current?.querySelector<HTMLElement>("[data-row-key]");
            const rowHeight = first?.offsetHeight ?? 32;
            const viewportHeight = scrollRef.current?.clientHeight ?? 10 * rowHeight;
            const pageSize = Math.max(1, Math.floor(viewportHeight / Math.max(1, rowHeight)) - 1);
            if (currentIndex < 0) {
                nextIndex = key === "PageDown"
                    ? Math.min(pageSize - 1, filteredRows.length - 1)
                    : 0;
            } else {
                const delta = key === "PageDown" ? pageSize : -pageSize;
                nextIndex = Math.min(Math.max(currentIndex + delta, 0), filteredRows.length - 1);
            }
        } else {
            nextIndex = key === "j" || key === "ArrowDown"
                ? Math.min(currentIndex + 1, filteredRows.length - 1)
                : currentIndex < 0 ? filteredRows.length - 1 : Math.max(currentIndex - 1, 0);
        }
        if (nextIndex === currentIndex) return;

        const selected = filteredRows[nextIndex];
        store.setSelected(stateKey, selected.key);

        window.setTimeout(() => {
            const container = scrollRef.current;
            if (!container) return;
            const rowEl = container.querySelector<HTMLElement>(`[data-row-key="${CSS.escape(selected.key)}"]`);
            if (!rowEl) return;
            const header = container.querySelector("thead");
            const headerHeight = header instanceof HTMLTableSectionElement ? header.offsetHeight : 0;
            const containerRect = container.getBoundingClientRect();
            const rowRect = rowEl.getBoundingClientRect();
            if (rowRect.top < containerRect.top + headerHeight) {
                container.scrollTop -= containerRect.top + headerHeight - rowRect.top;
            } else if (rowRect.bottom > containerRect.bottom) {
                container.scrollTop += rowRect.bottom - containerRect.bottom;
            }
        }, 0);
    }, [filteredRows, stateKey, store]);

    const activateSelection = useCallback(() => {
        const selected = selectedKeyRef.current != null
            ? filteredRows.find(row => row.key === selectedKeyRef.current)
            : undefined;
        if (selected) {
            props.onRowActivated({tableId, rowKey: selected.key, row: selected});
        }
    }, [filteredRows, props, tableId]);

    const triggerActionByShortcut = useCallback((shortcut: string, rawKey?: string) => {
        if (shortcut === "") return false;
        const selected = selectedKeyRef.current != null
            ? filteredRows.find(row => row.key === selectedKeyRef.current)
            : undefined;
        if (selected) {
            const def = actionDefs.find(candidate => {
                const candidateShortcut = candidate.shortcut ?? "";
                if (candidateShortcut === "") return false;
                if (candidateShortcut !== candidateShortcut.toLowerCase()) {
                    return rawKey !== undefined && candidateShortcut === rawKey;
                }
                return candidateShortcut.toLowerCase() === shortcut;
            });
            if (def) {
                const action = selected.actions?.find(candidate => candidate.id === def.id);
                if (action && action.enabled) {
                    if (def.kind === "copyText" && action.text !== undefined) {
                        copyToClipboard(action.text);
                    } else {
                        props.onRowAction?.({tableId, rowKey: selected.key, actionId: def.id});
                    }
                    return true;
                }
            }
        }
        if (groupAction?.shortcut && groupAction.shortcut !== "" && groupAction.shortcut.toLowerCase() === shortcut && selected && selected.group !== "") {
            props.onGroupAction?.({actionId: groupAction.id, group: selected.group});
            return true;
        }
        if (trailingAction?.shortcut && trailingAction.shortcut !== "" && trailingAction.shortcut.toLowerCase() === shortcut) {
            props.onTrailingAction?.({actionId: trailingAction.id, group: ""});
            return true;
        }
        return false;
    }, [actionDefs, filteredRows, groupAction, props, tableId, trailingAction]);

    const startColumnResize = useCallback((columnKey: string, event: React.PointerEvent<HTMLDivElement>) => {
        event.preventDefault();
        event.stopPropagation();
        const handle = event.currentTarget;
        const cell = handle.parentElement;
        const table = handle.closest("table");
        if (!(cell instanceof HTMLElement) || !(table instanceof HTMLTableElement)) return;
        const colElement = table.querySelector<HTMLTableColElement>(`col[data-col-key="${CSS.escape(columnKey)}"]`);
        if (!colElement) return;
        handle.setPointerCapture(event.pointerId);
        const startX = event.clientX;
        const startConfigured = widths[columnKey];
        const startRendered = cell.getBoundingClientRect().width;
        const gain = Math.max(0.25, startRendered / startConfigured);
        let applied = startConfigured;
        setDragColumn(columnKey);
        const onMove = (moveEvent: PointerEvent) => {
            const target = Math.max(UCX_MIN_COLUMN_WIDTH, startRendered + (moveEvent.clientX - startX));
            if (hasActionDefs) {
                const otherWidth = dataWidth - startConfigured;
                const availableWidth = (scrollRef.current?.clientWidth ?? 0) - UCX_ACTION_COLUMN_WIDTH;
                const fitsWithoutStretching = target + otherWidth >= availableWidth;
                applied = target;
                if (!fitsWithoutStretching && otherWidth > 0) {
                    applied = Math.max(UCX_MIN_COLUMN_WIDTH, target * otherWidth / (availableWidth - target));
                }
            } else {
                for (let i = 0; i < 4; i++) {
                    const rendered = cell.getBoundingClientRect().width;
                    const error = target - rendered;
                    if (Math.abs(error) < 0.5) break;
                    applied = Math.max(UCX_MIN_COLUMN_WIDTH, applied + error / gain);
                    colElement.style.width = `${applied}px`;
                }
            }
            setUserColumnWidth(columnKey, applied);
            setWidthRevision(value => value + 1);
        };
        const onUp = () => {
            if (handle.hasPointerCapture(event.pointerId)) {
                handle.releasePointerCapture(event.pointerId);
            }
            handle.removeEventListener("pointermove", onMove);
            handle.removeEventListener("pointerup", onUp);
            handle.removeEventListener("pointercancel", onUp);
            setDragColumn(null);
        };
        handle.addEventListener("pointermove", onMove);
        handle.addEventListener("pointerup", onUp);
        handle.addEventListener("pointercancel", onUp);
    }, [widths, dataWidth, hasActionDefs]);

    const resetColumnWidth = useCallback((columnKey: string) => {
        resetUserColumnWidth(columnKey);
        setWidthRevision(value => value + 1);
    }, []);

    const closeRowMenu = useCallback(() => {
        setMenuRowKey(null);
    }, []);

    const openRowMenu = useCallback((row: TableRow, x: number, y: number) => {
        if (!hasActionDefs) return;
        if (!row.actions || row.actions.length === 0) return;
        store.setSelected(stateKey, row.key);
        setMenuPosition({x, y});
        setMenuRowKey(row.key);
        setMenuRenderTick(value => value + 1);
    }, [hasActionDefs, stateKey, store]);

    const openRowMenuAtButton = useCallback((row: TableRow, button: HTMLElement) => {
        const rect = button.getBoundingClientRect();
        openRowMenu(row, rect.right, rect.bottom + 2);
    }, [openRowMenu]);

    const toggleRowMenuAtButton = useCallback((row: TableRow, button: HTMLElement) => {
        if (menuRowKeyRef.current === row.key) {
            menuCloseRef.current?.();
            menuRowKeyRef.current = null;
            return;
        }
        menuRowKeyRef.current = row.key;
        openRowMenuAtButton(row, button);
    }, [openRowMenuAtButton]);

    const openMenuForSelection = useCallback(() => {
        const selected = selectedKeyRef.current != null
            ? filteredRows.find(row => row.key === selectedKeyRef.current)
            : undefined;
        if (!selected || (selected.actions?.length ?? 0) === 0) return;
        const button = scrollRef.current
            ?.querySelector<HTMLButtonElement>(`[data-row-key="${CSS.escape(selected.key)}"] .streamed-table-actions-button`);
        if (!button) return;
        toggleRowMenuAtButton(selected, button);
    }, [filteredRows, toggleRowMenuAtButton]);

    const rowMenuEntries = useMemo((): ActionEntry<TableRow, undefined>[] => {
        if (menuRowKey === null) return [];
        const row = store.rowsFor(tableId).get(menuRowKey);
        if (!row || !row.actions || row.actions.length === 0) return [];
        const entries: ActionEntry<TableRow, undefined>[] = [];
        for (const def of actionDefs) {
            const action = row.actions.find(candidate => candidate.id === def.id);
            if (!action) continue;
            entries.push({
                text: def.label,
                icon: def.icon !== "" ? (def.icon as IconName) : undefined,
                enabled: action.enabled
                    ? () => true
                    : () => action.disabledReason ?? false,
                onClick: (selected) => {
                    const target = selected[0];
                    const entry = target.actions?.find(candidate => candidate.id === def.id);
                    if (!entry) return;
                    if (def.kind === "copyText" && entry.text !== undefined) {
                        copyToClipboard(entry.text);
                    } else {
                        props.onRowAction?.({tableId, rowKey: target.key, actionId: def.id});
                    }
                },
                shortcut: def.shortcut && def.shortcut !== "" ? {code: `Key${def.shortcut.toUpperCase()}`, key: def.shortcut, modifier: undefined} : undefined,
            });
        }
        return entries;
    }, [actionDefs, menuRowKey, props, store, tableId]);

    useEffect(() => {
        if (menuRenderTick === 0) return;
        if (menuRowKey === null) return;
        menuOpenRef.current?.(menuPosition.x, menuPosition.y);
    }, [menuRenderTick, menuRowKey, menuPosition.x, menuPosition.y]);

    useEffect(() => {
        const handler: UcxTableViewHandler = {
            activate: activateSelection,
            focus: () => rootRef.current?.focus({preventScroll: true}),
        };
        store.registerView(stateKey, handler);
        return () => store.unregisterView(stateKey, handler);
    }, [activateSelection, stateKey, store]);

    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            const root = rootRef.current;
            if (!root) return;
            const browser = root.closest("[data-ucx-browser]");
            const filterFocused = event.target instanceof HTMLInputElement
                && event.target.dataset.ucxTableFilter === stateKey
                && (browser == null || browser.contains(event.target));
            if (!regionContains(root) && !filterFocused) return;
            if (isEditableTarget(event.target) && !filterFocused) return;
            if (menuOpenRefState.current) return;
            if (filterFocused && event.key !== "ArrowDown" && event.key !== "ArrowUp") return;

            const movementKeys = ["j", "k", "ArrowDown", "ArrowUp", "Home", "End", "PageUp", "PageDown"];
            if (movementKeys.includes(event.key)) {
                if (event.ctrlKey || event.metaKey || event.altKey) return;
                event.preventDefault();
                moveSelection(event.key);
                return;
            }

            if (event.key === "g" || event.key === "G") {
                if (event.ctrlKey || event.metaKey || event.altKey) return;
                event.preventDefault();
                moveSelection(event.key === "g" ? "Home" : "End");
                return;
            }

            if (filterFocused) return;

            if (event.key === "/") {
                const input = document.querySelector<HTMLInputElement>(`[data-ucx-table-filter="${CSS.escape(stateKey)}"]`);
                if (!input) return;
                event.preventDefault();
                input.focus();
                return;
            }

            if (event.key === "Escape") {
                if (selectedKeyRef.current == null) return;
                event.preventDefault();
                store.setSelected(stateKey, null);
                return;
            }

            if (event.key === "x" || event.key === "ContextMenu") {
                if (event.ctrlKey || event.metaKey || event.altKey) return;
                event.preventDefault();
                openMenuForSelection();
                return;
            }

            if (event.key === "Enter") {
                event.preventDefault();
                activateSelection();
                return;
            }

            if (!event.ctrlKey && !event.metaKey && !event.altKey && event.key.length === 1) {
                if (triggerActionByShortcut(event.key.toLowerCase(), event.key)) {
                    event.preventDefault();
                }
            }
        };

        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [activateSelection, moveSelection, openMenuForSelection, stateKey, store, triggerActionByShortcut]);

    const emptyMessage = props.emptyMessage ?? "No resources found.";
    const colCount = columns.length + (hasActionDefs ? 1 : 0);
    const filterActive = filter.trim() !== "";
    const emptyRowContent = !loaded ?
        <span className="streamed-table-loading">
            <UcxSpinner size={16} />
            <Text color="textSecondary">Loading resources...</Text>
        </span> :
        (filterActive && orderedRows.length > 0 ?
            <Text color="textSecondary">No resources match your filter.</Text> :
            <Text color="textSecondary">{emptyMessage}</Text>
        );

    return <div
        ref={rootRef}
        tabIndex={-1}
        className={UcxStreamedTableClass}
        data-ucx-table={tableId}
        data-has-actions={hasActionDefs ? "true" : undefined}
        style={{"--ucx-table-width": `${totalWidth}px`} as React.CSSProperties}
    >
        <div className="streamed-table-frame">
            <div
                className="streamed-table-scroll"
                ref={scrollRef}
                onScroll={ev => {
                    scrollTopRef.current = ev.currentTarget.scrollTop;
                }}
                onMouseDown={() => rootRef.current?.focus({preventScroll: true})}
            >
            <Table tableType="presentation" aria-role="grid">
                <colgroup>
                    {columns.map(col => <col key={col.key} data-col-key={col.key} style={{width: `${renderedWidths[col.key]}px`}} />)}
                    {hasActionDefs ? <col className="streamed-table-actions-col" style={{width: `${UCX_ACTION_COLUMN_WIDTH}px`}} /> : null}
                </colgroup>
                <TableHeader>
                    <UiTableRow>
                        {columns.map(col => {
                            const sort = store.sortFor(stateKey);
                            const isSortColumn = sort !== null && sort.columnKey === col.key;
                            return <TableHeaderCell key={col.key}>
                                <span
                                    className="streamed-table-header-label"
                                    data-sort-active={isSortColumn ? "true" : undefined}
                                    tabIndex={0}
                                    role="button"
                                    aria-label={`Sort by ${col.label}`}
                                    onClick={event => {
                                        event.stopPropagation();
                                        toggleSort(col.key);
                                    }}
                                    onKeyDown={event => {
                                        if (event.key === "Enter" || event.key === " ") {
                                            event.preventDefault();
                                            event.stopPropagation();
                                            toggleSort(col.key);
                                        }
                                    }}
                                >
                                    {col.label}
                                    {isSortColumn ?
                                        <Icon
                                            className="streamed-table-sort-arrow"
                                            name={sort!.ascending ? "heroChevronUp" : "heroChevronDown"}
                                            size={11}
                                            color="textPrimary"
                                        /> :
                                        null
                                    }
                                </span>
                                <div
                                    className="streamed-table-resize-handle"
                                    data-dragging={dragColumn === col.key}
                                    onPointerDown={event => startColumnResize(col.key, event)}
                                    onDoubleClick={event => {
                                        event.stopPropagation();
                                        resetColumnWidth(col.key);
                                    }}
                                    onClick={event => event.stopPropagation()}
                                    onMouseDown={event => event.stopPropagation()}
                                />
                            </TableHeaderCell>;
                        })}
                        {hasActionDefs ?
                            <TableHeaderCell>
                                <div className="streamed-table-actions-header" />
                            </TableHeaderCell> :
                            null
                        }
                    </UiTableRow>
                </TableHeader>
                <tbody role="rowgroup">
                    {groupedRows.length === 0 ?
                        <UiTableRow>
                            <TableCell colSpan={colCount}>
                                {emptyRowContent}
                            </TableCell>
                        </UiTableRow> :
                        null
                    }
                    {groupedRows.map((group, groupIdx) =>
                        <React.Fragment key={groupIdx}>
                            {props.showGroupHeaders !== false && group.group !== "" ?
                                <UiTableRow className="group-row">
                                    <TableCell colSpan={columns.length + (hasActionDefs ? 1 : 0)}>
                                        <span className="group-row-inner">
                                            <span className="group-row-label">{group.group}</span>
                                            {groupAction ?
                                                <span className="group-row-action">
                                                    <IconButton
                                                        tooltip={groupAction.shortcut && groupAction.shortcut !== ""
                                                            ? `${groupAction.label} (${groupAction.shortcut})`
                                                            : groupAction.label}
                                                        icon={groupAction.icon as IconName}
                                                        compact
                                                        color="textPrimary"
                                                        onClick={() => props.onGroupAction?.({actionId: groupAction.id, group: group.group})}
                                                    />
                                                </span> :
                                                null
                                            }
                                        </span>
                                    </TableCell>
                                </UiTableRow> :
                                null
                            }
                            {group.rows.map(row => {
                                const hasActions = (row.actions?.length ?? 0) > 0;
                                const isSelected = selectedKey === row.key;
                                const isFirstRow = row.key === groupedRows[0]?.rows[0]?.key;
                                return <UiTableRow
                                    key={row.key}
                                    data-row-key={row.key}
                                    data-selected={isSelected}
                                    role="row"
                                    aria-selected={isSelected}
                                    tabIndex={isSelected || (selectedKey == null && isFirstRow) ? 0 : -1}
                                    highlightOnHover
                                    highlighted={isSelected}
                                    onClick={() => store.setSelected(stateKey, row.key)}
                                    onDoubleClick={() => props.onRowActivated({tableId, rowKey: row.key, row})}
                                    onContextMenu={hasActions ? event => {
                                        event.preventDefault();
                                        event.stopPropagation();
                                        openRowMenu(row, event.clientX, event.clientY);
                                    } : undefined}
                                >
                                    {columns.map((col, cellIdx) =>
                                        col.copy === true ?
                                            <TableCell key={col.key} role="gridcell" style={{position: "relative"}}>
                                                {row.cells[cellIdx]}
                                                {cellIdx === 0 && row.busy ? <UcxSpinner size={12} margin="0 0 0 6px" /> : null}
                                                <button
                                                    className="streamed-table-copy-button"
                                                    title="Copy name"
                                                    aria-label="Copy name"
                                                    tabIndex={-1}
                                                    onClick={event => {
                                                        event.stopPropagation();
                                                        copyToClipboard(row.cells[cellIdx]);
                                                    }}
                                                >
                                                    <Icon name="heroDocumentDuplicate" size={14} />
                                                </button>
                                            </TableCell> :
                                            <TableCell key={col.key} role="gridcell">
                                                {row.cells[cellIdx]}
                                                {cellIdx === 0 && row.busy ? <UcxSpinner size={12} margin="0 0 0 6px" /> : null}
                                            </TableCell>
                                    )}
                                    {hasActions ?
                                        <TableCell role="gridcell">
                                            <div className="streamed-table-actions-cell">
                                                <button
                                                    className="streamed-table-actions-button"
                                                    title="Row actions"
                                                    tabIndex={-1}
                                                    onClick={event => {
                                                        event.stopPropagation();
                                                        toggleRowMenuAtButton(row, event.currentTarget);
                                                    }}
                                                >
                                                    <Icon name="ellipsis" rotation={90} size={16} />
                                                </button>
                                            </div>
                                        </TableCell> :
                                        null
                                    }
                                </UiTableRow>;
                            })}
                        </React.Fragment>
                    )}
                    {trailingAction ?
                        <UiTableRow
                            className="trailing-action-row"
                            data-row-key="ucx-trailing-action"
                        >
                            <TableCell colSpan={columns.length + (hasActionDefs ? 1 : 0)}>
                                <span className="trailing-action-cell">
                                    <Button
                                        color={(trailingAction.color as any) ?? "secondaryMain"}
                                        onClick={() => props.onTrailingAction?.({actionId: trailingAction.id, group: ""})}
                                    >
                                        {trailingAction.icon ? <Icon name={trailingAction.icon as IconName} size={16} /> : null}
                                        {trailingAction.label}
                                        {trailingAction.shortcut && trailingAction.shortcut !== "" ?
                                            <span
                                                className={ShortcutClass}
                                                style={{marginLeft: "12px", mixBlendMode: "normal"}}
                                            >
                                                {trailingAction.shortcut}
                                            </span> :
                                            null}
                                    </Button>
                                </span>
                            </TableCell>
                        </UiTableRow> :
                        null
                    }
                </tbody>
            </Table>
            </div>
            <UcxTableShortcutGuide
                actionDefs={actionDefs}
                groupAction={groupAction}
                tableRef={rootRef}
            />
        </div>
        {menuRowKey !== null && rowMenuEntries.length > 0 ?
            <ActionMenu
                key={menuRowKey}
                actions={rowMenuEntries}
                selected={(() => {
                    const row = store.rowsFor(tableId).get(menuRowKey);
                    return row ? [row] : [];
                })()}
                callbacks={undefined}
                trigger={null}
                openFnRef={menuOpenRef}
                closeFnRef={menuCloseRef}
                onOpen={() => {
                    menuOpenRefState.current = true;
                }}
                onClose={() => {
                    menuOpenRefState.current = false;
                    closeRowMenu();
                    rootRef.current?.focus({preventScroll: true});
                }}
            /> :
            null
        }
    </div>;
};

export const UcxTableFilter: React.FunctionComponent<{
    stateKey: string;
    store: UcxTableStore;
    placeholder?: string;
}> = props => {
    useUcxTableRevision(props.store);
    const filter = props.store.filterFor(props.stateKey);
    const inputRef = useRef<HTMLInputElement | null>(null);

    const onKeyDown = useCallback((event: React.KeyboardEvent<HTMLInputElement>) => {
        const view = props.store.viewFor(props.stateKey);
        if (event.key === "Escape") {
            event.preventDefault();
            inputRef.current?.blur();
            view?.focus();
        } else if (event.key === "Enter") {
            event.preventDefault();
            view?.activate();
        }
    }, [props.stateKey, props.store]);

    return <div className={UcxTableControlsClass}>
        <div className="table-filter">
            <Input
                inputRef={inputRef}
                data-ucx-table-filter={props.stateKey}
                placeholder={props.placeholder ?? "Filter..."}
                value={filter}
                onKeyDown={onKeyDown}
                onChange={ev => props.store.setFilter(props.stateKey, ev.target.value)}
                width="300px"
            />
            {filter === "" ?
                <span className="table-filter-shortcut"><div className={ShortcutClass}>/</div></span> :
                null
            }
        </div>
    </div>;
};

export const UcxTableCount: React.FunctionComponent<{
    stateKey: string;
    store: UcxTableStore;
    title?: string;
}> = props => {
    useUcxTableRevision(props.store);
    const stats = props.store.statsFor(props.stateKey);
    return <Text fontSize={12} color="textSecondary">
        {stats.loaded ? stats.filtered : "…"} {props.title ?? ""}
    </Text>;
};

const UcxTableShortcutGuideClass = injectStyle("ucx-table-shortcut-guide", key => `
    ${key} {
        color: var(--textSecondary);
        font-size: 12px;
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: flex-end;
        gap: 4px 16px;
        padding: 8px 12px;
        border-top: 1px solid var(--borderColor);
        background: var(--tableBackground, var(--gray-5));
        flex-shrink: 0;
        user-select: none;
    }

    ${key} .shortcut-guide-row {
        display: flex;
        align-items: center;
        gap: 8px;
        flex-shrink: 0;
    }

    ${key} .shortcut-guide-keys {
        display: flex;
        gap: 3px;
        flex-shrink: 0;
    }

    ${key} .shortcut-guide-action {
        white-space: nowrap;
    }

    @media (max-width: 1000px) {
        ${key} {
            display: none;
        }
    }
`);

function UcxTableShortcutGuideRow({keys, action}: {keys: string[]; action: string}): React.ReactNode {
    return <div className="shortcut-guide-row">
        <span className="shortcut-guide-keys">
            {keys.map(k => <span key={k} className={ShortcutClass}>{k}</span>)}
        </span>
        <span className="shortcut-guide-action">{action}</span>
    </div>;
}

function UcxTableShortcutGuide({actionDefs, groupAction, tableRef}: {
    actionDefs: UcxTableActionDef[];
    groupAction?: UcxTableActionDef;
    tableRef: React.RefObject<HTMLDivElement | null>;
}): React.ReactNode {
    const rows: {keys: string[]; action: string}[] = [
        {keys: ["↑", "↓", "j", "k"], action: "Move selection"},
        {keys: ["Enter"], action: "Open"},
    ];
    if (actionDefs.length > 0) rows.push({keys: ["x"], action: "Row actions"});
    const builtIn = new Set(["j", "k", "g", "G", "x", "/"]);
    for (const def of actionDefs) {
        if (def.shortcut && def.shortcut !== "" && !builtIn.has(def.shortcut.toLowerCase())) {
            rows.push({keys: [def.shortcut], action: def.label});
        }
    }
    if (groupAction?.shortcut && groupAction.shortcut !== "" && !builtIn.has(groupAction.shortcut.toLowerCase())) {
        rows.push({keys: [groupAction.shortcut], action: groupAction.label});
    }
    return <div
        className={UcxTableShortcutGuideClass}
        onMouseDown={() => tableRef.current?.focus({preventScroll: true})}
    >
        {rows.map((row, index) => <UcxTableShortcutGuideRow key={`${row.keys.join("")}-${index}`} keys={row.keys} action={row.action} />)}
    </div>;
}

const UcxBrowserLayoutClass = injectStyle("ucx-browser-layout", key => `
    ${key} {
        display: grid;
        grid-template-columns: 200px minmax(0, 1fr);
        grid-template-rows: minmax(0, 1fr);
        gap: 16px;
        width: 100%;
        flex: 1 1 0;
        min-height: 480px;
        align-items: stretch;
        outline: none;
    }

    ${key}[data-has-sidebar="false"] {
        grid-template-columns: minmax(0, 1fr);
    }

    @media (max-width: 1000px) {
        ${key} {
            grid-template-columns: minmax(0, 1fr);
            grid-template-rows: auto;
        }
    }
`);

const UcxBrowserSidebarClass = injectStyle("ucx-browser-sidebar", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        padding: 8px;
        min-height: 0;
        gap: 8px;
        overflow: hidden;
        outline: none;
    }

    ${key}[data-pane-active="true"] {
        border-color: var(--primaryMain);
    }
`);

const UcxBrowserMainClass = injectStyle("ucx-browser-main", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-width: 0;
        min-height: 0;
        overflow: hidden;
    }
`);

const UcxBrowserContentClass = injectStyle("ucx-browser-content", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        gap: 8px;
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
        overflow: hidden;
        outline: none;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
    }

    ${key}[data-pane-active="true"] {
        border-color: var(--primaryMain);
    }

    ${key} > * {
        flex: 1 1 auto;
        min-height: 0;
    }

    ${key}[data-ucx-pane] .streamed-table-frame {
        border: 0;
        border-radius: 0;
    }
`);

const UcxBrowserBottomClass = injectStyle("ucx-browser-bottom", key => `
    ${key} {
        display: flex;
        align-items: center;
        gap: 16px;
        border-top: 1px solid var(--borderColor);
        padding-top: 8px;
        padding-bottom: 8px;
        flex-shrink: 0;
        outline: none;
    }

    ${key} [data-rich-select-trigger] {
        height: 35px;
    }

    ${key} [data-rich-select-trigger] > div {
        display: flex;
        align-items: center;
        height: 100%;
    }
`);

const UcxNavTreeClass = injectStyle("ucx-nav-tree", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-height: 0;
        flex: 1;
    }

    ${key} .nav-tree-scroll {
        display: flex;
        flex-direction: column;
        gap: 2px;
        overflow-y: auto;
        min-height: 0;
        flex: 1;
    }

    ${key} .nav-tree-group {
        display: flex;
        align-items: center;
        gap: 4px;
        font-size: 12px;
        font-weight: 600;
        color: var(--textSecondary);
        cursor: pointer;
        user-select: none;
    }

    ${key} .nav-tree-group-label {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${key} .nav-tree-leaf {
        display: flex;
        align-items: center;
        gap: 6px;
        font-size: 13.5px;
        min-width: 0;
        color: var(--textPrimary, inherit);
    }

    ${key} [role="treeitem"]:not([aria-level="1"]) .nav-tree-leaf {
        padding-left: 4px;
    }

    ${key} .nav-tree-leaf-disabled {
        color: var(--textDisabled, var(--textSecondary, inherit));
        opacity: 0.55;
        cursor: default;
    }

    ${key} .nav-tree-row-separated {
        display: flex;
        flex: 1 1 auto;
        flex-direction: column;
        height: 100%;
        position: relative;
    }

    ${key} .nav-tree-separator {
        border-top: 1px solid var(--borderColor);
        left: -6px;
        position: absolute;
        right: 0;
        top: -6px;
    }

    ${key} .nav-tree-row-separated > .nav-tree-leaf,
    ${key} .nav-tree-row-separated > .nav-tree-group {
        flex: 1;
    }

    ${key} .virtualized-tree-row:has(> .nav-tree-row-separated) {
        height: 26px;
        margin-top: 12px;
        overflow: visible;
    }

    ${key} .nav-tree-label {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${key} .nav-tree-command {
        position: relative;
        display: flex;
        align-items: center;
        flex-shrink: 0;
    }

    ${key} .nav-tree-command input {
        padding-right: 44px;
    }

    ${key} .nav-tree-command-shortcut {
        position: absolute;
        right: 12px;
        display: flex;
        pointer-events: none;
    }
`);

const UcxStreamedTableClass = injectStyle("ucx-streamed-table", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
        outline: none;
    }

    ${k} .streamed-table-frame {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 200px;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        overflow: hidden;
    }

    ${k} .streamed-table-scroll {
        overflow: auto;
        flex: 1;
        min-height: 0;
    }

    ${k} .streamed-table-loading {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 8px;
        padding: 8px 0;
    }

    ${k}:focus-within .streamed-table-frame {
        border-color: var(--primaryMain);
    }

    ${k} .streamed-table-scroll table {
        margin-top: 0;
        margin-bottom: 0;
    }

    ${k} .streamed-table-scroll > div {
        margin-top: 0;
        margin-bottom: 0;
        border: 0;
        border-radius: 0;
    }

    ${k} .streamed-table-scroll table td {
        font-size: 13px;
    }

    ${k} .streamed-table-scroll td {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} .streamed-table-scroll thead th {
        position: sticky;
        top: 0;
        z-index: 2;
        background: var(--tableBackground);
        box-shadow: 0 1px 0 var(--borderColor);
    }

    ${k} tr.group-row {
        position: sticky;
        top: 34px;
        z-index: 1;
    }
    
    ${k} {
        --browserActiveRow: var(--blue-10);
        --browserHoverRow: var(--blue-5);
        --tableBackground: var(--gray-5);
    }
    
    html.dark ${k} {
        --browserActiveRow: var(--blue-90);
        --browserHoverRow: var(--blue-80);
        --tableBackground: #282c34;
    }

    ${k} tr[data-highlight="true"]:hover {
        background-color: var(--browserHoverRow);
        cursor: pointer;
    }

    ${k} tr[data-highlighted="true"],
    ${k} tr[data-selected="true"] {
        background-color: var(--browserHoverRow);
        cursor: pointer;
    }

    ${k}:focus-within tr[data-highlighted="true"],
    ${k}:focus-within tr[data-selected="true"] {
        background-color: var(--browserActiveRow);
    }

    ${k} tr.group-row > td {
        background: var(--tableBackground, var(--blue-10));
        font-weight: 600;
        font-size: 13px;
        box-shadow: 0 1px 0 var(--borderColor);
    }

    ${k} tr.group-row > td .group-row-inner {
        display: flex;
        align-items: center;
    }

    ${k} tr.group-row > td .group-row-label {
        flex-grow: 1;
    }

    ${k} tr.group-row > td .group-row-action {
        display: inline-flex;
    }

    ${k} tr.trailing-action-row > td {
        background: var(--tableBackground);
        text-align: center;
        box-shadow: 0 1px 0 var(--borderColor);
    }

    ${k} tr.trailing-action-row > td .trailing-action-cell {
        display: inline-flex;
        padding: 4px 0;
    }

    ${k} .streamed-table-frame .streamed-table-scroll tbody tr:last-child {
        border-bottom: 1px solid var(--borderColor);
    }

    @media (max-width: 1000px) {
        ${k} .streamed-table-scroll {
            flex: 0 1 auto;
        }
    }

    ${k} table {
        user-select: none;
    }

    ${k} .streamed-table-scroll table {
        width: var(--ucx-table-width, 100%);
        min-width: 100%;
    }

    ${k}[data-has-actions="true"] .streamed-table-scroll table {
        min-width: 0;
    }

    ${k} .streamed-table-header-label {
        display: inline-block;
        max-width: calc(100% - 16px);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        vertical-align: bottom;
        cursor: pointer;
        user-select: none;
    }

    ${k} .streamed-table-header-label:hover {
        color: var(--textPrimary);
    }

    ${k} .streamed-table-header-label[data-sort-active="true"] {
        font-weight: 500;
        color: var(--textPrimary);
    }

    ${k} .streamed-table-sort-arrow {
        margin-left: 4px;
        vertical-align: middle;
    }

    ${k} .streamed-table-resize-handle {
        position: absolute;
        top: 0;
        right: 0;
        width: 5px;
        height: 100%;
        cursor: col-resize;
        touch-action: none;
        z-index: 3;
    }

    ${k} .streamed-table-resize-handle::after {
        content: "";
        position: absolute;
        top: 0;
        left: -3px;
        width: 3px;
        height: 100%;
    }

    ${k} .streamed-table-resize-handle[data-dragging="true"] {
        background: var(--primaryMain);
    }

    ${k} .streamed-table-actions-col {
        width: ${UCX_ACTION_COLUMN_WIDTH}px;
    }

    ${k} th:has(> .streamed-table-actions-header) {
        width: ${UCX_ACTION_COLUMN_WIDTH}px;
        min-width: ${UCX_ACTION_COLUMN_WIDTH}px;
        padding: 0 !important;
    }

    ${k} td:has(> .streamed-table-actions-cell) {
        width: ${UCX_ACTION_COLUMN_WIDTH}px;
        min-width: ${UCX_ACTION_COLUMN_WIDTH}px;
        padding: 0 !important;
        vertical-align: middle;
    }

    ${k} .streamed-table-actions-cell {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    ${k} .streamed-table-actions-button {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        padding: 0;
        border: 0;
        border-radius: 6px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
    }

    ${k} .streamed-table-actions-button:hover {
        background: var(--gray-20);
        color: var(--textPrimary);
    }

    html.dark ${k} .streamed-table-actions-button:hover {
        background: var(--gray-100);
    }

    ${k} .streamed-table-copy-button {
        position: absolute;
        top: 50%;
        transform: translateY(-50%);
        right: 8px;
        width: 24px;
        height: 24px;
        display: none;
        align-items: center;
        justify-content: center;
        padding: 0;
        border: 0;
        border-radius: 6px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
    }

    ${k} tr:hover .streamed-table-copy-button,
    ${k} tr[data-selected="true"] .streamed-table-copy-button {
        display: inline-flex;
    }

    ${k} .streamed-table-copy-button:hover {
        background: var(--gray-20);
        color: var(--textPrimary);
    }

    html.dark ${k} .streamed-table-copy-button:hover {
        background: var(--gray-100);
    }
`);

const UcxTableControlsClass = injectStyle("ucx-table-controls", key => `
    ${key} {
        display: flex;
        align-items: center;
        min-width: 0;
    }

    ${key} .table-filter {
        position: relative;
        display: flex;
        align-items: center;
        min-width: 0;
    }

    ${key} .table-filter input {
        padding-right: 44px;
    }

    ${key} .table-filter-shortcut {
        position: absolute;
        right: 12px;
        display: flex;
        pointer-events: none;
    }
`);
