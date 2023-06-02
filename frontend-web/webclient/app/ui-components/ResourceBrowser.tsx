import {Operation} from "@/ui-components/Operation";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {SvgCache} from "@/Utilities/SvgCache";
import {capitalize, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import {fileName, resolvePath} from "@/Utilities/FileUtilities";
import {visualizeWhitespaces} from "@/Utilities/TextUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {PageV2} from "@/UCloud";
import {injectStyle as unstyledInjectStyle} from "@/Unstyled";
import {InputClass} from "./Input";
import {getStartOfDay} from "@/Utilities/DateUtilities";

/*
 BUGS FOUND
*/

/* MISSING FEATURES
    - Handling projects that cannot consume resources.
*/

const CLEAR_FILTER_VALUE = "\n\nCLEAR_FILTER\n\n";

export type Filter = FilterWithOptions | FilterCheckbox | FilterInput | MultiOptionFilter;

interface FilterInput {
    type: "input",
    key: string;
    text: string;
    icon: IconName;
}

interface FilterWithOptions {
    type: "options";
    key: string;
    text: string;
    clearable: boolean;
    options: FilterOption[];
    icon: IconName;
}

interface FilterCheckbox {
    type: "checkbox";
    key: string;
    text: string;
    icon: IconName;
}

interface FilterOption {
    text: string;
    icon: IconName;
    color: ThemeColor;
    value: string;
}

interface MultiOptionFilter {
    type: "multi-option";
    keys: [string, string];
    text: string;
    clearable: boolean;
    options: MultiOption[];
    icon: IconName;
}

interface MultiOption {
    text: string;
    icon: IconName;
    color: ThemeColor;
    values: [string, string];
}

const SORT_DIRECTIONS: FilterWithOptions = {
    type: "options",
    key: "sortDirection",
    text: "Sort order",
    icon: "sortAscending",
    clearable: false,
    options: [
        {color: "black", icon: "sortAscending", text: "Ascending", value: "ascending"},
        {color: "black", icon: "sortDescending", text: "Descending", value: "descending"}
    ]
};

export function dateRangeFilters(text: string): MultiOptionFilter {
    const todayMs = getStartOfDay(new Date(timestampUnixMs())).getTime();
    const yesterdayEnd = todayMs - 1;
    const yesterdayStart = getStartOfDay(new Date(todayMs - 1)).getTime();
    const pastWeekEnd = new Date(timestampUnixMs()).getTime();
    const pastWeekStart = getStartOfDay(new Date(pastWeekEnd - (7 * 1000 * 60 * 60 * 24))).getTime();
    const pastMonthEnd = new Date(timestampUnixMs()).getTime();
    const pastMonthStart = getStartOfDay(new Date(pastMonthEnd - (30 * 1000 * 60 * 60 * 24))).getTime();

    return {
        text,
        type: "multi-option",
        clearable: true,
        icon: "calendar",
        keys: ["filterCreatedAfter", "filterCreatedBefore"],
        options: [{
            text: "Today",
            color: "black",
            icon: "calendar",
            values: [todayMs.toString(), ""]
        }, {
            text: "Yesterday",
            color: "black",
            icon: "calendar",
            values: [yesterdayStart.toString(), yesterdayEnd.toString()]
        }, {
            text: "Past week",
            color: "black",
            icon: "calendar",
            values: [pastWeekStart.toString(), pastMonthEnd.toString()]
        }, {
            text: "Past month",
            color: "black",
            icon: "calendar",
            values: [pastMonthStart.toString(), pastMonthEnd.toString()]
        }]
    }
}

export type OperationOrGroup<T, R> = Operation<T, R> | OperationGroup<T, R>;

export interface OperationGroup<T, R> {
    icon: IconName;
    text: string;
    color: ThemeColor;
    operations: Operation<T, R>[];
    iconRotation?: number;
}

export enum SelectionMode {
    CLEAR,
    SINGLE,
    TOGGLE_SINGLE,
    ADDITIVE_SINGLE,
    ADDITIVE_LIST,
}

export enum EmptyReasonTag {
    LOADING,
    EMPTY,
    NOT_FOUND_OR_NO_PERMISSIONS,
    UNABLE_TO_FULFILL,
}

export interface EmptyReason {
    tag: EmptyReasonTag;
    information?: string;
}

export interface RenderDimensions {
    width: number;
    height: number;
    x: number;
    y: number;
}

interface ResourceBrowserListenerMap<T> {
    "mount": () => void;
    "unmount": () => void;

    "rowSelectionUpdated": () => void;

    "beforeShortcut": (ev: KeyboardEvent) => void;
    "unhandledShortcut": (ev: KeyboardEvent) => void;

    "startRenderPage": () => void;
    "renderRow": (entry: T, row: ResourceBrowserRow, dimensions: RenderDimensions) => void;
    "endRenderPage": () => void;

    // beforeOpen is called pre-navigation/calling "open". If it returns `true`, calling open is skipped.
    "beforeOpen": (oldPath: string, path: string, resource?: T) => boolean;
    "open": (oldPath: string, path: string, resource?: T) => void;
    "wantToFetchNextPage": (path: string) => Promise<void>;
    "search": (query: string) => void;

    "useFolder": () => void;
    // UNUSED?
    "useEntry": (entry: T) => void;

    "copy": (entries: T[], target: string) => void;
    "move": (entries: T[], target: string) => void;

    "starClicked": (entry: T) => void;

    "renderEmptyPage": (reason: EmptyReason) => void;

    "fetchOperations": () => OperationOrGroup<T, unknown>[];
    "fetchOperationsCallback": () => unknown | null;

    "validDropTarget": (entry: T) => boolean;
    "renderDropIndicator": (selectedEntries: T[], currentTarget: string | null) => void;

    "generateBreadcrumbs": (path: string) => {title: string, absolutePath: string}[];
    "locationBarVisibilityUpdated": (visible: boolean) => void;

    "generateTabCompletionEntries": (prompt: string, shouldFetch: boolean, lastSelectedIndex: number) => string[] | Promise<void>;
    "renderLocationBar": (prompt: string) => {rendered: string, normalized: string};

    "pathToEntry": (entry: T) => string;
    "nameOfEntry": (entry: T) => string;

    "sort": (page: T[]) => void;
    "fetchFilters": () => Filter[];
}

interface ResourceBrowserRow {
    container: HTMLElement;
    selected: HTMLInputElement;
    star: HTMLElement;
    title: HTMLElement;
    stat1: HTMLElement;
    stat2: HTMLElement;
    stat3: HTMLElement;
}

interface ResourceBrowseFeatures {
    dragToSelect?: boolean;
    supportsMove?: boolean;
    supportsCopy?: boolean;
    locationBar?: boolean;
    showUseButtonOnRows?: boolean;
    showUseButtonOnDirectory?: boolean;
    showProjectSelector?: boolean;
    showStar?: boolean;
    renderSpinnerWhenLoading?: boolean;
    breadcrumbsSeparatedBySlashes?: boolean;
    search?: boolean;
    filters?: boolean;
    sortDirection?: boolean;
}

export class ResourceBrowser<T> {
    // DOM component references
    private root: HTMLElement;
    private operations: HTMLElement;
    /* private */ filters: HTMLElement;
    sessionFilters: HTMLElement;
    private header: HTMLElement;
    private breadcrumbs: HTMLUListElement;
    private scrolling: HTMLDivElement;
    private entryDragIndicator: HTMLDivElement;
    /* private */ entryDragIndicatorContent: HTMLDivElement;
    private dragIndicator: HTMLDivElement;
    private rows: ResourceBrowserRow[] = [];

    // Selection state
    private isSelected = new Uint8Array(0);
    private lastSingleSelection = -1;
    private lastListSelectionEnd = -1;

    // Data fetching (mostly controlled by the caller)
    isFetchingNext: boolean = false;
    cachedData: Record<string, T[]> = {};
    cachedNext: Record<string, string | null> = {};
    emptyReasons: Record<string, EmptyReason> = {};
    private scrollPosition: Record<string, number> = {};
    currentPath: string;

    // Cached values and temporary state for DOM events
    scrollingContainerWidth: number;
    scrollingContainerHeight: number;
    scrollingContainerTop: number;
    scrollingContainerLeft: number;
    private processingShortcut: boolean = false;
    private ignoreScrollEvent = false;
    private ignoreRowClicksUntil: number = 0;

    // alt shortcuts
    private altKeys = ["KeyQ", "KeyW", "KeyE", "KeyR", "KeyT"];
    private altShortcuts: (() => void)[] = [doNothing, doNothing, doNothing, doNothing, doNothing];

    // Context menu
    private contextMenu: HTMLDivElement;
    private contextMenuHandlers: (() => void)[] = [];

    // Rename
    private renameField: HTMLInputElement;
    private renameFieldIndex: number = -1;
    renameValue: string = "";
    private renameOnSubmit: () => void = doNothing;
    private renameOnCancel: () => void = doNothing;

    // Location bar
    locationBar: HTMLInputElement;
    private locationBarTabIndex: number = -1;
    private locationBarTabCount: number = 0;

    // Drag-and-drop
    private entryBelowCursorTemporary: T | string | null = null;
    private entryBelowCursor: T | string | null = null;

    icons: SvgCache = new SvgCache();
    private didPerformInitialOpen = false;

    // Filters
    resourceName: string; // currently just used for 
    browseFilters: Record<string, string> = {};

    // Inline searching
    searchQuery: string = "";
    private searchQueryTimeout = -1;

    // Clipboard
    clipboard: T[] = [];
    clipboardIsCut: boolean = false;

    // Undo and redo
    undoStack: (() => void)[] = [];
    redoStack: (() => void)[] = [];

    // Empty pages
    defaultEmptyGraphic: DocumentFragment | null = null;
    spinner = new ReactStaticRenderer(() => <HexSpin size={60} />);
    emptyPageElement: {
        container: HTMLElement;
        graphic: HTMLElement;
        reason: HTMLElement;
        providerReason: HTMLElement;
    };

    // Feature set supported by this component.
    // Must be set before mount is invoked. If these are changed after mount() then the results are undefined.
    features: ResourceBrowseFeatures = {
        dragToSelect: true,
        supportsMove: false,
        supportsCopy: false,
        locationBar: false,
        showUseButtonOnRows: false,
        showUseButtonOnDirectory: false,
        showProjectSelector: false,
        showStar: false,
        search: true,
        renderSpinnerWhenLoading: true, // automatically inserts the spinner graphic before invoking "renderEmptyPage"
        filters: false,
        sortDirection: false,
    };

    private listeners: Record<string, any[]> = {};

    constructor(root: HTMLElement, resourceName: string) {
        this.root = root;
        this.resourceName = resourceName;
    }

    public init(
        ref: React.MutableRefObject<ResourceBrowser<T> | null>,
        features: ResourceBrowseFeatures,
        initialPath: string | undefined,
        onInit: (browser: ResourceBrowser<T>) => void,
    ) {
        ref.current = this;
        this.features = features;
        onInit(this);
        this.mount();
        if (initialPath !== undefined) this.open(initialPath);
    }

    mount() {
        // Mount primary UI and stylesheets
        ResourceBrowser.injectStyle();

        this.root.classList.add("file-browser");
        this.root.innerHTML = `
            <header>
                <div class="header-first-row">
                    <ul></ul>
                    <input class="location-bar">
                    <img class="location-bar-edit">
                    <input class="${InputClass} search-field" hidden>
                    <img class="search-icon">
                    <img class="refresh-icon">
                </div>
                <div class="operations"></div>
                <div style="display: flex;">
                    <div class="filters"></div>
                    <div class="session-filters"></div>
                </div>
            </header>
            
            <div style="overflow-y: auto; position: relative;">
                <div class="scrolling">
                    <input class="rename-field">
                </div>
            </div>
            
            <div class="file-drag-indicator-content"></div>
            <div class="file-drag-indicator"></div>
            <div class="drag-indicator"></div>
            <div class="context-menu"></div>
            
            <div class="page-empty">
                <div class="graphic"></div>
                <div class="reason"></div>
                <div class="provider-reason"></div>
            </div>
        `;

        this.operations = this.root.querySelector<HTMLElement>(".operations")!;
        this.dragIndicator = this.root.querySelector<HTMLDivElement>(".drag-indicator")!;
        this.entryDragIndicator = this.root.querySelector<HTMLDivElement>(".file-drag-indicator")!;
        this.entryDragIndicatorContent = this.root.querySelector<HTMLDivElement>(".file-drag-indicator-content")!;
        this.contextMenu = this.root.querySelector<HTMLDivElement>(".context-menu")!;
        this.scrolling = this.root.querySelector<HTMLDivElement>(".scrolling")!;
        this.renameField = this.root.querySelector<HTMLInputElement>(".rename-field")!;
        this.locationBar = this.root.querySelector<HTMLInputElement>(".location-bar")!;
        this.header = this.root.querySelector("header")!; // Add UtilityBar
        this.filters = this.root.querySelector<HTMLDivElement>(".filters")!;
        this.sessionFilters = this.root.querySelector<HTMLDivElement>(".session-filters")!;
        this.breadcrumbs = this.root.querySelector<HTMLUListElement>("header ul")!;
        this.emptyPageElement = {
            container: this.root.querySelector(".page-empty")!,
            graphic: this.root.querySelector(".page-empty .graphic")!,
            reason: this.root.querySelector(".page-empty .reason")!,
            providerReason: this.root.querySelector(".page-empty .provider-reason")!,
        };

        const unmountInterval = window.setInterval(() => {
            if (!this.root.isConnected) {
                this.dispatchMessage("unmount", fn => fn());
                window.clearInterval(unmountInterval);
            }
        }, 1000);

        this.breadcrumbs.setAttribute(
            "data-no-slashes",
            (this.features.breadcrumbsSeparatedBySlashes === false).toString()
        );

        if (this.features.locationBar) {
            // Render edit button for the location bar
            const editIcon = this.header.querySelector<HTMLImageElement>(".header-first-row .location-bar-edit")!;
            editIcon.src = placeholderImage;
            editIcon.width = 24;
            editIcon.height = 24;
            this.icons.renderIcon({name: "edit", color: "iconColor", color2: "iconColor2", width: 64, height: 64})
                .then(url => editIcon.src = url);
            editIcon.addEventListener("click", () => {
                this.toggleLocationBar();
            });
        }

        if (this.features.search) {
            const icon = this.header.querySelector<HTMLImageElement>(".header-first-row .search-icon")!;
            icon.src = placeholderImage;
            icon.width = 24;
            icon.height = 24;
            this.icons.renderIcon({name: "search", color: "blue", color2: "blue", width: 64, height: 64})
                .then(url => icon.src = url);

            const input = this.header.querySelector<HTMLInputElement>(".header-first-row .search-field")!;
            input.placeholder = "Search...";
            input.onkeydown = e => e.stopPropagation();
            input.onkeyup = e => {
                if (e.key === "Enter") {
                    this.searchQuery = input.value;
                    if (!this.searchQuery) {
                        return;
                    }
                    this.dispatchMessage("search", fn => fn(this.searchQuery));
                }
            };

            icon.onclick = () => {
                if (!input) return;
                input.toggleAttribute("hidden");
                if (!input.hasAttribute("hidden")) {
                    input.focus()
                }
            }
        }

        if (this.features.filters || this.features.sortDirection) {
            // Note(Jonas): Expand height of header if filters/sort-directions are available.
            this.header.setAttribute("data-has-filters", "");
        }

        if (this.features.filters) {
            this.renderSessionFilters();
        }

        {
            // Render refresh icon
            const icon = this.header.querySelector<HTMLImageElement>(".header-first-row .refresh-icon")!;
            icon.src = placeholderImage;
            icon.width = 24;
            icon.height = 24;
            this.icons.renderIcon({name: "refresh", color: "blue", color2: "blue", width: 64, height: 64})
                .then(url => icon.src = url);
            icon.addEventListener("click", () => {
                this.refresh();
            });
        }


        // Event handlers not related to rows
        this.renameField.addEventListener("keydown", ev => {
            this.onRenameFieldKeydown(ev);
        });

        this.renameField.addEventListener("beforeinput", ev => {
            this.onRenameFieldBeforeInput(ev);
        });

        this.renameField.addEventListener("input", ev => {
            this.onRenameFieldInput(ev);
        });

        this.scrolling.parentElement!.addEventListener("scroll", () => {
            this.onScroll();
        });

        this.scrolling.parentElement!.addEventListener("pointerdown", e => {
            this.onRowPointerDown(-1, e);
        });

        this.scrolling.parentElement!.addEventListener("contextmenu", e => {
            e.stopPropagation();
            e.preventDefault();
            this.onRowContextMenu(-1, e);
        });

        this.locationBar.addEventListener("keydown", ev => {
            this.onLocationBarKeyDown(ev);
        });

        this.locationBar.addEventListener("input", ev => {
            this.onLocationBarKeyDown("input");
        });

        const keyDownListener = (ev: KeyboardEvent) => {
            if (!this.root.isConnected) {
                document.removeEventListener("keydown", keyDownListener);
                return;
            }

            this.onKeyPress(ev);
        };
        document.addEventListener("keydown", keyDownListener);

        const clickHandler = ev => {
            if (!this.root.isConnected) {
                document.removeEventListener("click", clickHandler);
                return;
            }

            if (this.contextMenuHandlers.length) {
                this.closeContextMenu();
            }

            if (this.renameFieldIndex !== -1 && ev.target !== this.renameField) {
                this.closeRenameField("submit");
            }
        };
        document.addEventListener("click", clickHandler);

        const sizeListener = () => {
            if (!this.root.isConnected) {
                window.removeEventListener("resize", sizeListener);
                return;
            }

            const parent = this.scrolling.parentElement!;
            const rect = parent.getBoundingClientRect();
            this.scrollingContainerWidth = rect.width;
            this.scrollingContainerHeight = rect.height;
            this.scrollingContainerTop = rect.top;
            this.scrollingContainerLeft = rect.left;

            if (this.didPerformInitialOpen) {
                this.renderBreadcrumbs();
                this.renderOperations();
                this.renderRows();
            }
        };
        window.addEventListener("resize", sizeListener);
        sizeListener();

        // Mount rows and attach event handlers
        const rows: HTMLDivElement[] = [];
        for (let i = 0; i < ResourceBrowser.maxRows; i++) {
            const row = div(`
                <input type="checkbox">
                <div class="favorite"></div>
                <div class="title"></div>
                <div class="stat1"></div>
                <div class="stat2"></div>
                <div class="stat3"></div>
            `);
            row.classList.add("row");
            const myIndex = i;
            row.addEventListener("pointerdown", e => {
                e.stopPropagation();
                this.onRowPointerDown(myIndex, e);
            });
            row.addEventListener("click", e => {
                this.onRowClicked(myIndex, e);
            });
            row.addEventListener("dblclick", () => {
                this.onRowDoubleClicked(myIndex);
            });
            row.addEventListener("mousemove", e => {
                this.onRowMouseMove(myIndex);
            });
            row.addEventListener("contextmenu", e => {
                e.stopPropagation();
                e.preventDefault();

                this.onRowContextMenu(myIndex, e);
            });
            rows.push(row);

            const r = {
                container: row,
                selected: row.querySelector<HTMLInputElement>("input")!,
                star: row.querySelector<HTMLElement>(".favorite")!,
                title: row.querySelector<HTMLElement>(".title")!,
                stat1: row.querySelector<HTMLElement>(".stat1")!,
                stat2: row.querySelector<HTMLElement>(".stat2")!,
                stat3: row.querySelector<HTMLElement>(".stat3")!,
            };

            r.selected.addEventListener("pointerdown", e => {
                e.preventDefault();
                e.stopPropagation();
            });

            r.selected.addEventListener("click", e => {
                e.stopPropagation();
                this.select(myIndex, SelectionMode.TOGGLE_SINGLE);
            });

            r.star.addEventListener("pointerdown", e => {
                e.preventDefault();
                e.stopPropagation();
            });

            r.star.addEventListener("click", e => {
                e.preventDefault();
                e.stopPropagation();
                this.onStarClicked(myIndex);
            });

            this.rows.push(r);
        }

        this.scrolling.append(...rows);
    }

    refresh() {
        this.open(this.currentPath, true);
    }

    open(path: string, force: boolean = false, resource?: T) {
        // Note(Jonas): This happens before the child component gets a chance to access the resource and execute logic.
        // So how do we for instance ensure that the popIn can happen? Call a dispatchable function, provided that
        // the child component has provided one?

        this.didPerformInitialOpen = true;
        if (this.currentPath === path && !force) return;
        const oldPath = this.currentPath;

        // Set new state and notify event handlers
        path = resolvePath(path);
        this.currentPath = path;
        this.isFetchingNext = false;

        // Reset state
        this.isSelected = new Uint8Array(0);
        this.lastSingleSelection = -1;
        this.lastListSelectionEnd = -1;
        window.clearTimeout(this.searchQueryTimeout);
        this.searchQueryTimeout = -1;
        this.searchQuery = "";
        this.renameFieldIndex = -1; // NOTE(Dan): In this case we are _not_ running the onCancel function.
        this.renameValue = "";
        this.locationBarTabIndex = -1;
        this.locationBar.value = path + "/";
        this.locationBar.dispatchEvent(new Event("input"));
        this.entryBelowCursor = null;

        // NOTE(Dan): Need to get this now before we call renderPage(), since it will reset the scroll position.
        const scrollPositionElement = this.scrollPosition[path];

        // Perform renders
        this.renderBreadcrumbs();
        this.renderOperations();
        this.renderRows();
        this.clearFilters();
        if (this.features.sortDirection) this.renderSortOrder();
        if (this.features.filters) this.renderFilters();

        // NOTE(Dan): We need to scroll to the position _after_ we have rendered the page.
        this.scrolling.parentElement!.scrollTo({top: scrollPositionElement ?? 0});

        this.dispatchMessage("open", fn => fn(oldPath, path, resource));
    }

    renderRows() {
        const page = this.cachedData[this.currentPath] ?? [];
        if (this.isSelected.length < page.length) {
            const newSelected = new Uint8Array(page.length);
            newSelected.set(this.isSelected, 0);
            this.isSelected = newSelected;
        } else if (this.isSelected.length > page.length) {
            this.isSelected = new Uint8Array(page.length);
        }

        const containerTop = this.scrollingContainerTop;
        const containerLeft = this.scrollingContainerLeft;
        const containerHeight = this.scrollingContainerHeight;
        const containerWidth = this.scrollingContainerWidth;
        const approximateSizeForTitle = containerWidth * (ResourceBrowser.rowTitleSizePercentage / 100);

        // Determine the total size of the page and figure out where we are
        const totalSize = ResourceBrowser.rowSize * page.length;
        const firstVisiblePixel = this.scrolling.parentElement!.scrollTop;
        this.scrollPosition[this.currentPath] = firstVisiblePixel;

        const firstRowInsideRegion = Math.ceil(firstVisiblePixel / ResourceBrowser.rowSize);
        const firstRowToRender = Math.max(0, firstRowInsideRegion - ResourceBrowser.extraRowsToPreRender);

        this.scrolling.style.height = `${totalSize}px`;

        const findRow = (idx: number): ResourceBrowserRow | null => {
            const rowNumber = idx - firstRowToRender;
            if (rowNumber < 0) return null;
            if (rowNumber >= ResourceBrowser.maxRows) return null;
            return this.rows[rowNumber];
        }

        // Check if any item has been selected
        let hasAnySelected = false;
        const selection = this.isSelected;
        for (let i = 0; i < selection.length; i++) {
            if (selection[i] !== 0) {
                hasAnySelected = true;
                break;
            }
        }

        // Reset rows and place them accordingly
        for (let i = 0; i < ResourceBrowser.maxRows; i++) {
            const row = this.rows[i];
            row.container.classList.add("hidden");
            row.container.removeAttribute("data-selected");
            row.container.removeAttribute("data-idx");
            row.container.style.position = "absolute";
            row.selected.style.display = hasAnySelected || !this.features.showStar ? "block" : "none";
            row.star.style.display = hasAnySelected || !this.features.showStar ? "none" : "block";
            const top = Math.min(totalSize - ResourceBrowser.rowSize, (firstRowToRender + i) * ResourceBrowser.rowSize);
            row.container.style.top = `${top}px`;
            row.title.innerHTML = "";
            row.stat1.innerHTML = "";
            row.stat2.innerHTML = "";
            row.stat3.innerHTML = "";
        }

        this.renameField.style.display = "none";

        // Render the visible rows by iterating over all items
        this.dispatchMessage("startRenderPage", fn => fn());
        for (let i = 0; i < page.length; i++) {
            const entry = page[i];
            const row = findRow(i);
            if (!row) continue;

            const relativeX = 60;
            const relativeY = parseInt(row.container.style.top.replace("px", ""));

            if (i === this.renameFieldIndex) {
                this.renameField.style.display = "block";
                this.renameField.style.top = `${relativeY + ((ResourceBrowser.rowSize - 30) / 2)}px`;
                this.renameField.style.width = approximateSizeForTitle + "px";
                this.renameField.value = this.renameValue;
                this.renameField.focus();
            }

            row.container.setAttribute("data-idx", i.toString());
            row.container.setAttribute("data-selected", (this.isSelected[i] !== 0).toString());
            row.selected.checked = (this.isSelected[i] !== 0);
            row.container.classList.remove("hidden");

            const x = this.scrollingContainerLeft + relativeX;
            const y = this.scrollingContainerTop + relativeY - firstVisiblePixel;
            this.dispatchMessage("renderRow", fn => fn(
                entry,
                row,
                {
                    width: containerWidth,
                    height: ResourceBrowser.rowSize,
                    x, y
                }
            ));
        }
        this.dispatchMessage("endRenderPage", fn => fn());

        if (page.length === 0) {
            const initialPage = this.currentPath;
            const initialReason = this.emptyReasons[this.currentPath] ?? {tag: EmptyReasonTag.LOADING};

            const renderEmptyPage = async () => {
                if (this.currentPath !== initialPage) return;
                const page = this.cachedData[this.currentPath] ?? [];
                if (page.length !== 0) return;

                const reason = this.emptyReasons[this.currentPath] ?? {tag: EmptyReasonTag.LOADING};
                const e = this.emptyPageElement;
                e.container.style.display = "flex";
                e.graphic.innerHTML = "";
                e.reason.innerHTML = "";
                e.providerReason.innerHTML = "";
                const containerSize = 400;
                e.container.style.width = containerSize + "px";
                e.container.style.height = containerSize + "px";
                e.container.style.left = (containerLeft + (containerWidth / 2) - (containerSize / 2)) + "px";
                e.container.style.top = (containerTop + (containerHeight / 2) - (containerSize / 2)) + "px";

                if (reason.tag === EmptyReasonTag.LOADING) {
                    if (this.features.renderSpinnerWhenLoading) {
                        e.graphic.append(this.spinner.clone());
                    }
                } else {
                    if (this.defaultEmptyGraphic) {
                        e.graphic.append(this.defaultEmptyGraphic.cloneNode(true));
                    }
                }

                this.dispatchMessage("renderEmptyPage", fn => fn(reason));
            };

            if (initialReason.tag === EmptyReasonTag.LOADING) {
                // Delay rendering of the loading page a bit until we are sure the loading operation is actually "slow"
                window.setTimeout(() => renderEmptyPage(), 300);
            } else {
                renderEmptyPage();
            }
        } else {
            this.emptyPageElement.container.style.display = "none";
        }
    }

    defaultTitleRenderer(title: string, dimensions: RenderDimensions): string {
        const approximateSizeForTitle = dimensions.width * (ResourceBrowser.rowTitleSizePercentage / 100);
        const approximateSizePerCharacter = 7.1;
        if (title.length * approximateSizePerCharacter > approximateSizeForTitle) {
            title = title.substring(0, (approximateSizeForTitle / approximateSizePerCharacter) - 3) + "...";
        }

        return visualizeWhitespaces(title);
    }

    defaultIconRenderer(): [HTMLDivElement, (url: string) => void] {
        const icon = document.createElement("div");
        // NOTE(Dan): We have to use a div with a background image, otherwise users will be able to drag the
        // image itself, which breaks the drag-and-drop functionality.
        icon.style.width = "20px";
        icon.style.height = "20px";
        icon.style.backgroundSize = "contain";
        icon.style.marginRight = "8px";
        icon.style.display = "inline-block";
        icon.style.backgroundPosition = "center";
        return [icon, (url) => icon.style.backgroundImage = `url(${url})`];
    }

    defaultBreadcrumbs(): {title: string; absolutePath: string;}[] {
        return [{title: capitalize(this.resourceName), absolutePath: ""}];
    }

    resetTitleComponent(element: HTMLElement) {
        element.style.display = "none";
    }

    placeTitleComponent(element: HTMLElement, dimensions: RenderDimensions, estimatedHeight?: number) {
        const height = estimatedHeight ?? 40;
        element.style.left = dimensions.x + "px";
        const absY = dimensions.y + (ResourceBrowser.rowSize - height) / 2;
        element.style.top = absY + "px";
        element.style.width = (dimensions.width * (ResourceBrowser.rowTitleSizePercentage / 100)) + "px";

        if (absY >= this.scrollingContainerTop &&
            absY <= this.scrollingContainerTop + this.scrollingContainerHeight) {
            element.style.display = "block";
        }
    }

    renderBreadcrumbs() {
        const crumbs = this.dispatchMessage("generateBreadcrumbs", fn => fn(this.currentPath));
        // NOTE(Dan): The next section computes which crumbs should be shown and what the content of them should be.
        // We start out by truncating all components down to a maximum length. This truncation takes place regardless
        // of how much screen estate we have. Following this, we determine a target size of our breadcrumbs based on
        // the size of our current container. Along with an estimated size of the font, we determine how many characters
        // in total we should target. In the end we will end up in one of the following states:
        //
        // 1. We will show all breadcrumbs
        // 2. We show only the first, immediate parent and last dir
        // 3. We only show the first and last dir
        //
        // We select this by choosing the first from the list which can stay within the target.
        const containerWidth = this.breadcrumbs.parentElement!.getBoundingClientRect().width;
        const approximateLengthPerCharacter = 22;
        const maxComponentLength = 30;
        const targetPathLength = (containerWidth / approximateLengthPerCharacter) - 5;

        let combinedLengthAfterTruncation = 0;
        let combinedLengthWithParent = 0;
        const truncatedComponents: string[] = [];
        for (let i = 0; i < crumbs.length; i++) {
            const component = crumbs[i];
            if (component.title.length > maxComponentLength) {
                truncatedComponents.push(component.title.substring(0, maxComponentLength - 3) + "...");
                combinedLengthAfterTruncation += maxComponentLength;
                if (i >= crumbs.length - 2) combinedLengthWithParent += maxComponentLength;
            } else {
                truncatedComponents.push(component.title);
                combinedLengthAfterTruncation += component.title.length;
                if (i >= crumbs.length - 2) combinedLengthWithParent += component.title.length;
            }
        }

        const canKeepMiddle = combinedLengthAfterTruncation <= targetPathLength;
        const canKeepParent = combinedLengthWithParent <= targetPathLength;

        this.breadcrumbs.innerHTML = "";
        const fragment = document.createDocumentFragment();
        let idx = 0;
        for (const component of crumbs) {
            const myPath = crumbs[idx].absolutePath;

            const canKeepThis =
                canKeepMiddle ||
                idx === 0 ||
                idx === crumbs.length - 1 ||
                (idx === crumbs.length - 2 && canKeepParent);

            if (idx === 1 && !canKeepMiddle && idx !== crumbs.length - 2) {
                const listItem = document.createElement("li");
                listItem.innerText = "...";
                fragment.append(listItem);
            }

            if (canKeepThis) {
                const listItem = document.createElement("li");
                listItem.innerText = visualizeWhitespaces(truncatedComponents[idx]);
                listItem.addEventListener("click", () => {
                    this.open(myPath);
                });
                listItem.addEventListener("mousemove", () => {
                    this.entryBelowCursorTemporary = myPath;
                });

                fragment.append(listItem);
            }
            idx++;
        }

        this.breadcrumbs.append(fragment);
    }

    renderSortOrder() {
        this.addOptionsToFilter(SORT_DIRECTIONS);
    }

    renderFilters() {
        const filters = this.dispatchMessage("fetchFilters", k => k());
        for (const f of filters) {
            switch (f.type) {
                case "checkbox": {
                    this.addCheckboxToFilter(f);
                    break;
                }
                case "options":
                case "multi-option": {
                    this.addOptionsToFilter(f);
                    break;
                }
            }
        }
    }

    // This might be a stupid solution, but some filters should not exist beyond the lifetime of the component,
    // referred to `session` here.
    renderSessionFilters() {
        const filters = this.dispatchMessage("fetchFilters", k => k());

        for (const f of filters) {
            if (f.type === "input") this.addInputToFilter(f);
        }
    }

    renderOperations() {
        this.renderOperationsIn(false);
    }

    public renderFiltersInContextMenu(filter: FilterWithOptions | MultiOptionFilter, x: number, y: number) {
        const renderOpIconAndText = (
            op: {text: string; icon: IconName; color: ThemeColor},
            element: HTMLElement,
            shortcut?: string,
        ) => {
            {
                // Set the icon
                const icon = image(placeholderImage, {height: 16, width: 16, alt: "Icon"});
                element.append(icon);
                this.icons.renderIcon({
                    name: op.icon as IconName,
                    color: op.color as ThemeColor,
                    color2: "iconColor2",
                    width: 64,
                    height: 64,
                }).then(url => icon.src = url);
            }

            {
                // ...and the text
                let operationText = op.text;
                if (operationText) element.append(operationText);
                if (operationText && shortcut) {
                    const shortcutElem = document.createElement("kbd");
                    shortcutElem.append(shortcut);
                    element.append(shortcutElem);
                }
            }
        }

        const renderFilterInContextMenu = (
            options: FilterOption[] | MultiOption[],
            posX: number,
            posY: number
        ) => {
            var counter = 1;
            const menu = this.contextMenu;
            let actualPosX = posX;
            let actualPosY = posY;
            menu.innerHTML = "";

            const itemSize = 40;
            let opCount = options.length;
            const listHeight = opCount * itemSize;
            const listWidth = 400;

            const windowWidth = window.innerWidth;
            const windowHeight = window.innerHeight;

            if (posX + listWidth >= windowWidth - 32) {
                actualPosX -= listWidth;
            }

            if (posY + listHeight >= windowHeight - 32) {
                actualPosY -= listHeight;
            }

            menu.style.transform = `translate(0, -${listHeight / 2}px) scale3d(1, 0.1, 1)`;
            window.setTimeout(() => menu.style.transform = "scale3d(1, 1, 1)", 0);
            menu.style.display = "block";
            menu.style.opacity = "1";

            menu.style.top = actualPosY + "px";
            menu.style.left = actualPosX + "px";

            const menuList = document.createElement("ul");
            menu.append(menuList);
            let shortcutNumber = counter;
            for (const child of options) {
                const item = document.createElement("li");
                renderOpIconAndText(child, item, shortcutNumber <= 9 ? `[${shortcutNumber}]` : undefined);

                const myIndex = shortcutNumber - 1;
                this.contextMenuHandlers.push(() => {
                    if (filter.type === "options") {
                        let c = child as FilterOption;
                        this.browseFilters[filter.key] = c.value;
                        if (c.value === CLEAR_FILTER_VALUE) {
                            clearFilterStorageValue(this.resourceName, filter.key);
                        } else {
                            setFilterStorageValue(this.resourceName, filter.key, c.value);
                        }
                    } else if (filter.type === "multi-option") {
                        let c = child as MultiOption;
                        const [keyOne, keyTwo] = filter.keys;
                        const [valueOne, valueTwo] = c.values;
                        this.browseFilters[keyOne] = valueOne;
                        if (valueTwo) this.browseFilters[keyTwo] = valueTwo;
                        else delete this.browseFilters[keyTwo];
                        if (valueOne === CLEAR_FILTER_VALUE) {
                            clearFilterStorageValue(this.resourceName, keyOne);
                            clearFilterStorageValue(this.resourceName, keyTwo);
                        } else {
                            setFilterStorageValue(this.resourceName, keyOne, valueOne);
                            if (valueTwo) setFilterStorageValue(this.resourceName, keyTwo, valueTwo);
                            else clearFilterStorageValue(this.resourceName, keyTwo);
                        }
                    }
                    this.open(this.currentPath, true);
                });
                item.addEventListener("mouseover", () => {
                    this.findActiveContextMenuItem(true);
                    this.selectContextMenuItem(myIndex);
                });
                item.addEventListener("click", ev => {
                    ev.stopPropagation();
                    this.findActiveContextMenuItem(true);
                    this.selectContextMenuItem(myIndex);
                    this.onContextMenuItemSelection();
                });

                menuList.append(item);
                shortcutNumber++;
            }
        };

        if (filter.type === "options") {
            let filters = filter.options.slice();
            if (filter.clearable) {
                filters.unshift({
                    text: "Clear filter",
                    color: "red",
                    icon: "close",
                    value: CLEAR_FILTER_VALUE
                });
            }
            renderFilterInContextMenu(filters, x, y);
        } else if (filter.type === "multi-option") {
            let filters = filter.options.slice();
            filters.unshift({
                text: "Clear filter",
                color: "red",
                icon: "close",
                values: [CLEAR_FILTER_VALUE, ""]
            });
            renderFilterInContextMenu(filters, x, y);
        }
    }

    private renderOperationsIn(useContextMenu: boolean, contextOpts?: {
        x: number,
        y: number,
    }) {
        const callbacks = this.dispatchMessage("fetchOperationsCallback", fn => fn());
        if (callbacks === null) return;

        const operations = this.dispatchMessage("fetchOperations", fn => fn());

        if (!useContextMenu) {
            for (let i = 0; i < this.altShortcuts.length; i++) {
                this.altShortcuts[i] = doNothing;
            }
        }

        const selected = this.findSelectedEntries();
        const page = this.cachedData[this.currentPath] ?? [];

        const renderOpIconAndText = (
            op: OperationOrGroup<unknown, unknown>,
            element: HTMLElement,
            shortcut?: string,
        ) => {
            {
                // Set the icon
                const icon = image(placeholderImage, {height: 16, width: 16, alt: "Icon"});
                element.append(icon);
                this.icons.renderIcon({
                    name: op.icon as IconName,
                    color: op.color as ThemeColor,
                    color2: "iconColor2",
                    width: 64,
                    height: 64,
                }).then(url => icon.src = url);
                if (op.iconRotation) {
                    icon.style.transform = `rotate(${op.iconRotation}deg)`;
                }
            }

            {
                // ...and the text
                let operationText = "";
                if (typeof op.text === "string") {
                    operationText = op.text;
                } else {
                    operationText = op.text(selected, callbacks);
                }
                if (operationText) element.append(operationText);
                if (operationText && shortcut) {
                    const shortcutElem = document.createElement("kbd");
                    shortcutElem.append(shortcut);
                    element.append(shortcutElem);
                }
            }
        }

        const renderOperationsInContextMenu = (
            operations: OperationOrGroup<T, unknown>[],
            posX: number,
            posY: number,
            counter: number = 1,
            allowCreation: boolean = true
        ): number => {
            const menu = this.contextMenu;
            if (allowCreation) {
                let actualPosX = posX;
                let actualPosY = posY;
                menu.innerHTML = "";

                const itemSize = 40;
                let opCount = 0;
                for (const op of operations) {
                    if ("operations" in op) {
                        opCount += op.operations.length;
                    } else {
                        opCount++;
                    }
                }

                const listHeight = opCount * itemSize;
                const listWidth = 400;

                const windowWidth = window.innerWidth;
                const windowHeight = window.innerHeight;

                if (posX + listWidth >= windowWidth - 32) {
                    actualPosX -= listWidth;
                }

                if (posY + listHeight >= windowHeight - 32) {
                    actualPosY -= listHeight;
                }

                menu.style.transform = `translate(0, -${listHeight / 2}px) scale3d(1, 0.1, 1)`;
                window.setTimeout(() => menu.style.transform = "scale3d(1, 1, 1)", 0);
                menu.style.display = "block";
                menu.style.opacity = "1";

                menu.style.top = actualPosY + "px";
                menu.style.left = actualPosX + "px";
            }

            const menuList = allowCreation ? document.createElement("ul") : menu.querySelector("ul")!;
            if (allowCreation) menu.append(menuList);
            let shortcutNumber = counter;
            for (const child of operations) {
                if ("operations" in child) {
                    counter = renderOperationsInContextMenu(child.operations, posX, posY, shortcutNumber, false);
                    continue;
                }

                const item = document.createElement("li");
                renderOpIconAndText(child, item, shortcutNumber <= 9 ? `[${shortcutNumber}]` : undefined);

                const myIndex = shortcutNumber - 1;
                this.contextMenuHandlers.push(() => {
                    child.onClick(selected, callbacks, page);
                });
                item.addEventListener("mouseover", () => {
                    this.findActiveContextMenuItem(true);
                    this.selectContextMenuItem(myIndex);
                });
                item.addEventListener("click", ev => {
                    ev.stopPropagation();
                    this.findActiveContextMenuItem(true);
                    this.selectContextMenuItem(myIndex);
                    this.onContextMenuItemSelection();
                });

                menuList.append(item);
                shortcutNumber++;
            }
            return counter;
        };

        let opCount = 0;
        const renderOperation = (
            op: OperationOrGroup<T, unknown>
        ): HTMLElement => {
            const element = document.createElement("div");
            element.classList.add("operation");
            element.classList.add(!useContextMenu ? "in-header" : "in-context-menu");

            const altKey = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌥" : "Alt + ";
            renderOpIconAndText(op, element, `[${altKey}${this.altKeys[opCount].replace("Key", "")}]`);

            {
                // ...and the handlers
                const handler = (ev?: Event) => {
                    ev?.stopPropagation();
                    if ("operations" in op) {
                        const elementBounding = element.getBoundingClientRect();
                        renderOperationsInContextMenu(
                            op.operations,
                            elementBounding.left,
                            (elementBounding.top + elementBounding.height),
                        );
                    } else if ("onClick" in op) {
                        op.onClick(selected, callbacks, page);
                    }
                };

                element.addEventListener("click", handler);
                if (!useContextMenu) {
                    this.altShortcuts[opCount] = handler;
                    opCount++;
                }
            }

            return element;
        }

        if (!useContextMenu) {
            const target = this.operations;
            target.innerHTML = "";
            for (const op of operations) {
                target.append(renderOperation(op));
            }
        } else {
            const posX = contextOpts?.x ?? 0;
            const posY = contextOpts?.y ?? 0;
            renderOperationsInContextMenu(operations, posX, posY);
        }
    }

    clearFilters() {
        const filtersToKeep = this.dispatchMessage("fetchFilters", fn => fn())
            .filter(it => it.type === "input").map((it: FilterInput) => it.key);
        for (const key of Object.keys(this.browseFilters)) {
            if (!filtersToKeep.includes(key)) delete this.browseFilters[key];
        }
        this.filters.replaceChildren();
    }

    select(virtualRowIndex: number, selectionMode: SelectionMode, render: boolean = true) {
        const selection = this.isSelected;
        if (virtualRowIndex < 0 || virtualRowIndex >= selection.length) return;

        switch (selectionMode) {
            case SelectionMode.CLEAR: {
                for (let i = 0; i < selection.length; i++) {
                    selection[i] = 0;
                }
                break;
            }

            case SelectionMode.SINGLE: {
                for (let i = 0; i < selection.length; i++) {
                    if (i === virtualRowIndex) selection[i] = 1;
                    else selection[i] = 0;
                }
                this.lastSingleSelection = virtualRowIndex;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.TOGGLE_SINGLE: {
                selection[virtualRowIndex] = selection[virtualRowIndex] !== 0 ? 0 : 1;
                this.lastSingleSelection = virtualRowIndex;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.ADDITIVE_SINGLE: {
                selection[virtualRowIndex] = 1;
                this.lastSingleSelection = virtualRowIndex;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.ADDITIVE_LIST: {
                if (this.lastListSelectionEnd !== -1) {
                    // Clear old list
                    const start = Math.min(this.lastSingleSelection, this.lastListSelectionEnd);
                    const end = Math.max(this.lastSingleSelection, this.lastListSelectionEnd);
                    for (let i = start; i <= end; i++) {
                        selection[i] = 0;
                    }
                }

                const firstEntry = Math.min(virtualRowIndex, this.lastSingleSelection);
                const lastEntry = Math.max(virtualRowIndex, this.lastSingleSelection);
                for (let i = firstEntry; i <= lastEntry; i++) {
                    selection[i] = 1;
                }
                this.lastListSelectionEnd = virtualRowIndex;
                break;
            }
        }

        if (render) {
            this.renderRows();
            this.renderOperations();
        }

        this.dispatchMessage("rowSelectionUpdated", fn => fn());
    }

    findSelectedEntries(): T[] {
        const selectedEntries: T[] = [];
        const page = this.cachedData[this.currentPath];
        for (let i = 0; i < this.isSelected.length && i < page.length; i++) {
            if (this.isSelected[i] !== 0) selectedEntries.push(page[i]);
        }
        return selectedEntries;
    }

    findVirtualRowIndex(predicate: (arg: T) => boolean): number | null {
        let idx = 0;
        for (const entry of this.cachedData[this.currentPath] ?? []) {
            if (predicate(entry)) {
                return idx;
            }

            idx++;
        }
        return null;
    }

    ensureRowIsVisible(rowIdx: number, topAligned: boolean, ignoreEvent: boolean = false) {
        const scrollingContainer = this.scrolling.parentElement!;
        const height = this.scrollingContainerHeight;

        const firstRowPixel = rowIdx * ResourceBrowser.rowSize;
        const lastRowPixel = firstRowPixel + ResourceBrowser.rowSize;

        const firstVisiblePixel = scrollingContainer.scrollTop;
        const lastVisiblePixel = firstVisiblePixel + height;

        if (firstRowPixel < firstVisiblePixel || firstRowPixel > lastVisiblePixel ||
            lastRowPixel < firstVisiblePixel || lastRowPixel > lastVisiblePixel) {
            if (ignoreEvent) this.ignoreScrollEvent = true;

            if (topAligned) {
                scrollingContainer.scrollTo({top: firstRowPixel});
            } else {
                scrollingContainer.scrollTo({top: lastRowPixel - height});
            }

            if (ignoreEvent) this.fetchNext();
        }
    }

    // Location bar utilities
    setLocationBarVisibility(visible: boolean) {
        if (visible) {
            this.header.classList.add("show-location-bar");
            this.locationBar.focus();
            this.locationBar.setSelectionRange(0, this.locationBar.value.length);
        } else {
            this.header.classList.remove("show-location-bar");
        }

        this.dispatchMessage("locationBarVisibilityUpdated", fn => fn(visible));
    }

    isLocationBarVisible(): boolean {
        return this.header.classList.contains("show-location-bar");
    }

    toggleLocationBar() {
        this.setLocationBarVisibility(!this.isLocationBarVisible());
    }

    registerPage(page: PageV2<T>, path: string, shouldClear: boolean) {
        const initialData: T[] = shouldClear ? [] : (this.cachedData[path] ?? []);
        this.cachedData[path] = initialData.concat(page.items);
        this.cachedNext[path] = page.next ?? null;
        if (this.cachedData[path].length === 0) {
            this.emptyReasons[path] = {tag: EmptyReasonTag.EMPTY};
        }

        if (page.next && this.cachedData[path].length < 10000) {
            this.dispatchMessage("wantToFetchNextPage", fn => fn(this.currentPath));
        }
    }

    // Page modification (outside normal loads)
    insertEntryIntoCurrentPage(item: T) {
        const page = this.cachedData[this.currentPath] ?? [];
        page.push(item);
        this.dispatchMessage("sort", fn => fn(page));
    }

    removeEntryFromCurrentPage(predicate: (T) => boolean) {
        const page = this.cachedData[this.currentPath] ?? [];
        const idx = page.findIndex(predicate);
        if (idx !== -1) page.splice(idx, 1);
        this.dispatchMessage("sort", fn => fn(page));

        if (page.length === 0) {
            this.emptyReasons[this.currentPath] = {tag: EmptyReasonTag.EMPTY};
        }

        this.isSelected = new Uint8Array(0);
        this.renderOperations();
    }

    // Renaming and creation input fields
    showRenameField(
        predicate: (arg: T) => boolean,
        onSubmit: () => void,
        onCancel: () => void,
        initialValue: string,
    ) {
        const idx = this.findVirtualRowIndex(predicate);
        if (idx == null) return;

        this.closeRenameField("cancel", false);
        this.renameFieldIndex = idx;
        this.renameValue = initialValue;
        this.renameOnSubmit = onSubmit;
        this.renameOnCancel = onCancel;
        this.renderRows();

        const extensionStart = initialValue.lastIndexOf(".");
        const selectionEnd = extensionStart === -1 ? initialValue.length : extensionStart;
        this.renameField.setSelectionRange(0, selectionEnd);
    }

    closeRenameField(why: "submit" | "cancel", render: boolean = true) {
        if (this.renameFieldIndex !== -1) {
            if (why === "submit") this.renameOnSubmit();
            else this.renameOnCancel();
        }

        this.renameFieldIndex = -1;
        this.renameOnSubmit = doNothing;
        this.renameOnCancel = doNothing;
        if (render) this.renderRows();
    }

    // Context menu
    closeContextMenu() {
        this.contextMenuHandlers = [];
        this.contextMenu.style.removeProperty("transform");
        this.contextMenu.style.opacity = "0";
        window.setTimeout(() => {
            if (this.contextMenu.style.opacity === "0") {
                this.contextMenu.style.display = "none";
                this.contextMenu.style.opacity = "1";
            }
        }, 240);
    }

    findActiveContextMenuItem(clearActive: boolean = true): number {
        const listItems = this.contextMenu.querySelectorAll("ul li");
        let selectedIndex = -1;
        for (let i = 0; i < listItems.length; i++) {
            const item = listItems.item(i);
            if (item.getAttribute("data-selected") === "true") {
                selectedIndex = i;
            }

            if (clearActive) item.removeAttribute("data-selected");
        }

        return selectedIndex;
    }

    selectContextMenuItem(index: number) {
        const listItems = this.contextMenu.querySelectorAll("ul li");
        if (index < 0 || index >= listItems.length) return;
        listItems.item(index).setAttribute("data-selected", "true");
    }

    // Row event handlers
    private onRowPointerDown(index: number, event: MouseEvent) {
        let shouldDragAndDrop = true;

        let rowRect: DOMRect;
        let entryIdx: number = NaN;

        const startX = event.clientX;
        const startY = event.clientY;

        const isBeyondDeadZone = (ev: MouseEvent) => {
            const dx = ev.clientX - startX;
            const dy = ev.clientY - startY;
            const distanceTravelled = Math.sqrt((dx * dx) + (dy * dy));
            return distanceTravelled >= 15;
        };

        const currentPathToTarget = (): string | null => {
            const e = this.entryBelowCursor;
            if (e === null) {
                return null;
            } else if (typeof e === "string") {
                return e;
            } else {
                return this.dispatchMessage("pathToEntry", fn => fn(e));
            }
        };

        const isDraggingAllowed = (selectedEntries: T[]): boolean => {
            const e = this.entryBelowCursor;
            if (e === null) return false;
            const pathToEntryBelowCursor = currentPathToTarget()!;

            const draggingToSelf = selectedEntries.some(it =>
                it === this.entryBelowCursor ||
                this.dispatchMessage("pathToEntry", fn => fn(it)) === pathToEntryBelowCursor
            );

            const belowCursor: T | null | string = this.entryBelowCursor;
            let draggingAllowed = belowCursor !== null && !draggingToSelf;
            if (draggingAllowed && typeof belowCursor !== "string" && belowCursor !== null) {
                draggingAllowed = this.dispatchMessage("validDropTarget", fn => fn(belowCursor));
            }

            return draggingAllowed;
        }

        if (index < 0 || !this.features.supportsMove) {
            shouldDragAndDrop = false;
        } else {
            const row = this.rows[index];
            const idxString = row.container.getAttribute("data-idx");
            entryIdx = idxString ? parseInt(idxString) : NaN;
            if (isNaN(entryIdx)) return;

            const range = document.createRange();
            if (row.title.lastChild) range.selectNode(row.title.lastChild);
            const textRect = range.getBoundingClientRect();
            rowRect = row.container.getBoundingClientRect();
            range.detach();

            // NOTE(Dan): We are purposefully only checking if x <= end of text.
            // NOTE(Dan): We are adding 30px to increase the hit-box slightly
            shouldDragAndDrop = event.clientX <= textRect.x + textRect.width + 30;
        }

        if (shouldDragAndDrop) {
            // Drag-and-drop functionality
            let didMount = false;
            let selectedEntries: T[] = [];
            let selectedIndices: number[] = [];

            // NOTE(Dan): Next we render the fileDragIndicator and display it. This will move around with the cursor.
            const moveHandler = (e: MouseEvent) => {
                if (!this.root.isConnected) return;

                if (!didMount) {
                    if (isBeyondDeadZone(e)) {
                        didMount = true;

                        const indicator = this.entryDragIndicator;
                        indicator.innerHTML = "";
                        indicator.style.transform = `translate(${rowRect.left + (rowRect.width / 2)}px, ${rowRect.top}px) scale3d(${rowRect.width}, 100%, 100%)`;
                        indicator.style.display = "block";

                        const page = this.cachedData[this.currentPath] ?? [];
                        let startEntry: T | null = null;
                        let startEntryIsSelected: boolean = false;
                        for (let i = 0; i < this.isSelected.length && i < page.length; i++) {
                            if (this.isSelected[i] !== 0) {
                                selectedEntries.push(page[i]);
                                selectedIndices.push(i);
                            }

                            if (i === entryIdx) {
                                startEntry = page[i];
                                startEntryIsSelected = this.isSelected[i] !== 0;
                            }
                        }

                        if (!startEntryIsSelected && startEntry != null) {
                            selectedEntries = [startEntry];
                            selectedIndices = [entryIdx];
                            this.select(entryIdx, SelectionMode.SINGLE);
                        }

                        const content = this.entryDragIndicatorContent;
                        content.innerHTML = "";
                        this.dispatchMessage("renderDropIndicator", fn => fn(selectedEntries, null));
                        content.style.display = "block";

                        if (selectedEntries.length > 0) {
                            for (const selected of selectedIndices) {
                                for (let i = 0; i < this.rows.length; i++) {
                                    const row = this.rows[i];
                                    const container = row.container;
                                    if (container.getAttribute("data-idx") === selected.toString()) {
                                        container.style.opacity = "50%";
                                        break;
                                    }
                                }
                            }
                        }

                        indicator.ontransitionend = () => {
                            indicator.style.transition = "none";
                        };

                        window.setTimeout(() => {
                            indicator.classList.add("animate");
                            indicator.style.transform = `translate(${rowRect.left + 200}px, ${rowRect.top}px) scale3d(400, 100%, 100%)`;
                        }, 0);
                    }
                } else {
                    const draggingAllowed = isDraggingAllowed(selectedEntries);

                    // NOTE(Dan): We use a data-attribute on the body such that we can set the cursor property to
                    // count for all elements. It is not enough to simply use !important on the body since that will
                    // still be overridden by most links.
                    if (!draggingAllowed) {
                        document.body.setAttribute("data-cursor", "not-allowed");
                    } else {
                        document.body.setAttribute("data-cursor", "grabbing");
                    }

                    const s = this.entryDragIndicator.style;
                    s.transform = `translate(${e.clientX + 200 + 10}px, ${e.clientY + 10}px) scale3d(400, 100%, 100%)`;

                    const s2 = this.entryDragIndicatorContent.style;
                    s2.left = (e.clientX + 10) + "px";
                    s2.top = (e.clientY + 10) + "px";

                    this.entryBelowCursor = this.entryBelowCursorTemporary;
                    this.entryBelowCursorTemporary = null;

                    const content = this.entryDragIndicatorContent;
                    content.innerHTML = "";
                    this.dispatchMessage(
                        "renderDropIndicator",
                        fn => fn(selectedEntries, draggingAllowed ? currentPathToTarget() : null)
                    );
                }
            };

            const releaseHandler = (e: Event) => {
                document.removeEventListener("mousemove", moveHandler);
                document.removeEventListener("pointerup", releaseHandler);
                if (!this.root.isConnected) return;
                if (!didMount) return;
                this.entryDragIndicator.classList.remove("animate");
                this.entryDragIndicator.style.removeProperty("transition");
                for (let i = 0; i < this.rows.length; i++) {
                    this.rows[i].container.style.removeProperty("opacity");
                }

                this.entryDragIndicator.style.display = "none";
                this.entryDragIndicatorContent.style.display = "none";

                document.body.removeAttribute("data-cursor");

                if (selectedEntries.length > 0 && isDraggingAllowed(selectedEntries)) {
                    const belowCursor = this.entryBelowCursor;
                    const pathToEntryBelowCursor = typeof belowCursor === "string" ? belowCursor :
                        this.dispatchMessage("pathToEntry", fn => fn(belowCursor!));

                    this.dispatchMessage("move", fn => fn(selectedEntries, pathToEntryBelowCursor));
                }

                this.ignoreRowClicksUntil = timestampUnixMs() + 15;
            };

            document.addEventListener("mousemove", moveHandler);
            document.addEventListener("pointerup", releaseHandler);
        } else {
            // Drag-to-select functionality
            if (!this.features.dragToSelect) return;

            const scrollingContainer = this.scrolling.parentElement!;
            const scrollingRectangle = scrollingContainer.getBoundingClientRect();
            let initialScrollTop = scrollingContainer.scrollTop;

            let didMount = false;
            document.body.setAttribute("data-no-select", "true");

            const dragMoveHandler = (e) => {
                if (!didMount && isBeyondDeadZone(e)) {
                    if (!isNaN(entryIdx)) {
                        this.select(entryIdx, SelectionMode.SINGLE);
                    } else {
                        this.select(0, SelectionMode.CLEAR);
                    }

                    didMount = true;
                }
                if (!didMount) return;
                const s = this.dragIndicator.style;
                s.display = "block";
                s.left = Math.min(e.clientX, startX) + "px";
                s.top = Math.min(e.clientY, startY) + "px";

                s.width = Math.abs(e.clientX - startX) + "px";
                s.height = Math.abs(e.clientY - startY) + "px";

                // NOTE(Dan): Figure out where the mouse is relative to rows.
                // TODO(Dan): Should we extract this to a function?
                let scrollOffset = 0;
                let rowOffset = 0;
                const scrollStart = scrollingContainer.scrollTop;
                for (let i = 0; i < this.rows.length; i++) {
                    const row = this.rows[i];
                    const top = parseInt(row.container.style.top.replace("px", ""));
                    if (top >= scrollStart) {
                        scrollOffset = top - scrollStart;
                        rowOffset = i;
                        break;
                    }
                }

                const lowerY = Math.min(e.clientY, startY);
                const upperY = Math.max(e.clientY, startY);

                const lowerOffset = Math.max(
                    0,
                    Math.floor((lowerY - scrollingRectangle.top - scrollOffset) / ResourceBrowser.rowSize) + rowOffset
                );
                const upperOffset = Math.min(
                    this.rows.length,
                    Math.floor((upperY - scrollingRectangle.top - scrollOffset) / ResourceBrowser.rowSize) + rowOffset
                );
                const baseFileIdx = parseInt(this.rows[0].container.getAttribute("data-idx")!);

                for (let i = 0; i < this.rows.length; i++) {
                    if (i >= lowerOffset && i <= upperOffset) {
                        this.isSelected[i + baseFileIdx] = 1;
                    } else {
                        // NOTE(Dan): When scrolling, then we should never turn anything off if scrolling has begun
                        // TODO(Dan): Maybe we should just move the scrolling region along with the scrolling?
                        if (initialScrollTop === scrollStart) this.isSelected[i + baseFileIdx] = 0;
                    }
                }
                this.renderRows();
                this.renderOperations();
            };

            const dragReleaseHandler = ev => {
                document.body.removeAttribute("data-no-select");
                this.dragIndicator.style.display = "none";
                document.removeEventListener("mousemove", dragMoveHandler);
                document.removeEventListener("pointerup", dragReleaseHandler);
                if (!ev.target) return;

                if (ev.target === this.dragIndicator) {
                    // NOTE(Dan): If the mouse haven't moved much, then we should just treat it as a selection. We have to
                    // this here since the normal click handler won't be invoked since it goes to the drag indicator
                    // instead.
                    if (!isBeyondDeadZone(ev)) this.onRowClicked(index, ev);
                }
            };

            document.addEventListener("mousemove", dragMoveHandler);
            document.addEventListener("pointerup", dragReleaseHandler);
        }
    }

    private onRowClicked(index: number, event: MouseEvent) {
        if (timestampUnixMs() < this.ignoreRowClicksUntil) return;
        if (index < 0 || index >= this.rows.length) return;
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        let mode = SelectionMode.SINGLE;
        if (event.ctrlKey || event.metaKey) mode = SelectionMode.TOGGLE_SINGLE;
        if (event.shiftKey) mode = SelectionMode.ADDITIVE_LIST;
        this.select(entryIdx, mode);
    }

    private onRowDoubleClicked(index: number) {
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        const page = this.cachedData[this.currentPath] ?? [];
        const pathToEntry = this.dispatchMessage("pathToEntry", fn => fn(page[entryIdx]));
        if (this.dispatchMessage("beforeOpen", fn => fn(pathToEntry, "", page[entryIdx]))) return;
        this.open(pathToEntry, false, page[entryIdx]);
    }

    private onRowMouseMove(index: number) {
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        const page = this.cachedData[this.currentPath] ?? [];
        this.entryBelowCursor = page[entryIdx];
    }

    private onRowContextMenu(index: number, event: MouseEvent) {
        if (index >= 0) {
            const row = this.rows[index];
            const entryIdxS = row.container.getAttribute("data-idx");
            const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
            if (entryIdx == null || isNaN(entryIdx)) return;

            if (this.isSelected[entryIdx] === 0) {
                // If this file isn't part of the selected set, then make it selected as the only item
                this.select(entryIdx, SelectionMode.SINGLE);
            }
        } else {
            // If we are not right-clicking on a specific file, then clear the entire selection.
            this.select(0, SelectionMode.CLEAR);
            this.renderRows();
        }

        this.renderOperationsIn(true, {x: event.clientX, y: event.clientY});
    }

    private onStarClicked(index: number) {
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        const page = this.cachedData[this.currentPath] ?? [];
        this.dispatchMessage("starClicked", fn => fn(page[entryIdx]));
    }

    // Row container event handlers
    private onScroll() {
        if (this.ignoreScrollEvent) {
            this.ignoreScrollEvent = false;
            return;
        }

        this.renderRows();
        this.fetchNext();
    }

    private async fetchNext() {
        const initialPath = this.currentPath;
        if (this.isFetchingNext || this.cachedNext[initialPath] === null) return;

        const scrollingContainer = this.scrolling.parentElement!;
        const scrollingPos = scrollingContainer.scrollTop;
        const scrollingHeight = scrollingContainer.scrollHeight;
        if (scrollingPos < scrollingHeight * 0.8) return;

        this.isFetchingNext = true;
        this.dispatchMessage("wantToFetchNextPage", fn => fn(this.currentPath))
            .finally(() => {
                if (initialPath === this.currentPath) this.isFetchingNext = false;
                this.renderRows();
            });
    }

    // Generic keyboard shortcut event handler
    private onKeyPress(ev: KeyboardEvent) {
        const relativeSelectFiles = (delta: number, forceShift?: boolean) => {
            ev.preventDefault();
            const shift = forceShift ?? ev.shiftKey;
            if (!shift) {
                const rowIdx = Math.max(0, Math.min(this.isSelected.length - 1, this.lastSingleSelection + delta));
                this.ensureRowIsVisible(rowIdx, delta <= 0, true);
                this.select(rowIdx, SelectionMode.SINGLE);
            } else {
                let baseIndex = this.lastListSelectionEnd;
                if (baseIndex === -1) {
                    this.select(this.lastSingleSelection, SelectionMode.ADDITIVE_SINGLE, false);
                    baseIndex = this.lastSingleSelection;
                }
                const rowIdx = Math.max(0, Math.min(this.isSelected.length - 1, baseIndex + delta));
                this.ensureRowIsVisible(rowIdx, delta <= 0, true);
                this.select(rowIdx, SelectionMode.ADDITIVE_LIST);
            }
        };

        const relativeContextMenuSelect = (delta: number) => {
            const ul = this.contextMenu.querySelector("ul");
            if (!ul) return;
            const listItems = ul.querySelectorAll("li");

            let selectedIndex = -1;
            for (let i = 0; i < listItems.length; i++) {
                const item = listItems.item(i);
                if (item.getAttribute("data-selected") === "true") {
                    selectedIndex = i;
                }

                item.removeAttribute("data-selected");
            }

            selectedIndex = selectedIndex + delta;
            if (Math.abs(delta) > 1 && (selectedIndex < 0 || selectedIndex >= listItems.length)) {
                // Large jumps should not cause wrap around, instead move to either the top or the bottom.
                if (selectedIndex < 0) selectedIndex = 0;
                else selectedIndex = listItems.length - 1;
            }

            // Wrap around the selection, moving up from the top should go to the bottom and vice-versa.
            if (selectedIndex < 0) selectedIndex = listItems.length - 1;
            else if (selectedIndex >= listItems.length) selectedIndex = 0;

            listItems.item(selectedIndex).setAttribute("data-selected", "true");
        };

        const relativeSelect = (delta: number, forceShift?: boolean) => {
            if (!this.contextMenuHandlers.length) {
                return relativeSelectFiles(delta, forceShift);
            } else {
                return relativeContextMenuSelect(delta);
            }
        };

        if (!this.contextMenuHandlers.length) {
            this.dispatchMessage("beforeShortcut", fn => fn(ev));
            if (ev.defaultPrevented) return;
        }

        if (ev.ctrlKey || ev.metaKey) {
            let didHandle = true;
            switch (ev.code) {
                case "KeyA": {
                    if (this.contextMenuHandlers.length) return;

                    relativeSelect(-1000000000, false);
                    relativeSelect(1000000000, true);
                    break;
                }

                case "KeyX":
                case "KeyC": {
                    if (!this.features.supportsMove || !this.features.supportsCopy) {
                        didHandle = false;
                    } else {
                        if (this.contextMenuHandlers.length) return;

                        const newClipboard = this.findSelectedEntries();
                        this.clipboard = newClipboard;
                        this.clipboardIsCut = ev.code === "KeyX";
                        if (newClipboard.length) {
                            const key = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌘" : "Ctrl + ";
                            snackbarStore.addInformation(
                                `${newClipboard.length} copied to clipboard. Use ${key}V to insert.`,
                                false
                            );
                        }
                    }
                    break;
                }

                case "KeyV": {
                    if (!this.features.supportsMove || !this.features.supportsCopy) {
                        didHandle = false;
                    } else {
                        if (this.contextMenuHandlers.length) return;
                        if (this.clipboard.length) {
                            this.dispatchMessage(
                                this.clipboardIsCut ? "move" : "copy",
                                fn => fn(this.clipboard, this.currentPath)
                            );

                            if (this.clipboardIsCut) this.clipboard = [];
                        }
                    }
                    break;
                }

                case "KeyG": {
                    if (!this.features.locationBar) {
                        didHandle = false;
                    } else {
                        if (this.contextMenuHandlers.length) return;
                        this.toggleLocationBar();
                    }
                    break;
                }

                case "KeyZ": {
                    if (this.contextMenuHandlers.length) return;
                    const fn = this.undoStack.shift();
                    if (fn !== undefined) fn();
                    break;
                }

                case "KeyY": {
                    if (this.contextMenuHandlers.length) return;
                    const fn = this.redoStack.shift();
                    if (fn !== undefined) fn();
                    break;
                }

                default: {
                    didHandle = false;
                    break;
                }
            }

            if (didHandle) {
                ev.preventDefault();
                ev.stopPropagation();
            } else {
                this.dispatchMessage("unhandledShortcut", fn => fn(ev));
            }
        } else if (ev.altKey) {
            const altCodeIndex = this.altKeys.indexOf(ev.code);
            if (altCodeIndex >= 0 && altCodeIndex < this.altShortcuts.length) {
                ev.preventDefault();
                ev.stopPropagation();

                this.altShortcuts[altCodeIndex]();
            } else {
                this.dispatchMessage("unhandledShortcut", fn => fn(ev));
            }
        } else {
            // NOTE(Dan): Don't add printable keys to the switch statement here, as it will break the search
            // functionality. Instead, add it to the default case.
            switch (ev.code) {
                case "Escape": {
                    if (this.contextMenuHandlers.length) {
                        this.closeContextMenu();
                    } else {
                        const selected = this.isSelected;
                        for (let i = 0; i < selected.length; i++) {
                            selected[i] = 0;
                        }
                        this.renderRows();
                        this.renderOperations();
                        this.searchQuery = "";
                    }
                    break;
                }

                case "ArrowUp": {
                    ev.preventDefault();
                    relativeSelect(-1);
                    break;
                }

                case "ArrowDown": {
                    ev.preventDefault();
                    relativeSelect(1);
                    break;
                }

                case "Home": {
                    ev.preventDefault();
                    relativeSelect(-1000000000);
                    break;
                }

                case "End": {
                    ev.preventDefault();
                    relativeSelect(1000000000);
                    break;
                }

                case "PageUp": {
                    ev.preventDefault();
                    relativeSelect(-50);
                    break;
                }

                case "PageDown": {
                    ev.preventDefault();
                    relativeSelect(50);
                    break;
                }

                case "Backspace": {
                    if (this.contextMenuHandlers.length) return;
                    const crumbs = this.dispatchMessage("generateBreadcrumbs", fn => fn(this.currentPath));
                    const parent = crumbs[crumbs.length - 2];
                    this.open(parent.absolutePath);
                    break;
                }

                case "Enter": {
                    if (this.contextMenuHandlers.length) {
                        this.onContextMenuItemSelection();
                    } else {
                        const selected = this.isSelected;
                        for (let i = 0; i < selected.length; i++) {
                            if (selected[i] !== 0) {
                                const entry = this.cachedData[this.currentPath][i];
                                const path = this.dispatchMessage("pathToEntry", fn => fn(entry));
                                this.open(path);
                                break;
                            }
                        }
                    }
                    break;
                }

                default: {
                    if (this.contextMenuHandlers.length) {
                        if (ev.code.startsWith("Digit")) {
                            this.processingShortcut = true;
                            window.setTimeout(() => {
                                this.processingShortcut = false;
                            }, 0);

                            const selectedItem = parseInt(ev.code.substring("Digit".length));
                            if (!isNaN(selectedItem) && selectedItem !== 0) {
                                this.onContextMenuItemSelection(selectedItem - 1);
                            }
                        }
                    } else {
                        // NOTE(Dan): Initiate search if the input is printable
                        const printableChar = ev.key;
                        if (printableChar && printableChar.length === 1) {
                            this.searchQuery += printableChar.toLowerCase();
                            window.clearTimeout(this.searchQueryTimeout);
                            this.searchQueryTimeout = window.setTimeout(() => {
                                this.searchQuery = "";
                            }, 1000);

                            const files = this.cachedData[this.currentPath] ?? [];
                            for (let i = 0; i < files.length; i++) {
                                const name = this.dispatchMessage("nameOfEntry", fn => fn(files[i])).toLowerCase();
                                if (name.indexOf(this.searchQuery) === 0) {
                                    this.ensureRowIsVisible(i, true, true);
                                    this.select(i, SelectionMode.SINGLE);
                                    break;
                                }
                            }
                        } else {
                            this.dispatchMessage("unhandledShortcut", fn => fn(ev));
                        }
                    }
                    break;
                }
            }
        }
    }

    // Location bar event handlers
    private onLocationBarKeyDown(ev: "input" | KeyboardEvent) {
        if (ev !== "input") ev.stopPropagation();

        const attrRealPath = "data-real-path";

        const setValue = (path: string): string | null => {
            const {rendered, normalized} = this.dispatchMessage("renderLocationBar", fn => fn(path));
            this.locationBar.setAttribute(attrRealPath, normalized);
            this.locationBar.value = rendered;
            return path;
        };

        const readValue = (): string | null => {
            if (!this.locationBar.hasAttribute(attrRealPath)) return setValue(this.locationBar.value);
            return this.locationBar.getAttribute(attrRealPath);
        };

        const doTabComplete = (allowFetch: boolean = true) => {
            const path = readValue();
            if (path === null) return;

            if (this.locationBarTabIndex === -1) this.locationBarTabIndex = path.length;

            const pathPrefix = path.substring(0, this.locationBarTabIndex);

            const entries = this.dispatchMessage(
                "generateTabCompletionEntries",
                fn => fn(pathPrefix, allowFetch, this.locationBarTabCount)
            );

            if ("then" in entries) {
                return entries.then(it => {
                    if (readValue() !== path) return;
                    return doTabComplete(false);
                });
            }

            if (entries.length === 0) return;

            const itemToUse = this.locationBarTabCount % entries.length;
            let newValue = entries[itemToUse];
            if (!newValue.startsWith("/")) newValue = "/" + newValue;
            newValue += "/";
            setValue(newValue);

            this.locationBarTabCount = (itemToUse + 1) % entries.length;
            if (entries.length === 1) {
                this.locationBarTabIndex = readValue()?.length ?? 0;
                this.locationBarTabCount = 0;
            }
        };

        if (ev === "input") {
            this.locationBarTabIndex = -1;
            this.locationBarTabCount = 0;

            setValue(this.locationBar.value);
        } else {
            switch (ev.code) {
                case "Tab": {
                    ev.preventDefault();
                    doTabComplete();
                    break;
                }

                case "Enter": {
                    const newPath = readValue();
                    if (newPath) {
                        this.setLocationBarVisibility(false);
                        this.open(newPath);
                    }
                    break;
                }

                case "Escape": {
                    this.setLocationBarVisibility(false);
                    setValue(this.currentPath);
                    break;
                }
            }
        }
    }

    // Context menu event handlers
    private onContextMenuItemSelection(fixedIdx?: number) {
        if (!this.contextMenuHandlers.length) return;
        const idx = fixedIdx ?? this.findActiveContextMenuItem(false);
        if (idx < 0 || idx >= this.contextMenuHandlers.length) return;
        this.contextMenuHandlers[idx]();
        this.closeContextMenu();
    }

    triggerOperation(predicate: (op: Operation<T, unknown>) => boolean): boolean {
        const callbacks = this.dispatchMessage("fetchOperationsCallback", fn => fn());
        if (callbacks === null) return false;
        const ops = this.dispatchMessage("fetchOperations", fn => fn());
        for (const op of ops) {
            let toCheck: Operation<T, unknown>[] = [];
            if ("operations" in op) {
                toCheck = op.operations;
            } else {
                toCheck = [op];
            }

            for (const child of toCheck) {
                if (predicate(child)) {
                    child.onClick(this.findSelectedEntries(), callbacks, this.cachedData[this.currentPath] ?? []);
                    return true;
                }
            }
        }

        return false;
    }

    // Rename field event handlers
    private onRenameFieldKeydown(ev: KeyboardEvent) {
        ev.stopPropagation();
        if (this.processingShortcut) {
            ev.preventDefault();
            return;
        }

        switch (ev.code) {
            case "Enter": {
                this.closeRenameField("submit");
                break;
            }

            case "Escape": {
                this.closeRenameField("cancel");
                break;
            }

            case "Home":
            case "End": {
                ev.preventDefault();
                break;
            }
        }
    }

    private onRenameFieldBeforeInput(ev: InputEvent) {
        if (this.processingShortcut) {
            ev.preventDefault();
            ev.stopPropagation();
        }
    }

    private onRenameFieldInput(ev: Event) {
        ev.preventDefault();
        ev.stopPropagation();
        if (this.processingShortcut) return;
        this.renameValue = this.renameField.value;
    }

    // Minor utilities
    createSpinner(size: number): Node {
        const fragment = this.spinner.clone();
        const parent = fragment.querySelector('[data-tag="loading-spinner"]')! as HTMLElement;
        parent.style.width = `${size}px`;
        parent.style.height = `${size}px`;

        const spinner = fragment.querySelector("svg")!;
        spinner.setAttribute("width", size.toString());
        spinner.setAttribute("height", size.toString());
        return fragment;
    }

    // NOTE(Dan): selectAndShow() requires that the matching row has been rendered at least once.
    // If it has not been rendered, then we won't be able to allocate the physical row, which we need in order to
    // scroll to it.
    selectAndShow(predicate: (arg: T) => boolean) {
        const idx = this.findVirtualRowIndex(predicate);
        if (idx !== null) {
            this.ensureRowIsVisible(idx, true, true);
            this.select(idx, SelectionMode.SINGLE);
        }
    }

    // Message passing required to control the component
    on<K extends keyof ResourceBrowserListenerMap<T>>(
        type: K,
        listener: ResourceBrowserListenerMap<T>[K],
    ) {
        let arr = this.listeners[type] ?? [];
        this.listeners[type] = arr;
        arr.push(listener);
    }

    private defaultHandlers: Partial<ResourceBrowserListenerMap<T>> = {
        open: doNothing,
        beforeOpen: () => false,
        rowSelectionUpdated: doNothing,
        mount: doNothing,
        unmount: doNothing,
        locationBarVisibilityUpdated: doNothing,
        sort: doNothing,
        startRenderPage: doNothing,
        endRenderPage: doNothing,
        beforeShortcut: doNothing,
        fetchFilters: () => [],

        renderLocationBar: prompt => {
            return {rendered: prompt, normalized: prompt};
        },

        nameOfEntry: entry => {
            const path = this.dispatchMessage("pathToEntry", fn => fn(entry));
            return fileName(path);
        },
    }

    dispatchMessage<K extends keyof ResourceBrowserListenerMap<T>>(
        type: K,
        invoker: (fn: ResourceBrowserListenerMap<T>[K]) => ReturnType<ResourceBrowserListenerMap<T>[K]>
    ): ReturnType<ResourceBrowserListenerMap<T>[K]> {
        let arr = this.listeners[type];
        if (arr === undefined) {
            const defaultHandler = this.defaultHandlers[type];
            if (defaultHandler === undefined) throw "Missing listener for " + type;
            arr = [defaultHandler];
        }

        let result: any;
        for (const l of arr) {
            result = invoker(l);
        }
        return result;
    }

    static rowTitleSizePercentage = 56;
    static rowSize = 55;
    static extraRowsToPreRender = 6;
    static maxRows = (Math.max(1080, window.screen.height) / ResourceBrowser.rowSize) + ResourceBrowser.extraRowsToPreRender;

    static styleInjected = false;
    static injectStyle() {
        if (ResourceBrowser.styleInjected) return;
        ResourceBrowser.styleInjected = true;
        //language=css
        unstyledInjectStyle("ignored", () => `
            body[data-cursor=not-allowed] * {
                cursor: not-allowed !important;
            }

            body[data-cursor=grabbing] * {
                cursor: grabbing !important;
            }
            
            body[data-no-select=true] * {
                user-select: none;
            }

            .file-browser .drag-indicator {
                position: fixed;
                z-index: 10000;
                background-color: rgba(0, 0, 255, 30%);
                border: 2px solid blue;
                display: none;
                top: 0;
                left: 0;
            }

            .file-browser .file-drag-indicator-content,
            .file-browser .file-drag-indicator {
                position: fixed;
                z-index: 10000;
                display: none;
                top: 0;
                left: 0;
                height: ${this.rowSize}px;
                user-select: none;
            }
            
            .file-browser .file-drag-indicator-content {
                z-index: 10001;
                width: 400px;
                margin: 16px;
                white-space: pre;
            }

            .file-browser .filters, .file-browser .session-filters {
                display: flex;
                margin-top: 12px;
            }

            .file-browser .file-drag-indicator-content img {
                margin-right: 8px;
            }

            .file-browser .file-drag-indicator {
                transition: transform 0.06s;
                background: var(--tableRowHighlight);
                width: 1px;
                overflow: hidden;
            }
         
            .file-browser .file-drag-indicator.animate {
            }

            .file-browser {
                width: 100%;
                height: calc(100vh - 32px);
                display: flex;
                flex-direction: column;
                font-size: 16px;
            }

            .file-browser header .header-first-row {
                margin-top: 5px;
                display: flex;
            }

            .file-browser header .header-first-row img {
                cursor: pointer;
                flex-shrink: 0;
                margin-left: 16px;
                margin-top: 5px;
            }

            .file-browser header .header-first-row ul,
            .file-browser header .header-first-row .location-bar {
                flex-grow: 1;
            }

            .file-browser header ul {
                padding: 0;
                margin: 0 0 8px;
                display: flex;
                flex-direction: row;
                gap: 8px;
                height: 35px;
                white-space: pre;
            }

            .file-browser > div {
                flex-grow: 1;
            }

            .file-browser header {
                width: 100%;
                height: 100px;
                flex-shrink: 0;
                overflow: hidden;
            }
            
            .file-browser header[data-has-filters] {
                height: 136px;
            }

            .file-browser header .location-bar,
            .file-browser header.show-location-bar ul {
                display: none;
            }

            .file-browser header.show-location-bar .location-bar,
            .file-browser header ul {
                display: flex;
            }

            .file-browser .location-bar {
                width: 100%;
                font-size: 120%;
                height: 35px;
                margin-bottom: 8px;
            }
            
            .file-browser header ul[data-no-slashes="true"] li::before {
                display: inline-block;
                content: unset;
                margin: 0;
            }

            .file-browser header ul li::before {
                display: inline-block;
                content: '/';
                margin-right: 8px;
            }

            .file-browser header ul li {
                list-style: none;
                margin: 0;
                padding: 0;
                cursor: pointer;
                font-size: 120%;
            }

            .file-browser .row {
                display: flex;
                flex-direction: row;
                height: ${ResourceBrowser.rowSize}px;
                width: 100%;
                align-items: center;
                border-bottom: 1px solid #96B3F8;
                gap: 8px;
                user-select: none;
                padding: 0 8px;
                transition: filter 0.3s;
            }
            
            body[data-cursor=grabbing] .file-browser .row:hover {
                filter: hue-rotate(10deg) saturate(500%);
            }

            .file-browser .row.hidden {
                display: none;
            }

            .file-browser .row input[type=checkbox] {
                height: 20px;
                width: 20px;
            }

            .file-browser .row[data-selected="true"] {
                background: var(--tableRowHighlight);
            }

            .file-browser .row .title {
                display: flex;
                align-items: center;
                width: ${ResourceBrowser.rowTitleSizePercentage}%;
                white-space: pre;
            }

            .file-browser .row .stat1,
            .file-browser .row .stat2,
            .file-browser .row .stat3 {
                display: flex;
                justify-content: end;
                text-align: end;
                width: 13%;
            }

            .file-browser .sensitivity-badge {
                height: 2em;
                width: 2em;
                display: flex;
                margin-right: 5px;
                align-items: center;
                justify-content: center;
                border: 0.2em solid var(--badgeColor, var(--midGray));
                border-radius: 100%;
            }

            .file-browser .sensitivity-badge.PRIVATE {
                --badgeColor: var(--midGray);
            }

            .file-browser .sensitivity-badge.CONFIDENTIAL {
                --badgeColor: var(--purple);
            }

            .file-browser .sensitivity-badge.SENSITIVE {
                --badgeColor: #ff0004;
            }

            .file-browser .operation {
                cursor: pointer;
                display: flex;
                align-items: center;
                gap: 8px;
            }

            .file-browser .operation.in-header {
                padding: 11px;
                background: var(--headerOperationColor);
                border-radius: 8px;
            }

            .file-browser .operations {
                display: flex;
                flex-direction: row;
                gap: 16px;
            }

            .file-browser .context-menu {
                position: fixed;
                z-index: 10000;
                top: 0;
                left: 0;
                border-radius: 8px;
                border: 1px solid #E2DDDD;
                cursor: pointer;
                background: var(--white);
                box-shadow: 0 3px 6px rgba(0, 0, 0, 30%);
                width: 400px;
                display: none;
                transition: opacity 120ms, transform 60ms;
            }

            .file-browser .context-menu ul {
                padding: 0;
                margin: 0;
                display: flex;
                flex-direction: column;
            }

            .file-browser .context-menu li {
                margin: 0;
                padding: 8px 8px;
                list-style: none;
                display: flex;
                align-items: center;
                gap: 8px;
            }

            .file-browser .context-menu li kbd {
                flex-grow: 1;
                text-align: end;
            }

            .file-browser .context-menu li[data-selected=true] {
                background: var(--tableRowHighlight);
            }

            .file-browser .rename-field {
                display: none;
                position: absolute;
                background-color: var(--lightGray);
                border-radius: 5px;
                border: 1px solid var(--black);
                color: var(--text);
                z-index: 10000;
                top: 0;
                left: 60px;
            }

            .file-browser .page-empty {
                display: none;
                position: fixed;
                top: 0;
                left: 0;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                gap: 16px;
                text-align: center;
            }
            
            .file-browser .page-empty .graphic {
                background: var(--blue);
                height: 100px;
                width: 100px;
                border-radius: 100px;
                display: flex;
                align-items: center;
                justify-content: center;
            }
            
            .file-browser .page-empty .provider-reason {
                font-style: italic;
            }
        `);
    }

    private addCheckboxToFilter<T>(filter: FilterCheckbox) {
        const wrapper = document.createElement("label");
        wrapper.style.cursor = "pointer";
        wrapper.style.marginRight = "8px";

        const icon = this.createFilterImg(filter.icon);
        icon.style.marginTop = "0";
        icon.style.marginRight = "8px";
        wrapper.appendChild(icon);

        const span = document.createElement("span");
        span.innerText = filter.text;
        wrapper.appendChild(span);

        const check = document.createElement("input");
        check.style.marginLeft = "5px";
        check.style.cursor = "pointer";
        check.type = "checkbox";
        check.checked = false;

        const valueFromStorage = getFilterStorageValue(this.resourceName, filter.key);
        if (valueFromStorage === "true") {
            this.browseFilters[filter.key] = "true";
            check.checked = true;
        }

        wrapper.appendChild(check);
        this.filters.appendChild(wrapper);
        wrapper.onclick = e => {
            e.stopImmediatePropagation();
            e.preventDefault();
            check.checked = !check.checked;
            if (this.browseFilters[filter.key]) {
                setFilterStorageValue(this.resourceName, filter.key, "false");
                delete this.browseFilters[filter.key];
            } else {
                setFilterStorageValue(this.resourceName, filter.key, "true");
                this.browseFilters[filter.key] = "true";
            }
            this.open(this.currentPath, true);
        }
    }

    private addOptionsToFilter<T>(filter: FilterWithOptions | MultiOptionFilter) {
        const wrapper = document.createElement("div");
        wrapper.style.display = "flex";
        wrapper.style.cursor = "pointer";
        wrapper.style.marginRight = "8px";
        wrapper.style.userSelect = "none";

        const valueFromStorage = getFilterStorageValue(this.resourceName, filter.type === "options" ? filter.key : filter.keys[0]);
        let iconName: IconName = filter.icon;
        if (valueFromStorage !== null) {
            if (filter.type === "options") {
                iconName = filter.options.find(it => it.value === valueFromStorage)?.icon ?? filter.icon;
            } else if (filter.type === "multi-option") {
                const secondValue = getFilterStorageValue(this.resourceName, filter.keys[1]);
                iconName = filter.options.find(it => it.values[0] === valueFromStorage && (!it.values[1] || it.values[1] === secondValue))?.icon ?? filter.icon;
            }
        }
        const icon = this.createFilterImg(iconName);
        icon.style.marginRight = "8px";
        wrapper.appendChild(icon);

        const text = document.createElement("span");
        text.style.marginRight = "5px";
        text.innerText = filter.text;

        if (valueFromStorage != null) {
            if (filter.type === "options") {
                const option = filter.options.find(it => it.value === valueFromStorage);
                if (option) {
                    text.innerText = option.text;
                    this.browseFilters[filter.key] = option.value;
                }
            } else if (filter.type === "multi-option") {
                const secondValue = getFilterStorageValue(this.resourceName, filter.keys[1]);
                const option = filter.options.find(it => it.values[0] === valueFromStorage && (!it.values[1] || it.values[1] === secondValue));
                if (option) {
                    text.innerText = option.text;
                    this.browseFilters[filter.keys[0]] = option.values[0];
                    if (secondValue) this.browseFilters[filter.keys[1]] = option.values[1];
                    else delete this.browseFilters[filter.keys[1]];
                }
            }
        }

        wrapper.appendChild(text);
        const chevronIcon = this.createFilterImg("chevronDownLight");
        wrapper.appendChild(chevronIcon);
        this.filters.appendChild(wrapper);

        wrapper.onclick = e => {
            const wrapperRect = wrapper.getBoundingClientRect();
            e.stopImmediatePropagation();
            this.renderFiltersInContextMenu(
                filter,
                wrapperRect.x,
                wrapperRect.y + wrapperRect.height,
            );
        };
    }


    private addInputToFilter(filter: FilterInput) {
        const wrapper = document.createElement("div");
        wrapper.style.display = "flex";
        wrapper.style.cursor = "pointer";
        wrapper.style.marginRight = "8px";
        wrapper.style.userSelect = "none";

        const icon = this.createFilterImg(filter.icon)
        icon.style.marginRight = "8px";
        wrapper.appendChild(icon);

        const text = document.createElement("span");
        text.style.marginRight = "5px";
        text.innerText = filter.text;
        wrapper.appendChild(text);

        const input = document.createElement("input");
        input.type = "text";
        input.className = InputClass;
        input.placeholder = "Search by...";
        input.style.display = "none";
        input.style.marginTop = "-4px";
        input.style.height = "32px";
        input.onclick = e => e.stopImmediatePropagation();
        input.onkeydown = e => {
            e.stopPropagation();
        };
        input.onkeyup = e => {
            if (input.value) {
                this.browseFilters[filter.key] = input.value;
            } else {
                delete this.browseFilters[filter.key];
            }
            this.open(this.currentPath, true);
        }
        wrapper.append(input);

        this.sessionFilters.append(wrapper);
        wrapper.onclick = e => {
            e.stopImmediatePropagation();
            input.value = "";
            if (input.style.display === "none") {
                input.style.display = "unset";
            } else input.style.display = "none";
        }
    }

    private createFilterImg(icon: IconName): HTMLImageElement {
        const c = document.createElement("img");
        c.width = 12;
        c.height = 12;
        c.style.marginTop = "7px";
        this.icons.renderIcon({color: "text", color2: "text", height: 32, width: 32, name: icon}).then(it => c.src = it);
        return c;
    }
}

export function div(html: string): HTMLDivElement {
    const elem = document.createElement("div");
    elem.innerHTML = html;
    return elem;
}

export function image(src: string, opts?: {alt?: string; height?: number; width?: number;}): HTMLImageElement {
    const result = new Image();
    result.src = src;
    result.alt = opts?.alt ?? "Icon";
    if (opts?.height != null) result.height = opts.height;
    if (opts?.width != null) result.width = opts.width;
    return result;
}

function getFilterStorageValue(namespace: string, key: string): string | null {
    return localStorage.getItem(`${namespace}:${key}`);
}

function setFilterStorageValue(namespace: string, key: string, value: string) {
    localStorage.setItem(`${namespace}:${key}`, value);
}

export function clearFilterStorageValue(namespace: string, key: string) {
    localStorage.removeItem(`${namespace}:${key}`);
}

// https://stackoverflow.com/a/13139830
export const placeholderImage = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
